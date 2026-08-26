package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.dto.SessionIncrementalUploadRequest;
import com.huawei.coworkdata.dto.SessionRecordDto;
import com.huawei.coworkdata.dto.SessionUploadResultDto;
import com.huawei.coworkdata.dto.UploadWatermarkDto;

import java.util.List;

/**
 * 地端 ↔ 云端会话同步业务接口。
 */
public interface SessionSyncService {

    UploadWatermarkDto getUploadWatermark(String sessionId);

    SessionUploadResultDto incrementalUpload(String sessionId, SessionIncrementalUploadRequest request);

    void softDelete(String sessionId);

    List<EventDto> replay(String sessionId);

    List<SessionRecordDto> listByUserId(String userId);
}
