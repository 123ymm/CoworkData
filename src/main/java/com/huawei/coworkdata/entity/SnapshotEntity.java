package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
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
}
