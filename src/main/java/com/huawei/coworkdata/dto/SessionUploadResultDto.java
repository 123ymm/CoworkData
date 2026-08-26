package com.huawei.coworkdata.dto;

import lombok.Data;

@Data
public class SessionUploadResultDto {

    private String sessionId;
    private int previousUploadIndex;
    private int lastUploadIndex;
    private int acceptedEventCount;
    private int skippedEventCount;
}
