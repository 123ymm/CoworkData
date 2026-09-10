package com.huawei.coworkdata.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 地端 cowork 增量上传会话事件。
 * <p>
 * {@code uploadIndex} 为本次批次结束后的新水位（通常为事件序号或本地游标）。
 * 服务端会拒绝 {@code uploadIndex <= 当前水位} 的回退（允许相等的幂等重试且 events 为空）。
 * <p>
 * 投影字段以地上游为准：非空则覆盖云端同列（对齐本地 SQLite sessions）。
 */
@Data
public class SessionIncrementalUploadRequest {

    /** W3 工号 → sessions.user_id */
    private String userId;

    /** substrate JWT sub → sessions.surrogate_id */
    private String surrogateId;

    /** cowork 套件 id */
    private String coworkId;

    /** local | cloud */
    private String source;

    /** Electron 安装 UUID */
    private String installId;

    /** "{surrogateId}:{coworkId}"；缺省由服务端在有两侧时拼接 */
    private String tenantId;

    /** 地端 sessions 投影字段 */
    private String userPrompt;
    private String goal;
    private String title;
    private String status;
    private String rootAgentId;
    private String llmProvider;
    private String llmModel;
    private Long tokenBudget;
    private Integer failureCounter;
    /** 地端完整 config_json 文本（如 {"template_id":"...","reasoning_effort":"..."}） */
    private String configJson;
    private String workspace;
    private OffsetDateTime createdAt;

    /** 本次上传后的新水位；若为空，则用「当前水位 + 成功写入事件数」 */
    private Integer uploadIndex;

    /** 增量事件列表（按发生顺序） */
    private List<EventDto> events = new ArrayList<>();
}
