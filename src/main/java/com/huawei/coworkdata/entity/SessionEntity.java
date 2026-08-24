package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("sessions")
public class SessionEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String userPrompt;
    private String status;
    private String goal;
    private String rootAgentId;
    private String llmProvider;
    private String llmModel;
    private Long tokenBudget;
    private Integer failureCounter;
    private String configJson;
    private String workspace;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
