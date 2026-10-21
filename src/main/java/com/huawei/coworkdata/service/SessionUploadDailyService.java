package com.huawei.coworkdata.service;

import com.huawei.coworkdata.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Upload activity only; never infer historical upload dates from creation timestamps. */
@Service
@RequiredArgsConstructor
public class SessionUploadDailyService {
    private static final ZoneId UPLOAD_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId is required for upload activity");
        }
        // openGauss 7 RC and PostgreSQL differ in their UPSERT syntax. Use ordinary
        // UPDATE/INSERT on the caller's connection, keeping the business write atomic.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if (connection.getAutoCommit()) {
                throw new IllegalStateException("Upload activity requires an active transaction");
            }
            Timestamp now;
            try (PreparedStatement clock = connection.prepareStatement("SELECT statement_timestamp()");
                 ResultSet result = clock.executeQuery()) {
                if (!result.next() || (now = result.getTimestamp(1, utcCalendar())) == null) {
                    throw new SQLException("Database did not return an upload timestamp");
                }
            }
            String date = now.toInstant().atZone(UPLOAD_ZONE).toLocalDate().toString();
            if (updateSummary(connection, sessionId, date, now) == 1) {
                return null;
            }

            // Concurrent first uploads may both find no row. A failed INSERT aborts
            // PostgreSQL/openGauss transactions; restore a savepoint before retrying.
            Savepoint savepoint = connection.setSavepoint();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO session_upload_daily "
                    + "(session_id, upload_date, first_uploaded_at, last_uploaded_at) "
                    + "VALUES (?, CAST(? AS DATE), ?, ?)")) {
                insert.setString(1, sessionId);
                insert.setString(2, date);
                insert.setObject(3, now.toInstant().atOffset(ZoneOffset.UTC));
                insert.setObject(4, now.toInstant().atOffset(ZoneOffset.UTC));
                insert.executeUpdate();
            } catch (SQLException failure) {
                try {
                    connection.rollback(savepoint);
                    connection.releaseSavepoint(savepoint);
                } catch (SQLException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                    throw failure;
                }
                if (!"23505".equals(failure.getSQLState())) {
                    throw failure;
                }
                // READ COMMITTED sees the winning insert after the conflict. Never
                // report success if a different isolation level/deletion hides it.
                if (updateSummary(connection, sessionId, date, now) != 1) {
                    throw failure;
                }
                return null;
            }
            connection.releaseSavepoint(savepoint);
            return null;
        });
    }

    private int updateSummary(Connection connection, String sessionId, String date, Timestamp now)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE session_upload_daily SET "
                + "first_uploaded_at = LEAST(first_uploaded_at, ?), "
                + "last_uploaded_at = GREATEST(last_uploaded_at, ?) "
                + "WHERE session_id = ? AND upload_date = CAST(? AS DATE)")) {
            update.setObject(1, now.toInstant().atOffset(ZoneOffset.UTC));
            update.setObject(2, now.toInstant().atOffset(ZoneOffset.UTC));
            update.setString(3, sessionId);
            update.setString(4, date);
            return update.executeUpdate();
        }
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
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
