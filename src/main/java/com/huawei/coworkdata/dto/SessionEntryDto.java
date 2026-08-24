package com.huawei.coworkdata.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SessionEntryDto {

    private String sessionId;
    private String workspace;
    private Map<String, Object> userInfo;
}
