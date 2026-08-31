package com.huawei.coworkdata.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class MemoryRecordDto {

    private String id;
    private String type;
    private String kind;
    private String scope;
    private String role;
    private String topic;
    private Object content;
    private String sessionId;
    private String taskId;
    private String agentId;
    private Map<String, Object> metadata;
    private OffsetDateTime timestamp;
}
