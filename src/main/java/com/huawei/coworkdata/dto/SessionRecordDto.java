package com.huawei.coworkdata.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class SessionRecordDto {

    private String id;
    private String tenantId;
    /** substrate JWT sub（surrogate_id） */
    private String userId;
    private String coworkId;
    /** local | cloud */
    private String source;
    /** Electron 安装 UUID */
    private String installId;
    private String userPrompt;
    private String status;
    /** 用户手动标题；与 goal 分离 */
    private String title;
    private String goal;
    private String rootAgentId;
    private String llmProvider;
    private String llmModel;
    private long tokenBudget;
    private int failureCounter;
    private Map<String, Object> config;
    private String workspace;
    /** 地端 cowork 上次上传到的进度索引 */
    private int lastUploadIndex;
    /** 软删时间；null 表示未删除 */
    private OffsetDateTime deleteAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
