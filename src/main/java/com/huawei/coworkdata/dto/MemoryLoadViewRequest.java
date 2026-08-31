package com.huawei.coworkdata.dto;

import lombok.Data;

import java.util.List;

@Data
public class MemoryLoadViewRequest {

    private String sessionId;
    private String layer;
    private List<String> typeFilter;
    private String taskId;
    private String agentId;
    private boolean crossTask;
}
