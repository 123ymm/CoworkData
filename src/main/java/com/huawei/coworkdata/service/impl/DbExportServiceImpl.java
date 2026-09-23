package com.huawei.coworkdata.service.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.huawei.coworkdata.service.DbExportService;
import com.huawei.coworkdata.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 按 schema.sql 白名单表导出为 ZIP；表名仅来自常量，不接受外部输入。
 */
@Service
@RequiredArgsConstructor
public class DbExportServiceImpl implements DbExportService {

    /** 与 schema.sql 业务表对齐；顺序固定便于核对。 */
    static final List<String> ALLOWED_TABLES = Collections.unmodifiableList(Arrays.asList(
            "sessions",
            "tasks",
            "events",
            "session_sse_events",
            "snapshots",
            "memory_events",
            "memory_subscriptions",
            "agent_templates",
            "user_profile",
            "cowork",
            "cowork_permission"
    ));

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void writeAllTables(OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        try {
            writeManifest(zip);
            for (String table : ALLOWED_TABLES) {
                writeTableEntry(zip, table);
            }
            zip.finish();
        } finally {
            zip.flush();
        }
    }

    private void writeManifest(ZipOutputStream zip) throws IOException {
        zip.putNextEntry(new ZipEntry("manifest.json"));
        JsonGenerator gen = openGenerator(zip);
        try {
            gen.writeStartObject();
            gen.writeStringField("exported_at", Instant.now().toString());
            gen.writeArrayFieldStart("tables");
            for (String table : ALLOWED_TABLES) {
                gen.writeString(table);
            }
            gen.writeEndArray();
            gen.writeEndObject();
            gen.flush();
        } finally {
            gen.close();
        }
        zip.closeEntry();
    }

    private void writeTableEntry(ZipOutputStream zip, String table) throws IOException {
        zip.putNextEntry(new ZipEntry(table + ".json"));
        JsonGenerator gen = openGenerator(zip);
        try {
            gen.writeStartArray();
            // 表名仅来自白名单常量，禁止拼接外部输入
            jdbcTemplate.query("SELECT * FROM " + table, rs -> {
                try {
                    gen.writeObject(rowToMap(rs));
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to write row for table " + table, e);
                }
            });
            gen.writeEndArray();
            gen.flush();
        } finally {
            gen.close();
        }
        zip.closeEntry();
    }

    /** 不关闭底层 ZipOutputStream，以便继续写入后续 entry。 */
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
