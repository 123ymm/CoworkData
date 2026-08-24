package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("events")
public class EventEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String runId;
    private String sessionId;
    private String taskId;
    private String agentId;
    private String tenantId;
    private String type;
    private Integer sequence;
    private String payloadJson;
    private String metadataJson;
    private String causationId;
    private OffsetDateTime timestamp;
}
