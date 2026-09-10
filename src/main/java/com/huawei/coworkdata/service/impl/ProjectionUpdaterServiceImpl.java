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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectionUpdaterServiceImpl implements ProjectionUpdaterService {

    private static final Logger log = LoggerFactory.getLogger(ProjectionUpdaterServiceImpl.class);

    private static final Set<String> TRANSIENT_EVENT_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "text_delta", "reasoning_delta", "TextDelta", "ReasoningDelta"
    )));

    private static final Set<String> VALID_SESSION_STATUSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "RUNNING", "SUCCEEDED", "FAILED", "CANCELED", "INTERRUPTED", "PAUSED_HITL", "PAUSED"
    )));

    private static final Map<String, String> TASK_STATUS_BY_EVENT;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("TaskStarted", "ACTIVE");
        m.put("TaskResumed", "ACTIVE");
        m.put("TaskSuspended", "SUSPENDED");
        m.put("TaskRequeued", "PENDING");
        m.put("TaskFinished", "FINISHED");
        m.put("TaskFailed", "FAILED");
        m.put("TaskCanceled", "CANCELED");
        TASK_STATUS_BY_EVENT = Collections.unmodifiableMap(m);
    }

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

    @Override
    public void onEventOrThrow(EventDto event) {
        if (event.getType() != null && TRANSIENT_EVENT_TYPES.contains(event.getType())) {
            return;
        }
        handle(event);
    }

    private void handle(EventDto event) {
        String type = event.getType();
        Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Collections.emptyMap();

        switch (type) {
            case "SessionCreated":
                upsertSession(event, payload);
                break;
            case "SessionResumed":
                updateSession(event.getSessionId(), statusMap("RUNNING"));
                break;
            case "SessionStatusChanged": {
                String newStatus = stringVal(payload.get("new_status"));
                if (VALID_SESSION_STATUSES.contains(newStatus)) {
                    updateSession(event.getSessionId(), statusMap(newStatus));
                }
                break;
            }
            case "SessionFinished": {
                String finalStatus = stringVal(payload.get("final_status"));
                if (finalStatus == null || finalStatus.trim().isEmpty()) {
                    finalStatus = "SUCCEEDED";
                }
                if (VALID_SESSION_STATUSES.contains(finalStatus)) {
                    updateSession(event.getSessionId(), statusMap(finalStatus));
                }
                break;
            }
            case "SessionPausedHitl": {
                String status = "wait".equals(stringVal(payload.get("form"))) ? "PAUSED" : "PAUSED_HITL";
                updateSession(event.getSessionId(), statusMap(status));
                break;
            }
            case "HitlApproved":
            case "HitlModified":
            case "HitlAnswered":
            case "HitlRejected":
            case "HitlCancelled":
                updateSessionIfStatus(event.getSessionId(),
                        Arrays.asList("PAUSED_HITL", "PAUSED"), "RUNNING");
                break;
            case "RunCanceled":
                updateSessionIfStatus(event.getSessionId(),
                        Arrays.asList("RUNNING", "INTERRUPTED", "PAUSED", "PAUSED_HITL"), "CANCELED");
                break;
            case "RecognizeIntentToolCall": {
                String goal = stringVal(payload.get("session_goal"));
                if (goal != null && !goal.trim().isEmpty()) {
                    updateSession(event.getSessionId(), singletonObjMap("goal", goal));
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
                break;
            }
            case "FailureThresholdHit":
                /* no-op */
                break;
            case "TaskCreated":
                upsertTask(event, payload);
                break;
            case "TaskRequeued":
                if (event.getTaskId() != null) {
                    Map<String, Object> values = new HashMap<>();
                    values.put("status", "PENDING");
                    values.put("outputsJson", "null");
                    if (payload.get("user_prompt") != null) {
                        values.put("userPrompt", payload.get("user_prompt"));
                    }
                    updateTask(event.getTaskId(), values);
                }
                break;
            default:
                if (TASK_STATUS_BY_EVENT.containsKey(type) && event.getTaskId() != null) {
                    updateTask(event.getTaskId(), statusMap(TASK_STATUS_BY_EVENT.get(type)));
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
                break;
        }
    }

    private static Map<String, Object> statusMap(String status) {
        return singletonObjMap("status", status);
    }

    private static Map<String, Object> singletonObjMap(String key, Object value) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, value);
        return m;
    }

    @Transactional
    protected void upsertSession(EventDto event, Map<String, Object> payload) {
        SessionEntity existing = sessionMapper.selectById(event.getSessionId());
        if (existing == null) {
            SessionEntity entity = new SessionEntity();
            entity.setId(event.getSessionId());
            String userId = stringVal(payload.get("user_id"));
            if (userId == null || userId.trim().isEmpty()) {
                userId = stringVal(payload.get("username"));
            }
            entity.setUserId(userId);
            String coworkId = stringVal(payload.get("cowork_id"));
            entity.setCoworkId(coworkId);
            String source = stringVal(payload.get("source"));
            entity.setSource(source != null ? source : "local");
            entity.setInstallId(stringVal(payload.get("install_id")));
            String tenant = stringVal(payload.get("tenant_id"));
            if (tenant == null) {
                tenant = event.getTenantId();
            }
            if ((tenant == null || tenant.trim().isEmpty() || "default".equals(tenant))
                    && userId != null && coworkId != null) {
                tenant = userId + ":" + coworkId;
            }
            entity.setTenantId(tenant != null ? tenant : "default");
            entity.setUserPrompt(payloadString(payload, "user_prompt", "userPrompt", ""));
            entity.setStatus("RUNNING");
            entity.setGoal("");
            entity.setRootAgentId(stringVal(payload.get("root_agent_id")));
            entity.setLlmProvider(stringVal(payload.get("llm_account")));
            entity.setLlmModel(stringVal(payload.get("llm_model")));
            Object budget = payload.get("token_budget");
            entity.setTokenBudget(budget instanceof Number ? ((Number) budget).longValue() : 200_000L);
            Map<String, Object> config = new HashMap<>();
            config.put("template_id", stringVal(payload.get("template_id")) != null
                    ? stringVal(payload.get("template_id")) : "");
            entity.setConfigJson(JsonUtils.toJson(config));
            entity.setFailureCounter(0);
            entity.setLastUploadIndex(0);
            entity.setCreatedAt(event.getTimestamp() != null ? event.getTimestamp() : OffsetDateTime.now());
            sessionMapper.insert(entity);
            return;
        }

        // 增量上传会先 ensureSessionForUpload 插空壳（user_prompt/goal=""、llm 空），
        // 再投递 SessionCreated。若这里只补 root_agent_id，投影字段会永远留空。
        SessionEntity update = new SessionEntity();
        update.setId(event.getSessionId());
        boolean dirty = false;
        boolean wasStub = isBlank(existing.getUserPrompt());
        if (isBlank(existing.getRootAgentId())) {
            String root = stringVal(payload.get("root_agent_id"));
            if (!isBlank(root)) {
                update.setRootAgentId(root);
                dirty = true;
            }
        }
        if (isBlank(existing.getUserPrompt())) {
            String prompt = payloadString(payload, "user_prompt", "userPrompt", null);
            if (!isBlank(prompt)) {
                update.setUserPrompt(prompt);
                dirty = true;
            }
        }
        if (isBlank(existing.getLlmProvider())) {
            String provider = stringVal(payload.get("llm_account"));
            if (!isBlank(provider)) {
                update.setLlmProvider(provider);
                dirty = true;
            }
        }
        if (isBlank(existing.getLlmModel())) {
            String model = stringVal(payload.get("llm_model"));
            if (!isBlank(model)) {
                update.setLlmModel(model);
                dirty = true;
            }
        }
        // config.template_id：空壳为 "{}" 或缺 template 时补上
        String templateId = stringVal(payload.get("template_id"));
        if (!isBlank(templateId)) {
            Map<String, Object> parsed = JsonUtils.parseMap(existing.getConfigJson());
            Map<String, Object> config = new HashMap<>();
            if (parsed != null) {
                config.putAll(parsed);
            }
            Object cur = config.get("template_id");
            if (cur == null || String.valueOf(cur).trim().isEmpty()) {
                config.put("template_id", templateId);
                update.setConfigJson(JsonUtils.toJson(config));
                dirty = true;
            }
        }
        Object budget = payload.get("token_budget");
        if (budget instanceof Number
                && (existing.getTokenBudget() == null || existing.getTokenBudget() == 200_000L)) {
            long b = ((Number) budget).longValue();
            if (b != 200_000L) {
                update.setTokenBudget(b);
                dirty = true;
            }
        }
        // ensureSession 空壳的 created_at 是上传时刻；用 SessionCreated 事件时间纠正
        if (event.getTimestamp() != null && (wasStub || dirty)) {
            update.setCreatedAt(event.getTimestamp());
            dirty = true;
        }
        if (dirty) {
            sessionMapper.updateById(update);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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
        if (!(taskObj instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<?, ?> taskData = (Map<?, ?>) taskObj;
        String taskId = stringVal(taskData.get("id"));
        if (taskId == null || taskId.trim().isEmpty()) {
            taskId = event.getTaskId();
        }
        if (taskId == null || taskId.trim().isEmpty()) {
            return;
        }
        TaskEntity existing = taskMapper.selectById(taskId);
        if (existing != null) {
            return;
        }
        Object settings = taskData.get("settings");
        boolean isDaemon = false;
        if (settings instanceof Map) {
            Map<?, ?> settingsMap = (Map<?, ?>) settings;
            isDaemon = "MetadataFillerTaskSettings".equals(stringVal(settingsMap.get("_type")));
        }
        TaskEntity entity = new TaskEntity();
        entity.setId(taskId);
        entity.setSessionId(event.getSessionId());
        entity.setStatus(stringVal(taskData.get("status")) != null ? stringVal(taskData.get("status")) : "ACTIVE");
        entity.setTitle(stringVal(taskData.get("title")) != null ? stringVal(taskData.get("title")) : "");
        entity.setDescription(stringVal(taskData.get("description")) != null
                ? stringVal(taskData.get("description")) : "");
        // tasks.user_prompt 可空；显式缺省保持 null（与地端 TaskModel 一致）
        String taskPrompt = firstString(taskData, "user_prompt", "userPrompt");
        entity.setUserPrompt(taskPrompt);
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

    /** 读 snake_case / camelCase；null 时回落 defaultVal（sessions.user_prompt 用）。 */
    private static String payloadString(
            Map<String, Object> payload, String snake, String camel, String defaultVal) {
        String s = firstString(payload, snake, camel);
        return s != null ? s : defaultVal;
    }

    private static String firstString(Map<?, ?> map, String snake, String camel) {
        if (map == null) {
            return null;
        }
        Object v = map.get(snake);
        if (v == null) {
            v = map.get(camel);
        }
        return stringVal(v);
    }
}
