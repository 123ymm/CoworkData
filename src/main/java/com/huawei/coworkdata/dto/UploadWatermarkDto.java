package com.huawei.coworkdata.dto;

import lombok.Data;

@Data
public class UploadWatermarkDto {

    private String sessionId;
    private int lastUploadIndex;
}
