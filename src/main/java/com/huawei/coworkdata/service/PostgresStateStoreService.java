package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.SessionRecordDto;

import java.util.List;
import java.util.Map;

public interface PostgresStateStoreService {

    List<SessionRecordDto> listSessions();

    List<SessionRecordDto> listSessionsByUserId(String userId);

    SessionRecordDto getSessionRecord(String sessionId);

    int getLastUploadIndex(String sessionId);

    List<String> listActiveSessionIds();

    List<Map<String, Object>> loadTasks(String sessionId);

    void appendSseEvent(String sessionId, String eventJson);

    List<String> loadSseEvents(String sessionId);

    void saveWorkspace(String sessionId, String workspace);

    String getWorkspace(String sessionId);

    String getSessionStatus(String sessionId);

    void updateSessionStatus(String sessionId, String status);

    void updateLastUploadIndex(String sessionId, int lastUploadIndex);

    void updateUserId(String sessionId, String userId);

    /** 若不存在则创建一条最小会话投影（用于地端增量上传） */
    void ensureSessionForUpload(String sessionId, String userId);

    void deleteSession(String sessionId);
}
