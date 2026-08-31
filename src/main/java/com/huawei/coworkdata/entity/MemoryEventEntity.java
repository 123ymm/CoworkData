package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("memory_events")
public class MemoryEventEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String sessionId;
    private String taskId;
    private String agentId;
    private String layer;
    private String type;
    private String role;
    private String topic;
    private String content;
    private Integer seqNo;
    private Integer topicSeqNo;
    private Boolean isSuperseded;
    private String metadataJson;
    private OffsetDateTime timestamp;
}
