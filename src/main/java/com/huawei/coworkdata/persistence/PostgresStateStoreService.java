package com.huawei.coworkdata.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huawei.coworkdata.dto.SessionRecordDto;
import com.huawei.coworkdata.entity.SessionEntity;
import com.huawei.coworkdata.entity.SessionSseEventEntity;
import com.huawei.coworkdata.entity.TaskEntity;
import com.huawei.coworkdata.mapper.EventMapper;
import com.huawei.coworkdata.mapper.SessionMapper;
import com.huawei.coworkdata.mapper.SessionSseEventMapper;
import com.huawei.coworkdata.mapper.SnapshotMapper;
import com.huawei.coworkdata.mapper.TaskMapper;
import com.huawei.coworkdata.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostgresStateStoreService {

    private static final Logger log = LoggerFactory.getLogger(PostgresStateStoreService.class);

    private final SessionMapper sessionMapper;
    private final TaskMapper taskMapper;
    private final EventMapper eventMapper;
    private final SessionSseEventMapper sessionSseEventMapper;
    private final SnapshotMapper snapshotMapper;

    public List<SessionRecordDto> listSessions() {
        List<SessionEntity> rows = sessionMapper.selectList(
                new LambdaQueryWrapper<SessionEntity>().orderByDesc(SessionEntity::getCreatedAt));
        return rows.stream().map(this::toRecord).toList();
    }

    public SessionRecordDto getSessionRecord(String sessionId) {
        SessionEntity row = sessionMapper.selectById(sessionId);
        return row == null ? null : toRecord(row);
    }

    public List<String> listActiveSessionIds() {
        return sessionMapper.selectList(
                new LambdaQueryWrapper<SessionEntity>()
                        .eq(SessionEntity::getStatus, "RUNNING")
                        .select(SessionEntity::getId))
                .stream()
                .map(SessionEntity::getId)
                .toList();
    }

    public List<Map<String, Object>> loadTasks(String sessionId) {
        List<TaskEntity> rows = taskMapper.selectList(
                new LambdaQueryWrapper<TaskEntity>().eq(TaskEntity::getSessionId, sessionId));
        return rows.stream().map(this::toTaskDict).toList();
    }

    @Transactional
    public void appendSseEvent(String sessionId, String eventJson) {
        SessionSseEventEntity entity = new SessionSseEventEntity();
        entity.setSessionId(sessionId);
        entity.setEventJson(eventJson);
        sessionSseEventMapper.insert(entity);
    }

    public List<String> loadSseEvents(String sessionId) {
        return sessionSseEventMapper.selectList(
                new LambdaQueryWrapper<SessionSseEventEntity>()
                        .eq(SessionSseEventEntity::getSessionId, sessionId)
                        .orderByAsc(SessionSseEventEntity::getId))
                .stream()
                .map(SessionSseEventEntity::getEventJson)
                .toList();
    }

    @Transactional
    public void saveWorkspace(String sessionId, String workspace) {
        SessionEntity update = new SessionEntity();
        update.setId(sessionId);
        update.setWorkspace(workspace);
        int updated = sessionMapper.updateById(update);
        if (updated == 0) {
            log.warn("save_workspace: session {} row not found", sessionId);
        }
    }

    public String getWorkspace(String sessionId) {
        SessionEntity row = sessionMapper.selectById(sessionId);
        return row == null ? null : row.getWorkspace();
    }

    public String getSessionStatus(String sessionId) {
        SessionEntity row = sessionMapper.selectById(sessionId);
        return row == null ? null : row.getStatus();
    }

    @Transactional
    public void updateSessionStatus(String sessionId, String status) {
        SessionEntity update = new SessionEntity();
        update.setId(sessionId);
        update.setStatus(status);
        sessionMapper.updateById(update);
    }

    @Transactional
    public void deleteSession(String sessionId) {
        taskMapper.delete(new LambdaQueryWrapper<TaskEntity>().eq(TaskEntity::getSessionId, sessionId));
        snapshotMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                com.huawei.coworkdata.entity.SnapshotEntity>()
                .eq(com.huawei.coworkdata.entity.SnapshotEntity::getSessionId, sessionId));
        eventMapper.delete(new LambdaQueryWrapper<com.huawei.coworkdata.entity.EventEntity>()
                .eq(com.huawei.coworkdata.entity.EventEntity::getSessionId, sessionId));
        sessionSseEventMapper.delete(
                new LambdaQueryWrapper<SessionSseEventEntity>().eq(SessionSseEventEntity::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    private SessionRecordDto toRecord(SessionEntity row) {
        SessionRecordDto dto = new SessionRecordDto();
        dto.setId(row.getId());
        dto.setTenantId(row.getTenantId());
        dto.setUserPrompt(row.getUserPrompt());
        dto.setStatus(row.getStatus());
        dto.setGoal(row.getGoal() != null ? row.getGoal() : "");
        dto.setRootAgentId(row.getRootAgentId());
        dto.setLlmProvider(row.getLlmProvider());
        dto.setLlmModel(row.getLlmModel());
        dto.setTokenBudget(row.getTokenBudget() != null ? row.getTokenBudget() : 0L);
        dto.setFailureCounter(row.getFailureCounter() != null ? row.getFailureCounter() : 0);
        dto.setConfig(JsonUtils.parseMap(row.getConfigJson()));
        dto.setWorkspace(row.getWorkspace());
        dto.setCreatedAt(row.getCreatedAt());
        dto.setUpdatedAt(row.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toTaskDict(TaskEntity row) {
        String ts = utcIso(row.getCreatedAt());
        String updated = utcIso(row.getUpdatedAt());
        if (updated.isEmpty()) {
            updated = ts;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", row.getId());
        map.put("session_id", row.getSessionId());
        map.put("status", row.getStatus());
        map.put("title", row.getTitle());
        map.put("description", row.getDescription());
        map.put("user_prompt", row.getUserPrompt() != null ? row.getUserPrompt() : "");
        map.put("assigned_agent_id", row.getAssignedAgentId() != null ? row.getAssignedAgentId() : "");
        map.put("creator_agent_id", row.getCreatorAgentId() != null ? row.getCreatorAgentId() : "");
        map.put("settings", Map.of());
        map.put("result", null);
        map.put("outputs", JsonUtils.parse(row.getOutputsJson(), Object.class));
        map.put("error", row.getError());
        map.put("created_at", ts);
        map.put("updated_at", updated);
        map.put("is_daemon", row.getIsDaemon() != null && row.getIsDaemon());
        return map;
    }

    private static String utcIso(OffsetDateTime dt) {
        if (dt == null) {
            return "";
        }
        return dt.withOffsetSameInstant(ZoneOffset.UTC).toString();
    }
}
