package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huawei.coworkdata.util.Strings;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("sessions")
public class SessionEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    /** "{surrogate_id}:{cowork_id}"；缺一侧时可为 default */
    private String tenantId;
    /** W3 工号（username） */
    private String userId;
    /** substrate JWT sub */
    private String surrogateId;
    private String coworkId;
    /** local | cloud */
    private String source;
    /** Electron 安装 UUID */
    private String installId;
    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.NOT_NULL)
    private String userPrompt;
    private String status;
    private String title;
    private String goal;
    private String rootAgentId;
    private String llmProvider;
    private String llmModel;
    private Long tokenBudget;
    private Integer failureCounter;
    private String configJson;
    private String workspace;
    private Integer lastUploadIndex;
    @TableLogic(value = "null", delval = "now()")
    private OffsetDateTime deleteAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setTenantId(String tenantId) {
        this.tenantId = Strings.nz(tenantId, "default");
    }

    public void setSource(String source) {
        this.source = Strings.nz(source, "local");
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = Strings.nz(userPrompt);
    }

    public void setStatus(String status) {
        this.status = Strings.nz(status, "RUNNING");
    }

    public void setTitle(String title) {
        this.title = Strings.nz(title);
    }

    public void setGoal(String goal) {
        this.goal = Strings.nz(goal);
    }

    public void setConfigJson(String configJson) {
        this.configJson = Strings.nz(configJson, "{}");
    }
}
