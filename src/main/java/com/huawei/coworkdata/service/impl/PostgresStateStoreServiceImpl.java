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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        return rows.stream().map(this::toRecord).collect(Collectors.toList());
    }

    @Override
    public List<SessionRecordDto> listSessionsByUserId(String userId) {
        // 过渡：工号或 surrogate 都能查到
        List<SessionEntity> rows = sessionMapper.selectList(
                new LambdaQueryWrapper<SessionEntity>()
                        .and(w -> w.eq(SessionEntity::getUserId, userId)
                                .or()
                                .eq(SessionEntity::getSurrogateId, userId))
                        .orderByDesc(SessionEntity::getCreatedAt));
        return rows.stream().map(this::toRecord).collect(Collectors.toList());
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
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> loadTasks(String sessionId) {
        List<TaskEntity> rows = taskMapper.selectList(
                new LambdaQueryWrapper<TaskEntity>().eq(TaskEntity::getSessionId, sessionId));
        return rows.stream().map(this::toTaskDict).collect(Collectors.toList());
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
    @Transactional
    public void clearSseEvents(String sessionId) {
        sessionSseEventMapper.delete(
                new LambdaQueryWrapper<SessionSseEventEntity>()
                        .eq(SessionSseEventEntity::getSessionId, sessionId));
    }

    @Override
    public List<String> loadSseEvents(String sessionId) {
        return sessionSseEventMapper.selectList(
                        new LambdaQueryWrapper<SessionSseEventEntity>()
                                .eq(SessionSseEventEntity::getSessionId, sessionId)
                                .orderByAsc(SessionSseEventEntity::getId))
                .stream()
                .map(SessionSseEventEntity::getEventJson)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void upsertTasks(String sessionId, List<Map<String, Object>> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        // 会话须已存在（FK）
        if (sessionMapper.selectById(sessionId) == null) {
            ensureSessionForUpload(sessionId, null, null, null, "local", null, null);
        }
        for (Map<String, Object> raw : tasks) {
            if (raw == null) {
                continue;
            }
            String taskId = firstString(raw, "id");
            if (taskId == null || taskId.isEmpty()) {
                continue;
            }
            TaskEntity entity = taskMapper.selectById(taskId);
            boolean insert = entity == null;
            if (insert) {
                entity = new TaskEntity();
                entity.setId(taskId);
            }
            entity.setSessionId(sessionId);
            String status = firstString(raw, "status");
            if (status != null) {
                entity.setStatus(status);
            } else if (insert) {
                entity.setStatus("PENDING");
            }
            String title = firstString(raw, "title");
            if (title != null || insert) {
                entity.setTitle(title != null ? title : "");
            }
            String description = firstString(raw, "description");
            if (description != null || insert) {
                entity.setDescription(description != null ? description : "");
            }
            if (raw.containsKey("user_prompt") || raw.containsKey("userPrompt")) {
                entity.setUserPrompt(firstString(raw, "user_prompt", "userPrompt"));
            }
            if (raw.containsKey("assigned_agent_id") || raw.containsKey("assignedAgentId")) {
                entity.setAssignedAgentId(firstString(raw, "assigned_agent_id", "assignedAgentId"));
            }
            if (raw.containsKey("creator_agent_id") || raw.containsKey("creatorAgentId")) {
                entity.setCreatorAgentId(firstString(raw, "creator_agent_id", "creatorAgentId"));
            }
            if (raw.containsKey("is_daemon") || raw.containsKey("isDaemon")) {
                Object d = raw.containsKey("is_daemon") ? raw.get("is_daemon") : raw.get("isDaemon");
                entity.setIsDaemon(d instanceof Boolean ? (Boolean) d : Boolean.FALSE);
            } else if (insert) {
                entity.setIsDaemon(Boolean.FALSE);
            }
            if (raw.containsKey("outputs") || raw.containsKey("outputs_json") || raw.containsKey("outputsJson")) {
                Object outputs = raw.containsKey("outputs") ? raw.get("outputs")
                        : (raw.containsKey("outputs_json") ? raw.get("outputs_json") : raw.get("outputsJson"));
                if (outputs instanceof String) {
                    entity.setOutputsJson((String) outputs);
                } else {
                    entity.setOutputsJson(JsonUtils.toJson(outputs));
                }
            } else if (insert) {
                entity.setOutputsJson("null");
            }
            if (raw.containsKey("error")) {
                Object err = raw.get("error");
                entity.setError(err == null ? null : String.valueOf(err));
            }
            OffsetDateTime created = parseOffset(raw.get("created_at"));
            if (created == null) {
                created = parseOffset(raw.get("createdAt"));
            }
            if (created != null) {
                entity.setCreatedAt(created);
            } else if (insert) {
                entity.setCreatedAt(OffsetDateTime.now());
            }
            OffsetDateTime updated = parseOffset(raw.get("updated_at"));
            if (updated == null) {
                updated = parseOffset(raw.get("updatedAt"));
            }
            entity.setUpdatedAt(updated != null ? updated : OffsetDateTime.now());
            if (insert) {
                taskMapper.insert(entity);
            } else {
                taskMapper.updateById(entity);
            }
        }
    }

    private static String firstString(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            if (!raw.containsKey(key)) {
                continue;
            }
            Object v = raw.get(key);
            if (v == null) {
                return null;
            }
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    private static OffsetDateTime parseOffset(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime) {
            return (OffsetDateTime) value;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(s.replace(" ", "T"));
            } catch (Exception e2) {
                return null;
            }
        }
    }

    @Override
    @Transactional
    public void saveWorkspace(String sessionId, String workspace) {
        SessionEntity update = new SessionEntity();
        update.setId(sessionId);
        update.setWorkspace(workspace);
        update.setUpdatedAt(OffsetDateTime.now());
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
        update.setUpdatedAt(OffsetDateTime.now());
        sessionMapper.updateById(update);
    }

    @Override
    @Transactional
    public void updateLastUploadIndex(String sessionId, int lastUploadIndex) {
        SessionEntity update = new SessionEntity();
        update.setId(sessionId);
        update.setLastUploadIndex(lastUploadIndex);
        update.setUpdatedAt(OffsetDateTime.now());
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
    public void updateSessionIdentity(String sessionId, String userId, String surrogateId, String tenantId,
                                      String source, String installId, String coworkId) {
        SessionEntity update = new SessionEntity();
        update.setId(sessionId);
        boolean any = false;
        if (userId != null && !userId.trim().isEmpty()) {
            update.setUserId(userId.trim());
            any = true;
        }
        if (surrogateId != null && !surrogateId.trim().isEmpty()) {
            update.setSurrogateId(surrogateId.trim());
            any = true;
        }
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            update.setTenantId(tenantId.trim());
            any = true;
        }
        if (source != null && !source.trim().isEmpty()) {
            update.setSource(source.trim());
            any = true;
        }
        if (installId != null && !installId.trim().isEmpty()) {
            update.setInstallId(installId.trim());
            any = true;
        }
        if (coworkId != null && !coworkId.trim().isEmpty()) {
            update.setCoworkId(coworkId.trim());
            any = true;
        }
        if (any) {
            sessionMapper.updateById(update);
        }
    }

    @Override
    @Transactional
    public void ensureSessionForUpload(String sessionId, String userId, String surrogateId,
                                       String tenantId, String source, String installId, String coworkId) {
        SessionEntity existing = sessionMapper.selectById(sessionId);
        if (existing != null) {
            updateSessionIdentity(sessionId,
                    (existing.getUserId() == null || existing.getUserId().trim().isEmpty()) ? userId : null,
                    (existing.getSurrogateId() == null || existing.getSurrogateId().trim().isEmpty()) ? surrogateId : null,
                    (existing.getTenantId() == null || existing.getTenantId().trim().isEmpty()
                            || "default".equals(existing.getTenantId())) ? tenantId : null,
                    (existing.getSource() == null || existing.getSource().trim().isEmpty()) ? source : null,
                    (existing.getInstallId() == null || existing.getInstallId().trim().isEmpty()) ? installId : null,
                    (existing.getCoworkId() == null || existing.getCoworkId().trim().isEmpty()) ? coworkId : null);
            return;
        }
        String resolvedCowork = blankToNull(coworkId);
        String resolvedUser = blankToNull(userId);
        String resolvedSurrogate = blankToNull(surrogateId);
        String resolvedTenant = blankToNull(tenantId);
        // tenant = "{surrogate_id}:{cowork_id}"
        if (resolvedTenant == null && resolvedSurrogate != null && resolvedCowork != null) {
            resolvedTenant = resolvedSurrogate + ":" + resolvedCowork;
        }
        if (resolvedTenant == null) {
            resolvedTenant = "default";
        }
        String resolvedSource = blankToNull(source);
        if (resolvedSource == null) {
            resolvedSource = "local";
        }
        SessionEntity entity = new SessionEntity();
        entity.setId(sessionId);
        entity.setTenantId(resolvedTenant);
        entity.setUserId(resolvedUser);
        entity.setSurrogateId(resolvedSurrogate);
        entity.setCoworkId(resolvedCowork);
        entity.setSource(resolvedSource);
        entity.setInstallId(blankToNull(installId));
        entity.setUserPrompt("");
        entity.setStatus("RUNNING");
        entity.setTitle("");
        entity.setGoal("");
        entity.setTokenBudget(200_000L);
        entity.setFailureCounter(0);
        entity.setConfigJson("{}");
        entity.setLastUploadIndex(0);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        sessionMapper.insert(entity);
    }

    @Override
    @Transactional
    public void backfillSessionProjection(String sessionId, String userPrompt, String goal,
                                          String title, String status, String rootAgentId,
                                          String llmProvider, String llmModel,
                                          Long tokenBudget, Integer failureCounter,
                                          String configJson, String workspace,
                                          OffsetDateTime createdAt) {
        SessionEntity existing = sessionMapper.selectById(sessionId);
        if (existing == null) {
            return;
        }
        SessionEntity update = new SessionEntity();
        update.setId(sessionId);
        boolean any = false;
        // 地上游为准：请求非空则覆盖（修复 ensureSession 空壳 + SessionCreated 被跳过）
        if (!isBlank(userPrompt) && !userPrompt.trim().equals(
                existing.getUserPrompt() != null ? existing.getUserPrompt() : "")) {
            update.setUserPrompt(userPrompt.trim());
            any = true;
        }
        if (!isBlank(goal) && !goal.trim().equals(
                existing.getGoal() != null ? existing.getGoal() : "")) {
            update.setGoal(goal.trim());
            any = true;
        }
        if (!isBlank(title) && !title.trim().equals(
                existing.getTitle() != null ? existing.getTitle() : "")) {
            update.setTitle(title.trim());
            any = true;
        }
        if (!isBlank(status) && !status.trim().equals(
                existing.getStatus() != null ? existing.getStatus() : "")) {
            update.setStatus(status.trim());
            any = true;
        }
        if (!isBlank(rootAgentId) && !rootAgentId.trim().equals(
                existing.getRootAgentId() != null ? existing.getRootAgentId() : "")) {
            update.setRootAgentId(rootAgentId.trim());
            any = true;
        }
        if (!isBlank(llmProvider) && !llmProvider.trim().equals(
                existing.getLlmProvider() != null ? existing.getLlmProvider() : "")) {
            update.setLlmProvider(llmProvider.trim());
            any = true;
        }
        if (!isBlank(llmModel) && !llmModel.trim().equals(
                existing.getLlmModel() != null ? existing.getLlmModel() : "")) {
            update.setLlmModel(llmModel.trim());
            any = true;
        }
        if (tokenBudget != null && !tokenBudget.equals(existing.getTokenBudget())) {
            update.setTokenBudget(tokenBudget);
            any = true;
        }
        if (failureCounter != null && !failureCounter.equals(existing.getFailureCounter())) {
            update.setFailureCounter(failureCounter);
            any = true;
        }
        if (!isBlank(workspace) && !workspace.trim().equals(
                existing.getWorkspace() != null ? existing.getWorkspace() : "")) {
            update.setWorkspace(workspace.trim());
            any = true;
        }
        if (createdAt != null && (existing.getCreatedAt() == null
                || !createdAt.isEqual(existing.getCreatedAt()))) {
            update.setCreatedAt(createdAt);
            any = true;
        }
        if (!isBlank(configJson)) {
            Map<String, Object> incoming = JsonUtils.parseMap(configJson);
            if (incoming != null && !incoming.isEmpty()) {
                Map<String, Object> merged = new HashMap<>();
                Map<String, Object> cur = JsonUtils.parseMap(existing.getConfigJson());
                if (cur != null) {
                    merged.putAll(cur);
                }
                merged.putAll(incoming);
                String mergedJson = JsonUtils.toJson(merged);
                if (!mergedJson.equals(existing.getConfigJson() != null
                        ? existing.getConfigJson() : "{}")) {
                    update.setConfigJson(mergedJson);
                    any = true;
                }
            }
        }
        if (any) {
            update.setUpdatedAt(OffsetDateTime.now());
            sessionMapper.updateById(update);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String blankToNull(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return s.trim();
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
        dto.setSurrogateId(row.getSurrogateId());
        dto.setCoworkId(row.getCoworkId());
        dto.setSource(row.getSource());
        dto.setInstallId(row.getInstallId());
        dto.setUserPrompt(row.getUserPrompt());
        dto.setStatus(row.getStatus());
        dto.setTitle(row.getTitle() != null ? row.getTitle() : "");
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
        map.put("settings", Collections.emptyMap());
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
