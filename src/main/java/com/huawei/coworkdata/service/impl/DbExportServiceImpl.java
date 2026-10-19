package com.huawei.coworkdata.service.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.huawei.coworkdata.service.DbExportService;
import com.huawei.coworkdata.util.JsonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 按 schema.sql 白名单表流式导出 ZIP（直写响应，不落临时文件）；
 * 表内按主键游标分批查询，避免一次 SELECT * 拖垮大表。
 */
@Service
public class DbExportServiceImpl implements DbExportService {

    /** 表名 + 游标列（均来自常量白名单）。 */
    private static final class TableSpec {
        final String table;
        final String keyColumn;
        /** true = 数值主键（如 SERIAL）；false = 字符串主键。 */
        final boolean numericKey;

        TableSpec(String table, String keyColumn, boolean numericKey) {
            this.table = table;
            this.keyColumn = keyColumn;
            this.numericKey = numericKey;
        }
    }

    /** 与 schema.sql 业务表对齐；顺序固定便于核对。 */
    static final List<TableSpec> EXPORT_TABLES = Collections.unmodifiableList(Arrays.asList(
            new TableSpec("sessions", "id", false),
            new TableSpec("tasks", "id", false),
            new TableSpec("events", "id", false),
            new TableSpec("session_sse_events", "id", true),
            new TableSpec("snapshots", "id", false),
            new TableSpec("memory_events", "id", false),
            new TableSpec("memory_subscriptions", "id", false),
            new TableSpec("agent_templates", "id", false),
            new TableSpec("user_profile", "user_id", false),
            new TableSpec("cowork", "cowork_id", false),
            new TableSpec("cowork_permission", "cowork_id", false)
    ));

    private final JdbcTemplate jdbcTemplate;
    private final int batchSize;

    public DbExportServiceImpl(
            JdbcTemplate jdbcTemplate,
            @Value("${coworkdata.export.batch-size:2000}") int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = batchSize > 0 ? batchSize : 2000;
    }

    @Override
    public void writeAllTables(OutputStream out) throws IOException {
        writeArchive(out, null, null);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void writeByUploadDate(OutputStream out, LocalDate start, LocalDate end) throws IOException {
        if (start == null || end == null || start.isAfter(end)) {
            throw new IllegalArgumentException("Invalid upload date range");
        }
        writeArchive(out, start, end);
    }

    private void writeArchive(OutputStream out, LocalDate start, LocalDate end) throws IOException {
        try (ZipOutputStream zip = new ExportZipStream(out)) {
            zip.setLevel(Deflater.BEST_SPEED);
            Map<String, Long> counts = new LinkedHashMap<>();
            writeManifest(zip, start, end);
            for (TableSpec spec : EXPORT_TABLES) {
                if (start == null || !"agent_templates".equals(spec.table)) {
                    counts.put(spec.table, writeTableEntry(zip, spec, start, end));
                }
            }
            zip.putNextEntry(new ZipEntry("export-summary.json"));
            try (JsonGenerator gen = openGenerator(zip)) {
                gen.writeObject(counts);
            }
            zip.closeEntry();
            zip.finish();
            zip.flush();
        } catch (IOException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IOException("Export failed: " + ex.getMessage(), ex);
        }
    }

    private void writeManifest(ZipOutputStream zip, LocalDate start, LocalDate end) throws IOException {
        zip.putNextEntry(new ZipEntry("manifest.json"));
        JsonGenerator gen = openGenerator(zip);
        try {
            gen.writeStartObject();
            gen.writeStringField("exported_at", Instant.now().toString());
            gen.writeNumberField("batch_size", batchSize);
            gen.writeStringField("scope", start == null ? "all_business_tables" : "full_sessions_with_upload_activity");
            if (start != null) {
                gen.writeStringField("start_date", start.toString());
                gen.writeStringField("end_date", end.toString());
                gen.writeStringField("timezone", "Asia/Shanghai");
                gen.writeStringField("consistency", "repeatable_read_snapshot");
                gen.writeStringField("history", "Recorded upload activity; optional legacy baseline uses assigned date 2026-10-18, not actual upload time");
            }
            gen.writeArrayFieldStart("tables");
            for (TableSpec spec : EXPORT_TABLES) {
                if (start == null || !"agent_templates".equals(spec.table)) {
                    gen.writeString(spec.table);
                }
            }
            gen.writeEndArray();
            gen.writeEndObject();
            gen.flush();
        } finally {
            gen.close();
        }
        zip.closeEntry();
    }

    private long writeTableEntry(ZipOutputStream zip, TableSpec spec, LocalDate start, LocalDate end) throws IOException {
        long total = 0;
        zip.putNextEntry(new ZipEntry(spec.table + ".json"));
        JsonGenerator gen = openGenerator(zip);
        try {
            gen.writeStartArray();
            // lastKey 仅存在于本次导出请求的局部变量（游标），不落库、不写前端
            Object lastKey = null;
            while (true) {
                AtomicReference<Object> batchLastKey = new AtomicReference<Object>();
                int written = writeBatch(gen, spec, lastKey, batchLastKey, start, end);
                if (written == 0) {
                    break;
                }
                total += written;
                lastKey = batchLastKey.get();
                if (written < batchSize) {
                    break;
                }
            }
            gen.writeEndArray();
            gen.flush();
        } finally {
            gen.close();
        }
        zip.closeEntry();
        return total;
    }

    /**
     * 按主键游标拉一批并立刻写入 JSON；返回本批行数。
     */
    private int writeBatch(
            JsonGenerator gen,
            TableSpec spec,
            Object lastKey,
            AtomicReference<Object> batchLastKey, LocalDate start, LocalDate end) {
        final AtomicInteger count = new AtomicInteger();
        // 表名/列名仅来自白名单常量
        final String table = spec.table;
        final String keyColumn = spec.keyColumn;

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT t.* FROM " + table + " t WHERE 1=1");
        if (start != null) {
            sql.append(" AND ").append(datePredicate(table));
            args.add(java.sql.Date.valueOf(start));
            args.add(java.sql.Date.valueOf(end));
        }
        if (lastKey != null) {
            sql.append(" AND t.").append(keyColumn).append(" > ?");
            args.add(lastKey);
        }
        sql.append(" ORDER BY t.").append(keyColumn).append(" ASC LIMIT ?");
        args.add(batchSize);
        jdbcTemplate.query(sql.toString(), args.toArray(), rs -> {
            writeRow(gen, spec, rs, batchLastKey, count);
        });
        return count.get();
    }

    private static String datePredicate(String table) {
        String selected = "EXISTS (SELECT 1 FROM session_upload_daily d WHERE "
                + "d.session_id = s.id AND d.upload_date >= ? AND d.upload_date <= ?)";
        String relation;
        if ("sessions".equals(table)) {
            relation = "s.id = t.id";
        } else if ("user_profile".equals(table)) {
            relation = "s.user_id = t.user_id";
        } else if ("cowork".equals(table) || "cowork_permission".equals(table)) {
            relation = "s.cowork_id = t.cowork_id";
        } else {
            relation = "s.id = t.session_id";
        }
        return "EXISTS (SELECT 1 FROM sessions s WHERE " + relation + " AND " + selected + ")";
    }

    private static void writeRow(
            JsonGenerator gen,
            TableSpec spec,
            ResultSet rs,
            AtomicReference<Object> batchLastKey,
            AtomicInteger count) throws SQLException {
        try {
            Map<String, Object> row = rowToMap(rs);
            gen.writeObject(row);
            batchLastKey.set(extractKey(rs, spec));
            count.incrementAndGet();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write row for table " + spec.table, e);
        }
    }

    /** 从 ResultSet 取游标列原始值（保持数值/字符串类型，便于下一轮绑定参数）。 */
    private static Object extractKey(ResultSet rs, TableSpec spec) throws SQLException {
        Object raw = rs.getObject(spec.keyColumn);
        if (raw == null) {
            throw new IllegalStateException("Null cursor key in table " + spec.table
                    + " column " + spec.keyColumn);
        }
        if (spec.numericKey) {
            if (raw instanceof Number) {
                return ((Number) raw).longValue();
            }
            return Long.valueOf(raw.toString());
        }
        return raw.toString();
    }

    /** Release native compression resources, but never finalize a failed archive or close HTTP output. */
    private static final class ExportZipStream extends ZipOutputStream {
        ExportZipStream(OutputStream out) { super(out); }
        @Override
        public void close() { def.end(); }
    }

    private static JsonGenerator openGenerator(OutputStream target) throws IOException {
        JsonGenerator gen = JsonUtils.mapper().getFactory().createGenerator(target);
        gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        return gen;
    }

    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            String label = meta.getColumnLabel(i);
            row.put(label, normalizeValue(rs.getObject(i)));
        }
        return row;
    }

    private static Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toInstant().atOffset(ZoneOffset.UTC).toString();
        }
        if (value instanceof java.sql.Date) {
            return value.toString();
        }
        if (value instanceof java.sql.Time) {
            return value.toString();
        }
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).toString();
        }
        if (value instanceof byte[]) {
            return java.util.Base64.getEncoder().encodeToString((byte[]) value);
        }
        return value;
    }
}
