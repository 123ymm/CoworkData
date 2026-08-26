package com.huawei.coworkdata.controller;

import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.service.EventPersisterService;
import com.huawei.coworkdata.service.ProjectionUpdaterService;
import com.huawei.coworkdata.service.ReconcileService;
import com.huawei.coworkdata.service.SkillReporterService;
import com.huawei.coworkdata.service.SnapshotWriterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 对应 persistence 目录下 EventBus 订阅者与 reconcile 的对外方法。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PersistencePipelineController {

    private final EventPersisterService eventPersister;
    private final ProjectionUpdaterService projectionUpdater;
    private final SnapshotWriterService snapshotWriter;
    private final SkillReporterService skillReporter;
    private final ReconcileService reconcileService;

    @PostMapping("/event-persister/on-event")
    public void eventPersisterOnEvent(@RequestBody EventDto event) {
        eventPersister.onEvent(event);
    }

    @PostMapping("/projection-updater/on-event")
    public void projectionUpdaterOnEvent(@RequestBody EventDto event) {
        projectionUpdater.onEvent(event);
    }

    @PostMapping("/snapshot-writer/on-event")
    public void snapshotWriterOnEvent(@RequestBody EventDto event) {
        snapshotWriter.onEvent(event);
    }

    @PostMapping("/snapshot-writer/close")
    public void snapshotWriterClose() {
        snapshotWriter.close();
    }

    @PostMapping("/skill-reporter/on-event")
    public void skillReporterOnEvent(@RequestBody EventDto event) {
        skillReporter.onEvent(event);
    }

    @PostMapping("/skill-reporter/close")
    public void skillReporterClose() {
        skillReporter.close();
    }

    @PostMapping("/reconcile/stranded-running-sessions")
    public Map<String, Integer> reconcileStrandedRunningSessions() {
        return Map.of("reconciled", reconcileService.reconcileStrandedRunningSessions());
    }
}
