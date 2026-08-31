package com.huawei.coworkdata.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class MemoryIngestDto {

    private String id;
    private String sessionId;
    private String taskId;
    private String agentId;
    private String layer;
    /** 写入 type 列（kind 或 legacy 字符串） */
    private String type;
    private String role;
    private String topic;
    private String content;
    private Map<String, Object> metadata;
    private OffsetDateTime timestamp;
    /** PUBLICATION 写入前 supersede 同 topic 旧行 */
    private boolean supersedePublicationTopic;
}
