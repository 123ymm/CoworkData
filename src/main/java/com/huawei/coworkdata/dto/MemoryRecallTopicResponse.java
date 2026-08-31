package com.huawei.coworkdata.dto;

import lombok.Data;

import java.util.List;

@Data
public class MemoryRecallTopicResponse {

    private List<MemoryRecordDto> records;
    private int cursor;
}
