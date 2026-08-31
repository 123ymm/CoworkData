package com.huawei.coworkdata.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AgentTemplateDto {

    private String id;
    private String name;
    private String version;
    private String description;
    private String templateDir;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
