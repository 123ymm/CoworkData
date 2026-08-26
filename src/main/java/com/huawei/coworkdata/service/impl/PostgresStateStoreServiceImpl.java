package com.huawei.coworkdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huawei.coworkdata.dto.SessionRecordDto;
import com.huawei.coworkdata.entity.SessionEntity;
import com.huawei.coworkdata.entity.SessionSseEventEntity;
import com.huawei.coworkdata.entity.TaskEntity;
import com.huawei.coworkdata.mapper.SessionMapper;
import com.huawei.coworkdata.mapper.SessionSseEventMapper;
import com.huawei.coworkdata.mapper.TaskMapper;
import com.huawei.coworkdata.service.PostgresStateStoreService;
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
public class PostgresStateStoreServiceImpl implements PostgresStateStoreService {

    private static final Logger log = LoggerFactory.getLogger(PostgresStateStoreServiceImpl.class);

    private final SessionMapper sessionMapper;
    private final TaskMapper taskMapper;
    private final SessionSseEventMapper sessionSseEventMapper;

    @Override
    public List<SessionRecordDto> listSessions() {
        List<SessionEntity> rows = sessionMapper.selectList(
                new LambdaQueryWrapper<SessionEntity>().orderByDesc(SessionEntity::getCreatedAt));
        return rows.stream().map(this::toRecord).toList();
    }

    @Override
    public List<SessionRecordDto> listSessionsByUserId(String userId) {
        List<SessionEntity> rows = sessionMapper.selectList(
                new LambdaQueryWrapper<SessionEntity>()
                        .eq(SessionEntity::getUserId, userId)
                        .orderByDesc(SessionEntity::getCreatedAt));
        return rows.stream().map(this::toRecord).toList();
    }

    @Override
    public SessionRecordDto getSessionRecord(String sessionId) {
        SessionEntity row = sessionMapper.selectById(sessionId);
        return row == null ? null : toRecord(row);
    }

    @Override
    public int getLastUploadIndex(String sessionId) {
        SessionEntity row = sessionMapper.selectById(sessionId);
        if (row == null) {
            return 0;
        }
        return row.getLastUploadIndex() != null ? row.getLastUploadIndex() : 0;
    }

    @Override
    public List<String> listActiveSessionIds() {
        return sessionMapper.selectList(
                        new LambdaQueryWrapper<SessionEntity>()
                                .eq(SessionEntity::getStatus, "RUNNING")
                                .select(SessionEntity::getId))
                .stream()
                .map(SessionEntity::getId)
                .toList();
    }

    @Override
    public List<Map<String, Object>> loadTasks(String sessionId) {
        List<TaskEntity> rows = taskMapper.selectList(
                new LambdaQueryWrapper<TaskEntity>().eq(TaskEntity::getSessionId, sessionId));
        return rows.stream().map(this::toTaskDict).toList();
    }

    @Override
    @Transactional
    public void appendSseEvent(String sessionId, String eventJson) {
        SessionSseEventEntity entity = new SessionSseEventEntity();
        entity.setSessionId(sessionId);
        entity.setEventJson(eventJson);
        sessionSseEventMapper.insert(entity);
    }

    @Override
    public List<String> loadSseEvents(String sessionId) {
        return sessionSseEventMapper.selectList(
                        new LambdaQueryWrapper<SessionSseEventEntity>()
                                .eq(SessionSseEventEntity::getSessionId, sessionId)
                                .orderByAsc(SessionSseEventEntity::getId))
                .stream()
                .map(SessionSseEventEntity::getEventJson)
                .toList();
    }

    @Override
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

    @Override
    public String getWorkspace(String sessionId) {
        SessionEntity row = sessionMapper.selectById(sessionId);
        return row == null ? null : row.getWorkspace();
    }

    @Override
    public String getSessionStatus(String sessionId) {
        SessionEntity row = sessionMapper.selectById(sessionId);
        return row == null ? null : row.getStatus();
    }

    @Override
    @Transactional
    public void updateSessionStatus(String sessionId, String status) {
        SessionEntity update = new SessionEntity();
        update.setId(sessionId);
        update.setStatus(status);
        sessionMapper.updateById(update);
    }

    @Override
    @Transactional
    public void updateLastUploadIndex(String sessionId, int lastUploadIndex) {
        SessionEntity update = new SessionEntity();
        update.setId(sessionId);
        update.setLastUploadIndex(lastUploadIndex);
        int updated = sessionMapper.updateById(update);
        if (updated == 0) {
            log.warn("update_last_upload_index: session {} row not found", sessionId);
        }
    }

    @Override
    @Transactional
    public void updateUserId(String sessionId, String userId) {
        SessionEntity update = new SessionEntity();
        update.setId(sessionId);
        update.setUserId(userId);
        sessionMapper.updateById(update);
    }

    @Override
    @Transactional
    public void ensureSessionForUpload(String sessionId, String userId) {
        SessionEntity existing = sessionMapper.selectById(sessionId);
        if (existing != null) {
            if ((existing.getUserId() == null || existing.getUserId().isBlank())
                    && userId != null && !userId.isBlank()) {
                updateUserId(sessionId, userId);
            }
            return;
        }
        SessionEntity entity = new SessionEntity();
        entity.setId(sessionId);
        entity.setTenantId("default");
        entity.setUserId(userId);
        entity.setUserPrompt("");
        entity.setStatus("RUNNING");
        entity.setGoal("");
        entity.setTokenBudget(200_000L);
        entity.setFailureCounter(0);
        entity.setConfigJson("{}");
        entity.setLastUploadIndex(0);
        entity.setCreatedAt(OffsetDateTime.now());
        sessionMapper.insert(entity);
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId) {
        // 软删：仅写入 delete_at，关联 events/tasks 等保留以便追溯
        sessionMapper.deleteById(sessionId);
    }

    private SessionRecordDto toRecord(SessionEntity row) {
        SessionRecordDto dto = new SessionRecordDto();
        dto.setId(row.getId());
        dto.setTenantId(row.getTenantId());
        dto.setUserId(row.getUserId());
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
        dto.setLastUploadIndex(row.getLastUploadIndex() != null ? row.getLastUploadIndex() : 0);
        dto.setDeleteAt(row.getDeleteAt());
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
