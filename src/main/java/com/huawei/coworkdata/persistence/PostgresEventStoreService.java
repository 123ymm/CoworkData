package com.huawei.coworkdata.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.dto.RunSnapshotDto;
import com.huawei.coworkdata.entity.EventEntity;
import com.huawei.coworkdata.entity.SnapshotEntity;
import com.huawei.coworkdata.mapper.EventMapper;
import com.huawei.coworkdata.mapper.SnapshotMapper;
import com.huawei.coworkdata.util.EventConverter;
import com.huawei.coworkdata.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostgresEventStoreService {

    private final EventMapper eventMapper;
    private final SnapshotMapper snapshotMapper;
    private final int keepSnapshots = 3;

    @Transactional
    public void append(EventDto event) {
        eventMapper.insert(EventConverter.toEntity(event));
    }

    public List<EventDto> readBySession(String sessionId) {
        List<EventEntity> rows = eventMapper.selectList(
                new LambdaQueryWrapper<EventEntity>()
                        .eq(EventEntity::getSessionId, sessionId)
                        .orderByAsc(EventEntity::getId));
        return EventConverter.toDtoList(rows);
    }

    public List<EventDto> readSessionEventsOfTypes(String sessionId, List<String> types) {
        List<EventEntity> rows = eventMapper.selectList(
                new LambdaQueryWrapper<EventEntity>()
                        .eq(EventEntity::getSessionId, sessionId)
                        .in(EventEntity::getType, types)
                        .orderByAsc(EventEntity::getId));
        return EventConverter.toDtoList(rows);
    }

    public List<String> listActiveSessionIds() {
        return eventMapper.listActiveSessionIds();
    }

    public Map<String, String> lastActivityTimes(List<String> excludeTypes) {
        List<String> excludes = excludeTypes != null && !excludeTypes.isEmpty()
                ? excludeTypes
                : List.of("SessionStatusChanged");
        String excludeSql = excludes.stream()
                .map(t -> "'" + t.replace("'", "''") + "'")
                .reduce((a, b) -> a + "," + b)
                .orElse("'SessionStatusChanged'");
        Map<String, String> result = new HashMap<>();
        for (Map<String, Object> row : eventMapper.lastActivityRows(excludeSql)) {
            String sessionId = stringValue(row.get("session_id"));
            Object ts = row.get("last_ts");
            if (sessionId != null && ts != null) {
                if (ts instanceof OffsetDateTime odt) {
                    result.put(sessionId, odt.withOffsetSameInstant(ZoneOffset.UTC).toString());
                } else {
                    result.put(sessionId, ts.toString());
                }
            }
        }
        return result;
    }

    public List<EventDto> readAfter(String sessionId, String afterEventId) {
        List<EventEntity> rows = eventMapper.selectList(
                new LambdaQueryWrapper<EventEntity>()
                        .eq(EventEntity::getSessionId, sessionId)
                        .gt(EventEntity::getId, afterEventId)
                        .orderByAsc(EventEntity::getId));
        return EventConverter.toDtoList(rows);
    }

    @Transactional
    public void saveSnapshot(RunSnapshotDto snapshot) {
        SnapshotEntity entity = new SnapshotEntity();
        entity.setId(snapshot.getId());
        entity.setSessionId(snapshot.getSessionId());
        entity.setLastEventId(snapshot.getLastEventId());
        entity.setLastEventSequence(snapshot.getLastEventSequence());
        entity.setStateBlobJson(JsonUtils.toJson(snapshot.getStateBlob()));
        entity.setSnapshotReason(snapshot.getSnapshotReason() != null ? snapshot.getSnapshotReason() : "");
        entity.setCreatedAt(snapshot.getSnapshotAt());
        snapshotMapper.insert(entity);
        snapshotMapper.pruneOldSnapshots(snapshot.getSessionId(), Math.max(1, keepSnapshots));
    }

    public RunSnapshotDto loadLatestSnapshot(String sessionId) {
        SnapshotEntity row = snapshotMapper.selectOne(
                new LambdaQueryWrapper<SnapshotEntity>()
                        .eq(SnapshotEntity::getSessionId, sessionId)
                        .orderByDesc(SnapshotEntity::getCreatedAt)
                        .last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        RunSnapshotDto dto = new RunSnapshotDto();
        dto.setId(row.getId());
        dto.setRunId("");
        dto.setSessionId(row.getSessionId());
        dto.setLastEventId(row.getLastEventId());
        dto.setLastEventSequence(row.getLastEventSequence());
        dto.setStateBlob(JsonUtils.parseMap(row.getStateBlobJson()));
        dto.setSnapshotReason(row.getSnapshotReason());
        dto.setSnapshotAt(row.getCreatedAt());
        return dto;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
