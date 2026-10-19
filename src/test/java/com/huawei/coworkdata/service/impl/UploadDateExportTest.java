package com.huawei.coworkdata.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.huawei.coworkdata.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;

class UploadDateExportTest {
    private JdbcTemplate jdbc;
    private DbExportServiceImpl exporter;
    @BeforeEach void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE DOMAIN TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        exporter = new DbExportServiceImpl(jdbc, 1); // Exercise every pagination boundary.
        for (String id : new String[]{"a", "b", "c"}) {
            jdbc.update("INSERT INTO sessions(id,user_id,cowork_id) VALUES(?,?,?)", id, "u"+id, "c"+id);
            jdbc.update("INSERT INTO user_profile(user_id) VALUES(?)", "u"+id);
            jdbc.update("INSERT INTO cowork(cowork_id) VALUES(?)", "c"+id);
            jdbc.update("INSERT INTO cowork_permission(cowork_id) VALUES(?)", "c"+id);
            for (String table : new String[]{"tasks", "events", "snapshots", "memory_events", "memory_subscriptions"}) {
                jdbc.update("INSERT INTO " + table + "(id,session_id) VALUES(?,?)", id, id);
            }
            jdbc.update("INSERT INTO session_sse_events(session_id) VALUES(?)", id);
        }
        activity("a", "2026-10-01");
        activity("a", "2026-10-02");
        activity("b", "2026-10-02");
        activity("c", "2026-10-03");
    }
    private void activity(String id, String day) {
        jdbc.update("INSERT INTO session_upload_daily VALUES(?,CAST(? AS DATE),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, day);
    }
    private Map<String, JsonNode> readZip(byte[] bytes) throws Exception {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream file = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int n;
                while ((n = zip.read(buffer)) != -1) file.write(buffer, 0, n);
                result.put(entry.getName(), JsonUtils.mapper().readTree(file.toByteArray()));
            }
        }
        // EOCD signature: a complete ZIP, not merely recoverable local entries.
        assertEquals(0x50, bytes[bytes.length-22] & 255);
        assertEquals(0x4b, bytes[bytes.length-21] & 255);
        assertEquals(0x05, bytes[bytes.length-20] & 255);
        assertEquals(0x06, bytes[bytes.length-19] & 255);
        return result;
    }
    @Test void filtersRelatedTablesAndDeduplicatesSessionsAcrossDays() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.writeByUploadDate(out, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"));
        Map<String, JsonNode> files = readZip(out.toByteArray());
        assertFalse(files.containsKey("agent_templates.json"));
        for (Map.Entry<String, JsonNode> file : files.entrySet()) {
            if (!file.getValue().isArray()) continue;
            assertEquals(2, file.getValue().size(), file.getKey());
        }
        assertEquals("a", files.get("sessions.json").get(0).get("id").asText());
        assertEquals("b", files.get("sessions.json").get(1).get("id").asText());
        assertEquals("Asia/Shanghai", files.get("manifest.json").get("timezone").asText());
        assertEquals(2, files.get("export-summary.json").get("events").asInt());
    }
    @Test void emptyRangeStillProducesValidArchive() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.writeByUploadDate(out, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-01"));
        Map<String, JsonNode> files = readZip(out.toByteArray());
        assertEquals(0, files.get("sessions.json").size());
        assertEquals(0, files.get("events.json").size());
    }
    @Test void allExportStillIncludesAllBusinessTables() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.writeAllTables(out);
        Map<String, JsonNode> files = readZip(out.toByteArray());
        assertEquals(3, files.get("sessions.json").size());
        assertTrue(files.containsKey("agent_templates.json"));
    }
    @Test void reversedRangeFailsBeforeWritingBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(IllegalArgumentException.class, () -> exporter.writeByUploadDate(out,
                LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-01")));
        assertEquals(0, out.size());
    }
}
