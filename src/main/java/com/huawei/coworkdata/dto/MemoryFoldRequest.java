package com.huawei.coworkdata.dto;

import lombok.Data;

import java.util.List;

@Data
public class MemoryFoldRequest {

    private List<String> supersedeIds;
    private List<MemoryIngestDto> replacements;
}
