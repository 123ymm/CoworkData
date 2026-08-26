package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("sessions")
public class SessionEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String tenantId;
    /** 会话所属用户（地端账号 / 工号等） */
    private String userId;
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
    /** 地端 cowork 上次上传到的进度索引 */
    private Integer lastUploadIndex;
    /** 软删时间；null 表示未删除 */
    @TableLogic(value = "null", delval = "now()")
    private OffsetDateTime deleteAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
