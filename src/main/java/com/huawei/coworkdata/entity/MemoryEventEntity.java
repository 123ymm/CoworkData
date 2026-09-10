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

    public void setSessionId(String sessionId) {
        this.sessionId = Strings.nz(sessionId);
    }

    public void setLayer(String layer) {
        this.layer = Strings.nz(layer, "task");
    }

    public void setType(String type) {
        this.type = Strings.nz(type);
    }

    public void setContent(String content) {
        this.content = Strings.nz(content);
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = Strings.nz(metadataJson, "{}");
    }

    public void setSeqNo(Integer seqNo) {
        this.seqNo = seqNo != null ? seqNo : 0;
    }

    public void setTopicSeqNo(Integer topicSeqNo) {
        this.topicSeqNo = topicSeqNo != null ? topicSeqNo : 0;
    }

    public void setIsSuperseded(Boolean isSuperseded) {
        this.isSuperseded = isSuperseded != null ? isSuperseded : Boolean.FALSE;
    }
}
