package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huawei.coworkdata.util.Strings;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("tasks")
public class TaskEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String sessionId;
    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.NOT_NULL)
    private String status;
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

    public void setStatus(String status) {
        this.status = Strings.nz(status, "PENDING");
    }

    public void setTitle(String title) {
        this.title = Strings.nz(title);
    }

    public void setDescription(String description) {
        this.description = Strings.nz(description);
    }

    public void setOutputsJson(String outputsJson) {
        this.outputsJson = Strings.nz(outputsJson, "null");
    }

    public void setIsDaemon(Boolean isDaemon) {
        this.isDaemon = isDaemon != null ? isDaemon : Boolean.FALSE;
    }
}
