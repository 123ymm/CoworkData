package com.huawei.coworkdata.service.impl;

import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.dto.RunSnapshotDto;
import com.huawei.coworkdata.service.PostgresEventStoreService;
import com.huawei.coworkdata.service.SnapshotWriterService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SnapshotWriterServiceImpl implements SnapshotWriterService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotWriterServiceImpl.class);

    public static final int DEFAULT_SNAPSHOT_EVERY_N_EVENTS = 50;

    private static final Set<String> TRANSIENT_EVENT_TYPES = Set.of(
            "text_delta", "reasoning_delta", "TextDelta", "ReasoningDelta"
    );

    private final PostgresEventStoreService eventStore;
    private final int everyN = DEFAULT_SNAPSHOT_EVERY_N_EVENTS;
    private final Map<String, Integer> sinceSnapshot = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    @Override
    public void onEvent(EventDto event) {
        String sessionId = event.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (event.getType() != null && TRANSIENT_EVENT_TYPES.contains(event.getType())) {
            return;
        }
        try {
            if ("SessionFinished".equals(event.getType())) {
                write(sessionId, event, "session_finished");
                sinceSnapshot.remove(sessionId);
                return;
            }
            int n = sinceSnapshot.getOrDefault(sessionId, 0) + 1;
            if ("RunFinished".equals(event.getType()) && n >= everyN) {
                write(sessionId, event, "periodic");
                sinceSnapshot.put(sessionId, 0);
            } else {
                sinceSnapshot.put(sessionId, n);
            }
        } catch (Exception e) {
            log.error("SnapshotWriter: failed for session {}", sessionId, e);
        }
    }

    @Override
    public void close() {
        closed = true;
        sinceSnapshot.clear();
    }

    private void write(String sessionId, EventDto event, String reason) {
        RunSnapshotDto snapshot = new RunSnapshotDto();
        snapshot.setId("snp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        snapshot.setRunId(event.getRunId() != null ? event.getRunId() : "");
        snapshot.setSessionId(sessionId);
        snapshot.setLastEventId(event.getId());
        snapshot.setLastEventSequence(event.getSequence());
        Map<String, Object> blob = new HashMap<>();
        blob.put("session_id", sessionId);
        blob.put("last_event_id", event.getId());
        snapshot.setStateBlob(blob);
        snapshot.setSnapshotReason(reason);
        snapshot.setSnapshotAt(OffsetDateTime.now());
        eventStore.saveSnapshot(snapshot);
        log.info("SnapshotWriter: snapshot {} written for session {} (reason={}, seq={})",
                snapshot.getId(), sessionId, reason, event.getSequence());
    }
}
