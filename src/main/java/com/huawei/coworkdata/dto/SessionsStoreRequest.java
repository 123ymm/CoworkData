package com.huawei.coworkdata.dto;

import lombok.Data;

import java.util.List;

@Data
public class SessionsStoreRequest {

    private List<SessionEntryDto> entries;
}
