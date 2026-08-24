package com.huawei.coworkdata.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class RunSnapshotDto {

    private String id;
    private String runId;
    private String sessionId;
    private String lastEventId;
    private int lastEventSequence;
    private Map<String, Object> stateBlob;
    private String snapshotReason;
    private OffsetDateTime snapshotAt;
}
