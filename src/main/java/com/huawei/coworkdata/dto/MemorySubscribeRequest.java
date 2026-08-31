package com.huawei.coworkdata.dto;

import lombok.Data;

import java.util.List;

@Data
public class MemorySubscribeRequest {

    private String sessionId;
    private String taskId;
    private String topic;
    private String intent;
}
