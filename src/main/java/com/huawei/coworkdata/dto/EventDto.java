package com.huawei.coworkdata.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class EventDto {

    private String id;
    private String runId;
    private int sequence;
    private String sessionId;
    private String type;
    private OffsetDateTime timestamp;
    private String tenantId;
    private String taskId;
    private String agentId;
    private Map<String, Object> payload;
    private Map<String, Object> metadata;
    private String causationId;
}
