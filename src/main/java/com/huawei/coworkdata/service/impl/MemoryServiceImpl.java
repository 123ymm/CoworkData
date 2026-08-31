package com.huawei.coworkdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huawei.coworkdata.dto.MemoryIngestDto;
import com.huawei.coworkdata.dto.MemoryRecordDto;
import com.huawei.coworkdata.entity.MemoryEventEntity;
import com.huawei.coworkdata.entity.MemorySubscriptionEntity;
import com.huawei.coworkdata.mapper.MemoryEventMapper;
import com.huawei.coworkdata.mapper.MemorySubscriptionMapper;
import com.huawei.coworkdata.service.MemoryService;
import com.huawei.coworkdata.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemoryServiceImpl implements MemoryService {

    private final MemoryEventMapper memoryEventMapper;
    private final MemorySubscriptionMapper subscriptionMapper;

    @Override
    @Transactional
    public String ingest(MemoryIngestDto dto) {
        String eventId = dto.getId() != null && !dto.getId().isBlank()
                ? dto.getId()
                : "mev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        if (memoryEventMapper.selectById(eventId) != null) {
            return eventId;
        }
        if (dto.isSupersedePublicationTopic() && dto.getTopic() != null && !dto.getTopic().isBlank()) {
            memoryEventMapper.supersedePublicationTopic(dto.getTopic());
        }
        int maxSeq = memoryEventMapper.maxSeqNo(
                dto.getSessionId(),
                dto.getLayer(),
                "task".equals(dto.getLayer()) ? dto.getTaskId() : null,
                "agent".equals(dto.getLayer()) ? dto.getAgentId()
                        : ("task".equals(dto.getLayer()) && dto.getAgentId() != null ? dto.getAgentId() : null));
        int topicSeq = 0;
        if (dto.getTopic() != null && !dto.getTopic().isBlank()) {
            topicSeq = memoryEventMapper.maxTopicSeqNo(dto.getTopic()) + 1;
        }
        MemoryEventEntity entity = new MemoryEventEntity();
        entity.setId(eventId);
        entity.setSessionId(dto.getSessionId());
        entity.setTaskId(dto.getTaskId());
        entity.setAgentId(dto.getAgentId());
        entity.setLayer(dto.getLayer());
        entity.setType(dto.getType());
        entity.setRole(dto.getRole());
        entity.setTopic(dto.getTopic());
        entity.setContent(dto.getContent() != null ? dto.getContent() : "");
        entity.setSeqNo(maxSeq + 1);
        entity.setTopicSeqNo(topicSeq);
        entity.setIsSuperseded(false);
        entity.setMetadataJson(JsonUtils.toJson(dto.getMetadata() != null ? dto.getMetadata() : Map.of()));
        entity.setTimestamp(dto.getTimestamp() != null ? dto.getTimestamp() : OffsetDateTime.now());
        memoryEventMapper.insert(entity);
        return eventId;
    }

    @Override
    @Transactional
    public List<String> fold(List<String> supersedeIds, List<MemoryIngestDto> replacements) {
        if (supersedeIds != null && !supersedeIds.isEmpty()) {
            memoryEventMapper.update(
                    null,
                    new LambdaUpdateWrapper<MemoryEventEntity>()
                            .in(MemoryEventEntity::getId, supersedeIds)
                            .eq(MemoryEventEntity::getIsSuperseded, false)
                            .set(MemoryEventEntity::getIsSuperseded, true));
        }
        List<String> newIds = new ArrayList<>();
        if (replacements != null) {
            for (MemoryIngestDto rep : replacements) {
                newIds.add(ingest(rep));
            }
        }
        return newIds;
    }

    @Override
    public List<MemoryRecordDto> loadViewRaw(
            String sessionId,
            String layer,
            List<String> typeFilter,
            String taskId,
            String agentId,
            boolean crossTask) {
        LambdaQueryWrapper<MemoryEventEntity> q = new LambdaQueryWrapper<MemoryEventEntity>()
                .eq(MemoryEventEntity::getSessionId, sessionId)
                .eq(MemoryEventEntity::getLayer, layer)
                .eq(MemoryEventEntity::getIsSuperseded, false)
                .in(MemoryEventEntity::getType, typeFilter)
                .orderByAsc(MemoryEventEntity::getTimestamp)
                .orderByAsc(MemoryEventEntity::getSeqNo);
        if ("task".equals(layer)) {
            if (crossTask) {
                q.eq(MemoryEventEntity::getAgentId, agentId);
            } else {
                if (taskId != null) {
                    q.eq(MemoryEventEntity::getTaskId, taskId);
                }
                if (agentId != null) {
                    q.eq(MemoryEventEntity::getAgentId, agentId);
                }
            }
        } else if ("agent".equals(layer)) {
            q.eq(MemoryEventEntity::getAgentId, agentId);
        }
        return memoryEventMapper.selectList(q).stream().map(this::toRecordDto).toList();
    }

    @Override
    public Map<String, Object> recallTopic(String topic, int since) {
        List<MemoryEventEntity> rows = memoryEventMapper.selectList(
                new LambdaQueryWrapper<MemoryEventEntity>()
                        .eq(MemoryEventEntity::getTopic, topic)
                        .gt(MemoryEventEntity::getTopicSeqNo, since)
                        .eq(MemoryEventEntity::getIsSuperseded, false)
                        .orderByAsc(MemoryEventEntity::getTopicSeqNo));
        int cursor = since;
        List<MemoryRecordDto> records = new ArrayList<>();
        for (MemoryEventEntity row : rows) {
            records.add(toRecordDto(row));
            cursor = row.getTopicSeqNo();
        }
        Map<String, Object> out = new HashMap<>();
        out.put("records", records);
        out.put("cursor", cursor);
        return out;
    }

    @Override
    @Transactional
    public String subscribe(String sessionId, String taskId, String topic, String intent) {
        String tid = taskId != null ? taskId : "";
        MemorySubscriptionEntity existing = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<MemorySubscriptionEntity>()
                        .eq(MemorySubscriptionEntity::getSessionId, sessionId)
                        .eq(MemorySubscriptionEntity::getTaskId, tid)
                        .eq(MemorySubscriptionEntity::getTopic, topic));
        if (existing != null) {
            return existing.getId();
        }
        String subId = "sub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MemorySubscriptionEntity entity = new MemorySubscriptionEntity();
        entity.setId(subId);
        entity.setSessionId(sessionId);
        entity.setTaskId(tid);
        entity.setTopic(topic);
        entity.setCursor(0);
        entity.setIntent(intent);
        entity.setCreatedAt(OffsetDateTime.now());
        subscriptionMapper.insert(entity);
        return subId;
    }

    @Override
    public List<Map<String, Object>> listSubscriptions(String sessionId, String taskId) {
        LambdaQueryWrapper<MemorySubscriptionEntity> q = new LambdaQueryWrapper<MemorySubscriptionEntity>()
                .eq(MemorySubscriptionEntity::getSessionId, sessionId);
        if (taskId != null) {
            q.in(MemorySubscriptionEntity::getTaskId, List.of(taskId, ""));
        }
        return subscriptionMapper.selectList(q).stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("sessionId", r.getSessionId());
            m.put("taskId", r.getTaskId());
            m.put("topic", r.getTopic());
            m.put("cursor", r.getCursor());
            m.put("intent", r.getIntent());
            return m;
        }).toList();
    }

    private MemoryRecordDto toRecordDto(MemoryEventEntity row) {
        MemoryRecordDto dto = new MemoryRecordDto();
        dto.setId(row.getId());
        dto.setType(row.getType());
        dto.setRole(row.getRole());
        dto.setTopic(row.getTopic());
        dto.setSessionId(row.getSessionId());
        dto.setTaskId(row.getTaskId());
        dto.setAgentId(row.getAgentId());
        dto.setScope(row.getLayer());
        dto.setTimestamp(row.getTimestamp());
        try {
            Object parsed = JsonUtils.mapper().readValue(row.getContent(), Object.class);
            dto.setContent(parsed);
        } catch (Exception ex) {
            dto.setContent(row.getContent());
        }
        Map<String, Object> meta = JsonUtils.parseMap(row.getMetadataJson());
        meta.put("seq_no", row.getSeqNo());
        meta.put("layer", row.getLayer());
        meta.put("task_id", row.getTaskId());
        dto.setMetadata(meta);
        return dto;
    }
}
