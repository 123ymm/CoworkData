package com.huawei.coworkdata.service;

import com.huawei.coworkdata.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Upload activity only; never infer historical upload dates from creation timestamps. */
@Service
@RequiredArgsConstructor
public class SessionUploadDailyService {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId is required for upload activity");
        }
        // Use one database timestamp for both the Shanghai date and the recorded instant.
        jdbc.update("INSERT INTO session_upload_daily "
                + "(session_id, upload_date, first_uploaded_at, last_uploaded_at) "
                + "SELECT ?, CAST(t AT TIME ZONE 'Asia/Shanghai' AS DATE), t, t "
                + "FROM (SELECT statement_timestamp() AS t) clock "
                + "ON CONFLICT (session_id, upload_date) DO UPDATE SET "
                + "first_uploaded_at = LEAST(session_upload_daily.first_uploaded_at, EXCLUDED.first_uploaded_at), "
                + "last_uploaded_at = GREATEST(session_upload_daily.last_uploaded_at, EXCLUDED.last_uploaded_at)",
                sessionId);
    }

    /** Lock a single row within the caller's transaction, never load a whole session. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Object> snapshot(String table, String id) {
        if (!Arrays.asList("sessions", "tasks", "snapshots").contains(table)) {
            throw new IllegalArgumentException("Unsupported upload table");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE id = ? FOR UPDATE", id);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rows.get(0).forEach((column, raw) -> {
            String key = column.toLowerCase(Locale.ROOT);
            if ("updated_at".equals(key) || "last_upload_index".equals(key)) {
                return;
            }
            Object value = raw;
            if (value instanceof Timestamp) {
                value = ((Timestamp) value).toInstant();
            } else if (value instanceof OffsetDateTime) {
                value = ((OffsetDateTime) value).toInstant();
            } else if (value instanceof String && key.endsWith("_json")) {
                try {
                    value = JsonUtils.mapper().readTree((String) value);
                } catch (Exception ignored) {
                    // Legacy non-JSON text must still compare by its actual value.
                }
            }
            result.put(key, value);
        });
        return result;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordIfChanged(String table, String id, String sessionId, Map<String, Object> before) {
        if (!java.util.Objects.equals(before, snapshot(table, id))) {
            record(sessionId);
        }
    }
}
