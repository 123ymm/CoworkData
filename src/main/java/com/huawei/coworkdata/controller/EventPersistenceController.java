package com.huawei.coworkdata.controller;

import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.dto.RunSnapshotDto;
import com.huawei.coworkdata.service.EventPersisterService;
import com.huawei.coworkdata.service.PostgresEventStoreService;
import com.huawei.coworkdata.service.ProjectionUpdaterService;
import com.huawei.coworkdata.service.SnapshotWriterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 对应 Python {@code PostgresEventStore} 对外方法，以及事件落库流水线。
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventPersistenceController {

    private final PostgresEventStoreService eventStore;
    private final EventPersisterService eventPersister;
    private final ProjectionUpdaterService projectionUpdater;
    private final SnapshotWriterService snapshotWriter;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void append(@RequestBody EventDto event) {
        eventStore.append(event);
    }

    @PostMapping("/pipeline")
    @ResponseStatus(HttpStatus.CREATED)
    public void appendWithPipeline(@RequestBody EventDto event) {
        eventPersister.onEvent(event);
        projectionUpdater.onEvent(event);
        snapshotWriter.onEvent(event);
    }

    @GetMapping("/sessions/{sessionId}/types")
    public List<EventDto> readSessionEventsOfTypes(
            @PathVariable String sessionId,
            @RequestParam List<String> types) {
        return eventStore.readSessionEventsOfTypes(sessionId, types);
    }

    @GetMapping("/active-session-ids")
    public List<String> listActiveSessionIds() {
        return eventStore.listActiveSessionIds();
    }

    @GetMapping("/last-activity-times")
    public Map<String, String> lastActivityTimes(
            @RequestParam(required = false) List<String> excludeTypes) {
        return eventStore.lastActivityTimes(excludeTypes);
    }

    @GetMapping("/sessions/{sessionId}/after/{afterEventId}")
    public List<EventDto> readAfter(
            @PathVariable String sessionId,
            @PathVariable String afterEventId) {
        return eventStore.readAfter(sessionId, afterEventId);
    }

    @PostMapping("/sessions/{sessionId}/snapshots")
    @ResponseStatus(HttpStatus.CREATED)
    public void saveSnapshot(@PathVariable String sessionId, @RequestBody RunSnapshotDto snapshot) {
        snapshot.setSessionId(sessionId);
        eventStore.saveSnapshot(snapshot);
    }

    @GetMapping("/sessions/{sessionId}/snapshots/latest")
    public RunSnapshotDto loadLatestSnapshot(@PathVariable String sessionId) {
        return eventStore.loadLatestSnapshot(sessionId);
    }
}
