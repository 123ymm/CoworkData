package com.huawei.coworkdata.service.impl;

import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.dto.SessionIncrementalUploadRequest;
import com.huawei.coworkdata.dto.SessionRecordDto;
import com.huawei.coworkdata.dto.SessionUploadResultDto;
import com.huawei.coworkdata.dto.UploadWatermarkDto;
import com.huawei.coworkdata.service.PostgresEventStoreService;
import com.huawei.coworkdata.service.PostgresStateStoreService;
import com.huawei.coworkdata.service.ProjectionUpdaterService;
import com.huawei.coworkdata.service.SessionSyncService;
import com.huawei.coworkdata.service.SnapshotWriterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionSyncServiceImpl implements SessionSyncService {

    private final PostgresStateStoreService stateStore;
    private final PostgresEventStoreService eventStore;
    private final ProjectionUpdaterService projectionUpdater;
    private final SnapshotWriterService snapshotWriter;

    @Override
    public UploadWatermarkDto getUploadWatermark(String sessionId) {
        SessionRecordDto session = stateStore.getSessionRecord(sessionId);
        UploadWatermarkDto dto = new UploadWatermarkDto();
        dto.setSessionId(sessionId);
        if (session == null) {
            dto.setLastUploadIndex(0);
            return dto;
        }
        dto.setLastUploadIndex(session.getLastUploadIndex());
        return dto;
    }

    @Override
    @Transactional
    public SessionUploadResultDto incrementalUpload(String sessionId, SessionIncrementalUploadRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        List<EventDto> events = request.getEvents() != null ? request.getEvents() : List.of();

        stateStore.ensureSessionForUpload(sessionId, request.getUserId());
        int previous = stateStore.getLastUploadIndex(sessionId);

        if (request.getUploadIndex() != null && request.getUploadIndex() < previous) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "uploadIndex " + request.getUploadIndex() + " is behind current watermark " + previous);
        }

        int accepted = 0;
        int skipped = 0;
        for (EventDto event : events) {
            if (event.getSessionId() == null || event.getSessionId().isBlank()) {
                event.setSessionId(sessionId);
            } else if (!sessionId.equals(event.getSessionId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "event.sessionId mismatch: " + event.getSessionId());
            }
            // 投影/快照走流水线；事件写入用幂等 append，避免重复上传冲突
            boolean inserted = eventStore.appendIfAbsent(event);
            if (inserted) {
                accepted++;
                projectionUpdater.onEvent(event);
                snapshotWriter.onEvent(event);
            } else {
                skipped++;
            }
        }

        int newIndex;
        if (request.getUploadIndex() != null) {
            newIndex = request.getUploadIndex();
        } else {
            newIndex = previous + accepted;
        }
        if (newIndex < previous) {
            newIndex = previous;
        }
        stateStore.updateLastUploadIndex(sessionId, newIndex);
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            stateStore.updateUserId(sessionId, request.getUserId());
        }

        SessionUploadResultDto result = new SessionUploadResultDto();
        result.setSessionId(sessionId);
        result.setPreviousUploadIndex(previous);
        result.setLastUploadIndex(newIndex);
        result.setAcceptedEventCount(accepted);
        result.setSkippedEventCount(skipped);
        return result;
    }

    @Override
    @Transactional
    public void softDelete(String sessionId) {
        SessionRecordDto session = stateStore.getSessionRecord(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        stateStore.deleteSession(sessionId);
    }

    @Override
    public List<EventDto> replay(String sessionId) {
        SessionRecordDto session = stateStore.getSessionRecord(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return eventStore.readBySession(sessionId);
    }

    @Override
    public List<SessionRecordDto> listByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        return stateStore.listSessionsByUserId(userId);
    }
}
