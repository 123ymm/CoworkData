package com.huawei.coworkdata.controller;

import com.huawei.coworkdata.dto.AppendSseEventRequest;
import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.dto.SaveWorkspaceRequest;
import com.huawei.coworkdata.dto.SessionIncrementalUploadRequest;
import com.huawei.coworkdata.dto.SessionRecordDto;
import com.huawei.coworkdata.dto.SessionUploadResultDto;
import com.huawei.coworkdata.dto.UpdateLastUploadIndexRequest;
import com.huawei.coworkdata.dto.UpdateSessionStatusRequest;
import com.huawei.coworkdata.dto.UploadWatermarkDto;
import com.huawei.coworkdata.service.PostgresStateStoreService;
import com.huawei.coworkdata.service.SessionSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 会话 API：地端同步 + 投影读写（统一前缀 {@code /api/sessions}）。
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionSyncService sessionSyncService;
    private final PostgresStateStoreService stateStore;

    // ── 地端同步（优先） ──────────────────────────────────────────────────────

    /** 按用户列出会话（须带 userId） */
    @GetMapping
    public List<SessionRecordDto> listByUser(@RequestParam String userId) {
        return sessionSyncService.listByUserId(userId);
    }

    /** 查询上传水位 */
    @GetMapping("/{sessionId}/upload-watermark")
    public UploadWatermarkDto getUploadWatermark(@PathVariable String sessionId) {
        return sessionSyncService.getUploadWatermark(sessionId);
    }

    /** 增量上传事件 */
    @PostMapping("/{sessionId}/upload")
    public SessionUploadResultDto incrementalUpload(
            @PathVariable String sessionId,
            @RequestBody SessionIncrementalUploadRequest request) {
        return sessionSyncService.incrementalUpload(sessionId, request);
    }

    /** 会话回放：全部事件 */
    @GetMapping("/{sessionId}/events")
    public List<EventDto> replay(@PathVariable String sessionId) {
        return sessionSyncService.replay(sessionId);
    }

    /** 软删会话 */
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(@PathVariable String sessionId) {
        sessionSyncService.softDelete(sessionId);
    }

    // ── 投影读写 ────────────────────────────────────────────────────────────

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

    @PutMapping("/{sessionId}/last-upload-index")
    public void updateLastUploadIndex(
            @PathVariable String sessionId,
            @RequestBody UpdateLastUploadIndexRequest request) {
        stateStore.updateLastUploadIndex(sessionId, request.getLastUploadIndex());
    }
}
