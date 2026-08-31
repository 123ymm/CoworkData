package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.MemoryIngestDto;
import com.huawei.coworkdata.dto.MemoryRecordDto;

import java.util.List;
import java.util.Map;

public interface MemoryService {

    String ingest(MemoryIngestDto dto);

    List<String> fold(List<String> supersedeIds, List<MemoryIngestDto> replacements);

    List<MemoryRecordDto> loadViewRaw(
            String sessionId,
            String layer,
            List<String> typeFilter,
            String taskId,
            String agentId,
            boolean crossTask);

    Map<String, Object> recallTopic(String topic, int since);

    String subscribe(String sessionId, String taskId, String topic, String intent);

    List<Map<String, Object>> listSubscriptions(String sessionId, String taskId);
}
