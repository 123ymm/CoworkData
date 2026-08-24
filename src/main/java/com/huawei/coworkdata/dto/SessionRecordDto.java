package com.huawei.coworkdata.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class SessionRecordDto {

    private String id;
    private String tenantId;
    private String userPrompt;
    private String status;
    private String goal;
    private String rootAgentId;
    private String llmProvider;
    private String llmModel;
    private long tokenBudget;
    private int failureCounter;
    private Map<String, Object> config;
    private String workspace;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
