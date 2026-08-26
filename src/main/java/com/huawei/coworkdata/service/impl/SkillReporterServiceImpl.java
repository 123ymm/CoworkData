package com.huawei.coworkdata.service.impl;

import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.dto.SessionEntryDto;
import com.huawei.coworkdata.dto.SessionsStoreRequest;
import com.huawei.coworkdata.service.SkillReporterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SkillReporterServiceImpl implements SkillReporterService {

    private static final Set<String> TASK_TERMINAL_EVENTS = Set.of(
            "TaskFinished", "TaskFailed", "TaskCanceled", "TaskFinalized"
    );

    private final Map<String, SessionEntryDto> sessionsStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> taskStartTime = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    @Override
    public void setSessionsStore(SessionsStoreRequest request) {
        sessionsStore.clear();
        if (request != null && request.getEntries() != null) {
            for (SessionEntryDto entry : request.getEntries()) {
                if (entry.getSessionId() != null) {
                    sessionsStore.put(entry.getSessionId(), entry);
                }
            }
        }
        log.info("SkillReporter: set_sessions_store called, store size={}", sessionsStore.size());
    }

    @Override
    public void onEvent(EventDto event) {
        if (closed) {
            return;
        }
        String type = event.getType();
        if ("TaskCreated".equals(type)) {
            handleTaskCreated(event);
            return;
        }
        if ("TaskStarted".equals(type)) {
            handleTaskStarted(event);
            return;
        }
        if ("CapabilityFinished".equals(type)) {
            handleCapabilityFinished(event);
            return;
        }
        if (type != null && TASK_TERMINAL_EVENTS.contains(type)) {
            cleanupTerminalTask(event);
            return;
        }
        if ("SessionFinished".equals(type)) {
            cleanupFinishedSession(event.getSessionId());
        }
    }

    @Override
    public void close() {
        closed = true;
        taskStartTime.clear();
    }

    private void handleTaskCreated(EventDto event) {
        Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
        Object taskObj = payload.get("task");
        if (!(taskObj instanceof Map<?, ?> taskData)) {
            return;
        }
        String taskId = stringVal(taskData.get("id"));
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        Object settings = taskData.get("settings");
        String skillName = null;
        if (settings instanceof Map<?, ?> settingsMap) {
            skillName = stringVal(settingsMap.get("skill_name"));
        }
        if (skillName == null || skillName.isBlank()) {
            log.info("SkillReporter TASK_CREATED: skill_name is empty, skip");
            return;
        }
        Map<String, Object> info = new HashMap<>();
        info.put("skill_name", skillName);
        info.put("start_time", System.nanoTime());
        info.put("session_id", event.getSessionId());
        taskStartTime.put(taskId, info);
    }

    private void handleTaskStarted(EventDto event) {
        String taskId = event.getTaskId();
        if (taskId == null) {
            return;
        }
        Map<String, Object> taskInfo = taskStartTime.get(taskId);
        if (taskInfo == null) {
            return;
        }
        SessionEntryDto sessionEntry = sessionsStore.get(event.getSessionId());
        if (sessionEntry != null && sessionEntry.getUserInfo() != null) {
            Object username = sessionEntry.getUserInfo().get("username");
            taskInfo.put("user_id", username != null ? username.toString() : "");
        }
    }

    private void handleCapabilityFinished(EventDto event) {
        String taskId = event.getTaskId();
        if (taskId == null) {
            return;
        }
        Map<String, Object> taskInfo = taskStartTime.remove(taskId);
        if (taskInfo == null) {
            return;
        }
        long startNanos = ((Number) taskInfo.getOrDefault("start_time", 0L)).longValue();
        double duration = Math.max(0.0, (System.nanoTime() - startNanos) / 1_000_000_000.0);
        String skillName = stringVal(taskInfo.get("skill_name"));
        log.info("SkillReporter CAPABILITY_FINISHED: skill={}, duration={}s (report delegated to host)",
                skillName, duration);
    }

    private void cleanupTerminalTask(EventDto event) {
        String taskId = event.getTaskId();
        if (taskId != null) {
            taskStartTime.remove(taskId);
        }
    }

    private void cleanupFinishedSession(String sessionId) {
        taskStartTime.entrySet().removeIf(e ->
                stringVal(e.getValue().get("session_id")).equals(sessionId));
    }

    private static String stringVal(Object value) {
        return value == null ? "" : value.toString();
    }
}
