package com.huawei.coworkdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huawei.coworkdata.entity.EventEntity;
import com.huawei.coworkdata.entity.SessionEntity;
import com.huawei.coworkdata.entity.SessionSseEventEntity;
import com.huawei.coworkdata.entity.SnapshotEntity;
import com.huawei.coworkdata.entity.TaskEntity;
import com.huawei.coworkdata.mapper.EventMapper;
import com.huawei.coworkdata.mapper.SessionMapper;
import com.huawei.coworkdata.mapper.SessionSseEventMapper;
import com.huawei.coworkdata.mapper.SnapshotMapper;
import com.huawei.coworkdata.mapper.TaskMapper;
import com.huawei.coworkdata.service.SessionExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SessionExportServiceImpl implements SessionExportService {

    private final SessionMapper sessionMapper;
    private final TaskMapper taskMapper;
    private final EventMapper eventMapper;
    private final SessionSseEventMapper sessionSseEventMapper;
    private final SnapshotMapper snapshotMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> exportBundle(String sessionId) {
        SessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("sessions", List.of(sessionRow(session)));
        bundle.put("tasks", taskRows(sessionId));
        bundle.put("events", eventRows(sessionId));
        bundle.put("session_sse_events", sseRows(sessionId));
        bundle.put("snapshots", snapshotRows(sessionId));
        bundle.put("memory_events", memoryEventRows(sessionId));
        bundle.put("memory_subscriptions", memorySubscriptionRows(sessionId));
        return bundle;
    }

    private Map<String, Object> sessionRow(SessionEntity s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", s.getId());
        row.put("tenant_id", s.getTenantId());
        row.put("user_prompt", s.getUserPrompt());
        row.put("status", s.getStatus());
        row.put("goal", s.getGoal());
        row.put("root_agent_id", s.getRootAgentId());
        row.put("llm_provider", s.getLlmProvider());
        row.put("llm_model", s.getLlmModel());
        row.put("token_budget", s.getTokenBudget());
        row.put("failure_counter", s.getFailureCounter());
        row.put("config_json", s.getConfigJson());
        row.put("workspace", s.getWorkspace());
        row.put("created_at", s.getCreatedAt());
        row.put("updated_at", s.getUpdatedAt());
        return row;
    }

    private List<Map<String, Object>> taskRows(String sessionId) {
        List<TaskEntity> rows = taskMapper.selectList(
                new LambdaQueryWrapper<TaskEntity>().eq(TaskEntity::getSessionId, sessionId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (TaskEntity t : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("session_id", t.getSessionId());
            row.put("status", t.getStatus());
            row.put("title", t.getTitle());
            row.put("description", t.getDescription());
            row.put("user_prompt", t.getUserPrompt());
            row.put("assigned_agent_id", t.getAssignedAgentId());
            row.put("creator_agent_id", t.getCreatorAgentId());
            row.put("is_daemon", t.getIsDaemon());
            row.put("outputs_json", t.getOutputsJson());
            row.put("error", t.getError());
            row.put("created_at", t.getCreatedAt());
            row.put("updated_at", t.getUpdatedAt());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> eventRows(String sessionId) {
        List<EventEntity> rows = eventMapper.selectList(
                new LambdaQueryWrapper<EventEntity>()
                        .eq(EventEntity::getSessionId, sessionId)
                        .orderByAsc(EventEntity::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (EventEntity e : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("run_id", e.getRunId());
            row.put("session_id", e.getSessionId());
            row.put("task_id", e.getTaskId());
            row.put("agent_id", e.getAgentId());
            row.put("tenant_id", e.getTenantId());
            row.put("type", e.getType());
            row.put("sequence", e.getSequence());
            row.put("payload_json", e.getPayloadJson());
            row.put("metadata_json", e.getMetadataJson());
            row.put("causation_id", e.getCausationId());
            row.put("timestamp", e.getTimestamp());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> sseRows(String sessionId) {
        List<SessionSseEventEntity> rows = sessionSseEventMapper.selectList(
                new LambdaQueryWrapper<SessionSseEventEntity>()
                        .eq(SessionSseEventEntity::getSessionId, sessionId)
                        .orderByAsc(SessionSseEventEntity::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (SessionSseEventEntity e : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("session_id", e.getSessionId());
            row.put("event_json", e.getEventJson());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> snapshotRows(String sessionId) {
        List<SnapshotEntity> rows = snapshotMapper.selectList(
                new LambdaQueryWrapper<SnapshotEntity>().eq(SnapshotEntity::getSessionId, sessionId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (SnapshotEntity s : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.getId());
            row.put("session_id", s.getSessionId());
            row.put("last_event_id", s.getLastEventId());
            row.put("last_event_sequence", s.getLastEventSequence());
            row.put("state_blob_json", s.getStateBlobJson());
            row.put("snapshot_reason", s.getSnapshotReason());
            row.put("created_at", s.getCreatedAt());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> memoryEventRows(String sessionId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT id, session_id, task_id, agent_id, layer, type, role, topic, content,"
                            + " seq_no, topic_seq_no, is_superseded, metadata_json, timestamp"
                            + " FROM memory_events WHERE session_id = ? ORDER BY timestamp, seq_no",
                    sessionId);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> memorySubscriptionRows(String sessionId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT id, session_id, task_id, topic, cursor, intent, created_at"
                            + " FROM memory_subscriptions WHERE session_id = ?",
                    sessionId);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
