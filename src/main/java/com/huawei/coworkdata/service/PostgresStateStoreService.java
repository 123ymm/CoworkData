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

    /** 清空会话 SSE 历史（地端从水位 0 全量回填前调用，避免重复） */
    void clearSseEvents(String sessionId);

    List<String> loadSseEvents(String sessionId);

    /** 地上游 tasks 行 upsert（按 id；对齐本地 SQLite tasks） */
    void upsertTasks(String sessionId, List<Map<String, Object>> tasks);

    void saveWorkspace(String sessionId, String workspace);

    String getWorkspace(String sessionId);

    String getSessionStatus(String sessionId);

    void updateSessionStatus(String sessionId, String status);

    void updateLastUploadIndex(String sessionId, int lastUploadIndex);

    void updateUserId(String sessionId, String userId);

    /** 若不存在则创建一条最小会话投影（用于地端增量上传） */
    void ensureSessionForUpload(String sessionId, String userId, String surrogateId,
                                String tenantId, String source, String installId, String coworkId);

    /** 上传后补全会话身份字段（非空才覆盖） */
    void updateSessionIdentity(String sessionId, String userId, String surrogateId, String tenantId,
                               String source, String installId, String coworkId);

    /** 用上传请求里的投影字段对齐云端 sessions（地上游非空覆盖） */
    void backfillSessionProjection(String sessionId, String userPrompt, String goal,
                                   String title, String status, String rootAgentId,
                                   String llmProvider, String llmModel,
                                   Long tokenBudget, Integer failureCounter,
                                   String configJson, String workspace,
                                   java.time.OffsetDateTime createdAt);

    void deleteSession(String sessionId);
}
