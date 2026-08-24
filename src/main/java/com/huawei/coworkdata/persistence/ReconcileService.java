package com.huawei.coworkdata.persistence;

import com.huawei.coworkdata.dto.EventDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReconcileService {

    private static final Logger log = LoggerFactory.getLogger(ReconcileService.class);

    private final PostgresStateStoreService stateStore;
    private final PostgresEventStoreService eventStore;

    public int reconcileStrandedRunningSessions() {
        Set<String> eventActive = new HashSet<>(eventStore.listActiveSessionIds());
        List<String> projectionRunning = stateStore.listActiveSessionIds();

        int reconciled = 0;
        for (String sessionId : projectionRunning) {
            if (eventActive.contains(sessionId)) {
                continue;
            }
            try {
                List<EventDto> finished = eventStore.readSessionEventsOfTypes(sessionId, List.of("SessionFinished"));
                String finalStatus = "SUCCEEDED";
                if (!finished.isEmpty()) {
                    EventDto last = finished.get(finished.size() - 1);
                    Object fs = last.getPayload() != null ? last.getPayload().get("final_status") : null;
                    if (fs != null && !fs.toString().isBlank()) {
                        finalStatus = fs.toString();
                    }
                }
                stateStore.updateSessionStatus(sessionId, finalStatus);
                reconciled++;
                log.info("Reconcile: stranded session {} RUNNING -> {} (event truth)", sessionId, finalStatus);
            } catch (Exception e) {
                log.error("Reconcile: failed to reconcile stranded session {}", sessionId, e);
            }
        }
        return reconciled;
    }
}
