package com.huawei.coworkdata.controller;

import com.huawei.coworkdata.dto.MemoryIngestDto;
import com.huawei.coworkdata.dto.MemoryFoldRequest;
import com.huawei.coworkdata.dto.MemoryLoadViewRequest;
import com.huawei.coworkdata.dto.MemoryRecallTopicResponse;
import com.huawei.coworkdata.dto.MemorySubscribeRequest;
import com.huawei.coworkdata.dto.MemoryRecordDto;
import com.huawei.coworkdata.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对应 Python {@code PostgresMemoryProvider}（v2 协议面）。
 */
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    @PostMapping("/ingest")
    public Map<String, String> ingest(@RequestBody MemoryIngestDto dto) {
        Map<String, String> result = new HashMap<>();
        result.put("id", memoryService.ingest(dto));
        return result;
    }

    @PostMapping("/fold")
    public Map<String, List<String>> fold(@RequestBody MemoryFoldRequest request) {
        Map<String, List<String>> result = new HashMap<>();
        result.put("ids", memoryService.fold(request.getSupersedeIds(), request.getReplacements()));
        return result;
    }

    @PostMapping("/load-view-raw")
    public List<MemoryRecordDto> loadViewRaw(@RequestBody MemoryLoadViewRequest request) {
        return memoryService.loadViewRaw(
                request.getSessionId(),
                request.getLayer(),
                request.getTypeFilter(),
                request.getTaskId(),
                request.getAgentId(),
                request.isCrossTask());
    }

    @GetMapping("/recall-topic")
    public MemoryRecallTopicResponse recallTopic(
            @RequestParam String topic,
            @RequestParam(defaultValue = "0") int since) {
        Map<String, Object> raw = memoryService.recallTopic(topic, since);
        MemoryRecallTopicResponse resp = new MemoryRecallTopicResponse();
        @SuppressWarnings("unchecked")
        List<MemoryRecordDto> records = (List<MemoryRecordDto>) raw.get("records");
        resp.setRecords(records);
        resp.setCursor((Integer) raw.get("cursor"));
        return resp;
    }

    @PostMapping("/subscriptions")
    public Map<String, String> subscribe(@RequestBody MemorySubscribeRequest request) {
        Map<String, String> result = new HashMap<>();
        result.put("id", memoryService.subscribe(
                request.getSessionId(),
                request.getTaskId(),
                request.getTopic(),
                request.getIntent()));
        return result;
    }

    @GetMapping("/subscriptions")
    public List<Map<String, Object>> listSubscriptions(
            @RequestParam String sessionId,
            @RequestParam(required = false) String taskId) {
        return memoryService.listSubscriptions(sessionId, taskId);
    }

    @GetMapping("/describe")
    public Map<String, Object> describe() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "coworkdata_memory");
        result.put("supportsSemantic", false);
        result.put("supportsTopic", true);
        result.put("archivesSuperseded", true);
        return result;
    }
}
