package com.huawei.coworkdata.controller;

import com.huawei.coworkdata.dto.AppendSseEventRequest;
import com.huawei.coworkdata.dto.SaveWorkspaceRequest;
import com.huawei.coworkdata.dto.SessionRecordDto;
import com.huawei.coworkdata.dto.UpdateSessionStatusRequest;
import com.huawei.coworkdata.persistence.PostgresStateStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 对应 Python {@code PostgresStateStore} 对外方法。
 */
@RestController
@RequestMapping("/api/persistence/sessions")
@RequiredArgsConstructor
public class SessionPersistenceController {

    private final PostgresStateStoreService stateStore;

    @GetMapping
    public List<SessionRecordDto> listSessions() {
        return stateStore.listSessions();
    }

    @GetMapping("/active")
    public List<String> listActiveSessionIds() {
        return stateStore.listActiveSessionIds();
    }

    @GetMapping("/{sessionId}")
    public SessionRecordDto getSessionRecord(@PathVariable String sessionId) {
        SessionRecordDto record = stateStore.getSessionRecord(sessionId);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return record;
    }

    @GetMapping("/{sessionId}/tasks")
    public List<Map<String, Object>> loadTasks(@PathVariable String sessionId) {
        return stateStore.loadTasks(sessionId);
    }

    @PostMapping("/{sessionId}/sse-events")
    @ResponseStatus(HttpStatus.CREATED)
    public void appendSseEvent(@PathVariable String sessionId, @RequestBody AppendSseEventRequest request) {
        stateStore.appendSseEvent(sessionId, request.getEventJson());
    }

    @GetMapping("/{sessionId}/sse-events")
    public List<String> loadSseEvents(@PathVariable String sessionId) {
        return stateStore.loadSseEvents(sessionId);
    }

    @PutMapping("/{sessionId}/workspace")
    public void saveWorkspace(@PathVariable String sessionId, @RequestBody SaveWorkspaceRequest request) {
        stateStore.saveWorkspace(sessionId, request.getWorkspace());
    }

    @GetMapping("/{sessionId}/workspace")
    public Map<String, String> getWorkspace(@PathVariable String sessionId) {
        String workspace = stateStore.getWorkspace(sessionId);
        return Map.of("workspace", workspace != null ? workspace : "");
    }

    @GetMapping("/{sessionId}/status")
    public Map<String, String> getSessionStatus(@PathVariable String sessionId) {
        String status = stateStore.getSessionStatus(sessionId);
        return Map.of("status", status != null ? status : "");
    }

    @PutMapping("/{sessionId}/status")
    public void updateSessionStatus(
            @PathVariable String sessionId,
            @RequestBody UpdateSessionStatusRequest request) {
        stateStore.updateSessionStatus(sessionId, request.getStatus());
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable String sessionId) {
        stateStore.deleteSession(sessionId);
    }
}
