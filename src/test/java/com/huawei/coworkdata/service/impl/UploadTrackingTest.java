package com.huawei.coworkdata.service.impl;

import com.huawei.coworkdata.dto.*;
import com.huawei.coworkdata.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:tracking;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "logging.level.root=WARN"
})
class UploadTrackingTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired SessionSyncService sync;
    @Autowired PostgresStateStoreService state;
    @Autowired PostgresEventStoreService events;
    @Autowired MemoryService memory;
    @SpyBean SessionUploadDailyService dailyProxy;
    SessionUploadDailyService daily;
    String sid;
    @BeforeEach void setup() {
        daily = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(dailyProxy);
        sid = UUID.randomUUID().toString();
        // H2 cannot run PostgreSQL ON CONFLICT DO UPDATE. Test service decisions and
        // transaction participation here; production UPSERT has a separate PostgreSQL test.
        doAnswer(call -> {
            jdbc.update("MERGE INTO session_upload_daily KEY(session_id,upload_date) VALUES(?,CURRENT_DATE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    call.getArgument(0, String.class));
            return null;
        }).when(daily).record(anyString());
        jdbc.update("INSERT INTO sessions(id) VALUES(?)", sid);
    }
    private void clearActivity() {
        jdbc.update("DELETE FROM session_upload_daily WHERE session_id=?", sid);
        clearInvocations(daily);
    }
    @Test void emptyRetryAndWatermarkOnlyDoNotCountButProjectionChangeDoes() {
        SessionIncrementalUploadRequest request = new SessionIncrementalUploadRequest();
        request.setUploadIndex(1);
        sync.incrementalUpload(sid, request);
        verify(daily, never()).record(anyString());
        request.setTitle("new title");
        sync.incrementalUpload(sid, request);
        verify(daily).record(sid);
        clearActivity();
        sync.incrementalUpload(sid, request);
        verify(daily, never()).record(anyString());
    }
    @Test void firstEmptyEnsureIsStillANewSession() {
        jdbc.update("DELETE FROM sessions WHERE id=?", sid);
        sync.incrementalUpload(sid, new SessionIncrementalUploadRequest());
        verify(daily).record(sid);
    }
    @Test void taskRetryAndIgnoredNullsDoNotCount() {
        Map<String,Object> task = new HashMap<>();
        task.put("id", UUID.randomUUID().toString());
        task.put("title", "title");
        state.upsertTasks(sid, Collections.singletonList(task));
        verify(daily).record(sid);
        clearActivity();
        state.upsertTasks(sid, Collections.singletonList(task));
        verify(daily, never()).record(anyString());
        task.put("title", "changed");
        state.upsertTasks(sid, Collections.singletonList(task));
        verify(daily).record(sid);
    }
    @Test void workspaceAndStatusRequireActualChange() {
        state.updateSessionStatus(sid, "RUNNING");
        verify(daily, never()).record(anyString());
        state.saveWorkspace(sid, "workspace");
        verify(daily).record(sid);
        clearActivity();
        state.saveWorkspace(sid, "workspace");
        verify(daily, never()).record(anyString());
        state.updateSessionStatus(sid, "DONE");
        verify(daily).record(sid);
    }
    @Test void memoryAndSubscriptionTrackChangesAndSkipDuplicates() {
        MemoryIngestDto dto = new MemoryIngestDto();
        dto.setId(UUID.randomUUID().toString()); dto.setSessionId(sid);
        dto.setLayer("task"); dto.setType("MESSAGE"); dto.setSeqNo(1); dto.setTopicSeqNo(0);
        memory.ingest(dto);
        verify(daily).record(sid);
        clearActivity();
        memory.ingest(dto);
        verify(daily, never()).record(anyString());
        memory.subscribe(sid, "", "topic", "intent", null, 0, null);
        verify(daily).record(sid);
        clearActivity();
        memory.subscribe(sid, "", "topic", "intent", null, 0, null);
        verify(daily, never()).record(anyString());
        memory.subscribe(sid, "", "topic", "intent", null, 1, null);
        verify(daily).record(sid);
    }
    @Test void snapshotRetryDoesNotCountAndSseDoes() {
        RunSnapshotDto dto = new RunSnapshotDto();
        dto.setId(UUID.randomUUID().toString()); dto.setSessionId(sid);
        dto.setLastEventId(""); dto.setStateBlob(Collections.emptyMap());
        events.saveSnapshot(dto);
        verify(daily).record(sid);
        clearActivity();
        events.saveSnapshot(dto);
        verify(daily, never()).record(anyString());
        state.appendSseEvent(sid, "{}");
        verify(daily).record(sid);
    }
    @Test void eventBatchCreatesOneDailyRowAndRetryDoesNotCount() {
        EventDto first = new EventDto();
        first.setId(UUID.randomUUID().toString()); first.setSessionId(sid); first.setType("TestEvent");
        EventDto second = new EventDto();
        second.setId(UUID.randomUUID().toString()); second.setSessionId(sid); second.setType("TestEvent");
        SessionIncrementalUploadRequest request = new SessionIncrementalUploadRequest();
        request.setEvents(Arrays.asList(first, second));
        assertEquals(2, sync.incrementalUpload(sid, request).getAcceptedEventCount());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_daily WHERE session_id=?", Integer.class, sid));
        clearInvocations(daily);
        assertEquals(0, sync.incrementalUpload(sid, request).getAcceptedEventCount());
        verify(daily, never()).record(anyString());
    }
    @Test void dateEndpointRejectsInvalidInputBeforeStreaming() throws Exception {
        String path = "/api/db/export-by-upload-date";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .param("startDate", "2026-10-20").param("endDate", "2026-10-19"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .param("startDate", "not-a-date").param("endDate", "2026-10-19"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
    @Test void summaryFailureRollsBackBusinessWrite() {
        doThrow(new IllegalStateException("summary failure")).when(daily).record(sid);
        assertThrows(IllegalStateException.class, () -> state.appendSseEvent(sid, "{}"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM session_sse_events WHERE session_id=?", Integer.class, sid));
    }
    @Test void laterUploadFailureRollsBackBothEventAndSummary() {
        EventDto first = new EventDto();
        first.setId(UUID.randomUUID().toString()); first.setSessionId(sid); first.setType("TestEvent");
        EventDto invalid = new EventDto(); invalid.setSessionId("another-session");
        SessionIncrementalUploadRequest request = new SessionIncrementalUploadRequest();
        request.setEvents(Arrays.asList(first, invalid));
        assertThrows(RuntimeException.class, () -> sync.incrementalUpload(sid, request));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE session_id=?", Integer.class, sid));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_daily WHERE session_id=?", Integer.class, sid));
    }
}
