package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huawei.coworkdata.util.Strings;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
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

    public void setSessionId(String sessionId) {
        this.sessionId = Strings.nz(sessionId);
    }

    public void setTenantId(String tenantId) {
        this.tenantId = Strings.nz(tenantId, "default");
    }

    public void setType(String type) {
        this.type = Strings.nz(type);
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = Strings.nz(payloadJson, "{}");
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = Strings.nz(metadataJson, "{}");
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence != null ? sequence : 0;
    }
}
