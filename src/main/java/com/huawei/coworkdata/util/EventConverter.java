package com.huawei.coworkdata.util;

import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.entity.EventEntity;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class EventConverter {

    private EventConverter() {
    }

    public static EventDto toDto(EventEntity row) {
        EventDto dto = new EventDto();
        dto.setId(row.getId());
        dto.setRunId(row.getRunId());
        dto.setSequence(row.getSequence());
        dto.setSessionId(row.getSessionId());
        dto.setType(row.getType());
        dto.setTimestamp(row.getTimestamp());
        dto.setTenantId(row.getTenantId());
        dto.setTaskId(row.getTaskId());
        dto.setAgentId(row.getAgentId());
        dto.setPayload(JsonUtils.parseMap(row.getPayloadJson()));
        dto.setMetadata(JsonUtils.parseMap(row.getMetadataJson()));
        dto.setCausationId(row.getCausationId());
        return dto;
    }

    public static EventEntity toEntity(EventDto event) {
        EventEntity entity = new EventEntity();
        entity.setId(event.getId());
        entity.setRunId(event.getRunId());
        entity.setSequence(event.getSequence());
        entity.setSessionId(event.getSessionId());
        entity.setType(event.getType());
        entity.setTimestamp(event.getTimestamp());
        entity.setTenantId(event.getTenantId() != null ? event.getTenantId() : "default");
        entity.setTaskId(event.getTaskId());
        entity.setAgentId(event.getAgentId());
        entity.setPayloadJson(JsonUtils.toJson(event.getPayload() != null ? event.getPayload() : Collections.emptyMap()));
        entity.setMetadataJson(JsonUtils.toJson(event.getMetadata() != null ? event.getMetadata() : Collections.emptyMap()));
        entity.setCausationId(event.getCausationId());
        return entity;
    }

    public static List<EventDto> toDtoList(List<EventEntity> rows) {
        return rows.stream().map(EventConverter::toDto).collect(Collectors.toList());
    }
}
