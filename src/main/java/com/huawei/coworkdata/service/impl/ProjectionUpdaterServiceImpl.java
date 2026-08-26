package com.huawei.coworkdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.entity.SessionEntity;
import com.huawei.coworkdata.entity.TaskEntity;
import com.huawei.coworkdata.mapper.SessionMapper;
import com.huawei.coworkdata.mapper.TaskMapper;
import com.huawei.coworkdata.service.ProjectionUpdaterService;
import com.huawei.coworkdata.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectionUpdaterServiceImpl implements ProjectionUpdaterService {

    private static final Logger log = LoggerFactory.getLogger(ProjectionUpdaterServiceImpl.class);

    private static final Set<String> TRANSIENT_EVENT_TYPES = Set.of(
            "text_delta", "reasoning_delta", "TextDelta", "ReasoningDelta"
    );

    private static final Set<String> VALID_SESSION_STATUSES = Set.of(
            "RUNNING", "SUCCEEDED", "FAILED", "CANCELED", "INTERRUPTED", "PAUSED_HITL", "PAUSED"
    );

    private static final Map<String, String> TASK_STATUS_BY_EVENT = Map.of(
            "TaskStarted", "ACTIVE",
            "TaskResumed", "ACTIVE",
            "TaskSuspended", "SUSPENDED",
            "TaskRequeued", "PENDING",
            "TaskFinished", "FINISHED",
            "TaskFailed", "FAILED",
            "TaskCanceled", "CANCELED"
    );

    private final SessionMapper sessionMapper;
    private final TaskMapper taskMapper;

    @Override
    public void onEvent(EventDto event) {
        if (event.getType() != null && TRANSIENT_EVENT_TYPES.contains(event.getType())) {
            return;
        }
        try {
            handle(event);
        } catch (Exception e) {
            log.error("ProjectionUpdater: error on event {} ({})", event.getId(), event.getType(), e);
        }
    }

    private void handle(EventDto event) {
        String type = event.getType();
        Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();

        switch (type) {
            case "SessionCreated" -> upsertSession(event, payload);
            case "SessionResumed" -> updateSession(event.getSessionId(), Map.of("status", "RUNNING"));
            case "SessionStatusChanged" -> {
                String newStatus = stringVal(payload.get("new_status"));
                if (VALID_SESSION_STATUSES.contains(newStatus)) {
                    updateSession(event.getSessionId(), Map.of("status", newStatus));
                }
            }
            case "SessionFinished" -> {
                String finalStatus = stringVal(payload.get("final_status"));
                if (finalStatus == null || finalStatus.isBlank()) {
                    finalStatus = "SUCCEEDED";
                }
                if (VALID_SESSION_STATUSES.contains(finalStatus)) {
                    updateSession(event.getSessionId(), Map.of("status", finalStatus));
                }
            }
            case "SessionPausedHitl" -> {
                String status = "wait".equals(stringVal(payload.get("form"))) ? "PAUSED" : "PAUSED_HITL";
                updateSession(event.getSessionId(), Map.of("status", status));
            }
            case "HitlApproved", "HitlModified", "HitlAnswered", "HitlRejected", "HitlCancelled" ->
                    updateSessionIfStatus(event.getSessionId(),
                            List.of("PAUSED_HITL", "PAUSED"), "RUNNING");
            case "RunCanceled" ->
                    updateSessionIfStatus(event.getSessionId(),
                            List.of("RUNNING", "INTERRUPTED", "PAUSED", "PAUSED_HITL"), "CANCELED");
            case "RecognizeIntentToolCall" -> {
                String goal = stringVal(payload.get("session_goal"));
                if (goal != null && !goal.isBlank()) {
                    updateSession(event.getSessionId(), Map.of("goal", goal));
                }
                if (event.getTaskId() != null) {
                    Map<String, Object> values = new HashMap<>();
                    if (payload.get("title") != null) {
                        values.put("title", payload.get("title"));
                    }
                    if (payload.get("description") != null) {
                        values.put("description", payload.get("description"));
                    }
                    if (!values.isEmpty()) {
                        updateTask(event.getTaskId(), values);
                    }
                }
            }
            case "FailureThresholdHit" -> { /* no-op */ }
            case "TaskCreated" -> upsertTask(event, payload);
            case "TaskRequeued" -> {
                if (event.getTaskId() != null) {
                    Map<String, Object> values = new HashMap<>();
                    values.put("status", "PENDING");
                    values.put("outputsJson", "null");
                    if (payload.get("user_prompt") != null) {
                        values.put("userPrompt", payload.get("user_prompt"));
                    }
                    updateTask(event.getTaskId(), values);
                }
            }
            default -> {
                if (TASK_STATUS_BY_EVENT.containsKey(type) && event.getTaskId() != null) {
                    updateTask(event.getTaskId(), Map.of("status", TASK_STATUS_BY_EVENT.get(type)));
                    if ("TaskFailed".equals(type)) {
                        Object errorCode = payload.get("error_code");
                        if (!"TASK_FAILED_BY_THRESHOLD".equals(errorCode)) {
                            incrementFailureCounter(event.getSessionId());
                        }
                    } else if ("TaskFinished".equals(type)) {
                        resetFailureCounter(event.getSessionId());
                    }
                } else if ("TaskFinalized".equals(type) && event.getTaskId() != null) {
                    Map<String, Object> values = new HashMap<>();
                    values.put("outputsJson", JsonUtils.toJson(payload.get("outputs")));
                    values.put("error", payload.get("error"));
                    updateTask(event.getTaskId(), values);
                }
            }
        }
    }

    @Transactional
    protected void upsertSession(EventDto event, Map<String, Object> payload) {
        SessionEntity existing = sessionMapper.selectById(event.getSessionId());
        if (existing == null) {
            SessionEntity entity = new SessionEntity();
            entity.setId(event.getSessionId());
            entity.setTenantId(stringVal(payload.get("tenant_id")) != null
                    ? stringVal(payload.get("tenant_id")) : event.getTenantId());
            String userId = stringVal(payload.get("user_id"));
            if (userId == null || userId.isBlank()) {
                userId = stringVal(payload.get("username"));
            }
            entity.setUserId(userId);
            entity.setUserPrompt(stringVal(payload.get("user_prompt")) != null
                    ? stringVal(payload.get("user_prompt")) : "");
            entity.setStatus("RUNNING");
            entity.setGoal("");
            entity.setRootAgentId(stringVal(payload.get("root_agent_id")));
            entity.setLlmProvider(stringVal(payload.get("llm_account")));
            entity.setLlmModel(stringVal(payload.get("llm_model")));
            Object budget = payload.get("token_budget");
            entity.setTokenBudget(budget instanceof Number n ? n.longValue() : 200_000L);
            Map<String, Object> config = new HashMap<>();
            config.put("template_id", stringVal(payload.get("template_id")) != null
                    ? stringVal(payload.get("template_id")) : "");
            entity.setConfigJson(JsonUtils.toJson(config));
            entity.setFailureCounter(0);
            entity.setLastUploadIndex(0);
            entity.setCreatedAt(event.getTimestamp() != null ? event.getTimestamp() : OffsetDateTime.now());
            sessionMapper.insert(entity);
        } else if (existing.getRootAgentId() == null) {
            SessionEntity update = new SessionEntity();
            update.setId(event.getSessionId());
            update.setRootAgentId(stringVal(payload.get("root_agent_id")));
            sessionMapper.updateById(update);
        }
    }

    @Transactional
    protected void updateSession(String sessionId, Map<String, Object> values) {
        SessionEntity update = new SessionEntity();
        update.setId(sessionId);
        applySessionValues(update, values);
        sessionMapper.updateById(update);
    }

    @Transactional
    protected void updateSessionIfStatus(String sessionId, List<String> fromStatuses, String toStatus) {
        SessionEntity row = sessionMapper.selectById(sessionId);
        if (row != null && fromStatuses.contains(row.getStatus())) {
            SessionEntity update = new SessionEntity();
            update.setId(sessionId);
            update.setStatus(toStatus);
            sessionMapper.updateById(update);
        }
    }

    @Transactional
    protected void incrementFailureCounter(String sessionId) {
        SessionEntity row = sessionMapper.selectById(sessionId);
        if (row != null) {
            SessionEntity update = new SessionEntity();
            update.setId(sessionId);
            update.setFailureCounter((row.getFailureCounter() != null ? row.getFailureCounter() : 0) + 1);
            sessionMapper.updateById(update);
        }
    }

    @Transactional
    protected void resetFailureCounter(String sessionId) {
        SessionEntity row = sessionMapper.selectById(sessionId);
        if (row != null && row.getFailureCounter() != null && row.getFailureCounter() > 0) {
            SessionEntity update = new SessionEntity();
            update.setId(sessionId);
            update.setFailureCounter(0);
            sessionMapper.updateById(update);
        }
    }

    @Transactional
    protected void upsertTask(EventDto event, Map<String, Object> payload) {
        Object taskObj = payload.get("task");
        if (!(taskObj instanceof Map<?, ?> taskData)) {
            return;
        }
        String taskId = stringVal(taskData.get("id"));
        if (taskId == null || taskId.isBlank()) {
            taskId = event.getTaskId();
        }
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        TaskEntity existing = taskMapper.selectById(taskId);
        if (existing != null) {
            return;
        }
        Object settings = taskData.get("settings");
        boolean isDaemon = false;
        if (settings instanceof Map<?, ?> settingsMap) {
            isDaemon = "MetadataFillerTaskSettings".equals(stringVal(settingsMap.get("_type")));
        }
        TaskEntity entity = new TaskEntity();
        entity.setId(taskId);
        entity.setSessionId(event.getSessionId());
        entity.setStatus(stringVal(taskData.get("status")) != null ? stringVal(taskData.get("status")) : "ACTIVE");
        entity.setTitle(stringVal(taskData.get("title")) != null ? stringVal(taskData.get("title")) : "");
        entity.setDescription(stringVal(taskData.get("description")) != null
                ? stringVal(taskData.get("description")) : "");
        entity.setUserPrompt(stringVal(taskData.get("user_prompt")));
        String assigned = stringVal(taskData.get("assigned_agent_id"));
        entity.setAssignedAgentId(assigned != null ? assigned : event.getAgentId());
        String creator = stringVal(taskData.get("creator_agent_id"));
        entity.setCreatorAgentId(creator != null ? creator : event.getAgentId());
        entity.setIsDaemon(isDaemon);
        entity.setOutputsJson("null");
        entity.setCreatedAt(event.getTimestamp() != null ? event.getTimestamp() : OffsetDateTime.now());
        taskMapper.insert(entity);
    }

    @Transactional
    protected void updateTask(String taskId, Map<String, Object> values) {
        LambdaUpdateWrapper<TaskEntity> wrapper = new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, taskId);
        if (values.containsKey("status")) {
            wrapper.set(TaskEntity::getStatus, values.get("status"));
        }
        if (values.containsKey("title")) {
            wrapper.set(TaskEntity::getTitle, values.get("title"));
        }
        if (values.containsKey("description")) {
            wrapper.set(TaskEntity::getDescription, values.get("description"));
        }
        if (values.containsKey("userPrompt")) {
            wrapper.set(TaskEntity::getUserPrompt, values.get("userPrompt"));
        }
        if (values.containsKey("outputsJson")) {
            wrapper.set(TaskEntity::getOutputsJson, values.get("outputsJson"));
        }
        if (values.containsKey("error")) {
            wrapper.set(TaskEntity::getError, values.get("error"));
        }
        taskMapper.update(null, wrapper);
    }

    private static void applySessionValues(SessionEntity update, Map<String, Object> values) {
        if (values.containsKey("status")) {
            update.setStatus(stringVal(values.get("status")));
        }
        if (values.containsKey("goal")) {
            update.setGoal(stringVal(values.get("goal")));
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }
}
