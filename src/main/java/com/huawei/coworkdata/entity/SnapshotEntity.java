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
@TableName("snapshots")
public class SnapshotEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String sessionId;
    private String lastEventId;
    private Integer lastEventSequence;
    private String stateBlobJson;
    private String snapshotReason;
    private OffsetDateTime createdAt;

    public void setSessionId(String sessionId) {
        this.sessionId = Strings.nz(sessionId);
    }

    public void setLastEventId(String lastEventId) {
        this.lastEventId = Strings.nz(lastEventId);
    }

    public void setLastEventSequence(Integer lastEventSequence) {
        this.lastEventSequence = lastEventSequence != null ? lastEventSequence : 0;
    }

    public void setStateBlobJson(String stateBlobJson) {
        this.stateBlobJson = Strings.nz(stateBlobJson, "{}");
    }

    public void setSnapshotReason(String snapshotReason) {
        this.snapshotReason = Strings.nz(snapshotReason);
    }
}
