package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("tasks")
public class TaskEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String sessionId;
    private String status;
    private String title;
    private String description;
    private String userPrompt;
    private String assignedAgentId;
    private String creatorAgentId;
    private Boolean isDaemon;
    private String outputsJson;
    private String error;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
