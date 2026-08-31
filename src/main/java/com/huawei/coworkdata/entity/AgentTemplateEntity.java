package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("agent_templates")
public class AgentTemplateEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private String version;
    private String description;
    private String templateDir;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
