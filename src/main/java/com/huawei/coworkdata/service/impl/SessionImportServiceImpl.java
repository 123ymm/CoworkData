package com.huawei.coworkdata.service.impl;

import com.huawei.coworkdata.service.SessionImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SessionImportServiceImpl implements SessionImportService {

    private static final List<String> TABLE_ORDER = Collections.unmodifiableList(Arrays.asList(
            "sessions", "tasks", "events", "memory_events",
            "memory_subscriptions", "session_sse_events", "snapshots"));

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public String importBundle(Map<String, Object> bundle) {
        List<Map<String, Object>> sessions = (List<Map<String, Object>>) bundle.get("sessions");
        if (sessions == null || sessions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bundle has no sessions");
        }
        String newSessionId = String.valueOf(sessions.get(0).get("id"));

        for (String table : TABLE_ORDER) {
            List<Map<String, Object>> rows = (List<Map<String, Object>>) bundle.get(table);
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            for (Map<String, Object> row : rows) {
                insertRow(table, row);
            }
        }
        return newSessionId;
    }

    private void insertRow(String table, Map<String, Object> row) {
        Map<String, Object> cols = new LinkedHashMap<>(row);
        if ("session_sse_events".equals(table)) {
            cols.remove("id");
        }
        if (cols.isEmpty()) {
            return;
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
        StringBuilder vals = new StringBuilder(" VALUES (");
        Object[] args = new Object[cols.size()];
        int i = 0;
        for (Map.Entry<String, Object> e : cols.entrySet()) {
            if (i > 0) {
                sql.append(", ");
                vals.append(", ");
            }
            sql.append(e.getKey());
            vals.append("?");
            args[i++] = e.getValue();
        }
        sql.append(")").append(vals).append(")");
        jdbcTemplate.update(sql.toString(), args);
    }
}
