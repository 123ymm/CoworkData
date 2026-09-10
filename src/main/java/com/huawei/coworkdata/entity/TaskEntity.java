package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.NOT_NULL)
    private String status;
    /** NOT NULL：INSERT 必须带值（缺省用 ""，与地端一致） */
    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.NOT_NULL)
    private String title;
    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.NOT_NULL)
    private String description;
    private String userPrompt;
    private String assignedAgentId;
    private String creatorAgentId;
    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.NOT_NULL)
    private Boolean isDaemon;
    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.NOT_NULL)
    private String outputsJson;
    private String error;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
