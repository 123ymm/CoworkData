package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.dto.RunSnapshotDto;

import java.util.List;
import java.util.Map;

public interface PostgresEventStoreService {

    void append(EventDto event);

    /** 若事件 id 已存在则跳过，返回是否新写入 */
    boolean appendIfAbsent(EventDto event);

    List<EventDto> readBySession(String sessionId);

    List<EventDto> readSessionEventsOfTypes(String sessionId, List<String> types);

    List<String> listActiveSessionIds();

    Map<String, String> lastActivityTimes(List<String> excludeTypes);

    List<EventDto> readAfter(String sessionId, String afterEventId);

    void saveSnapshot(RunSnapshotDto snapshot);

    RunSnapshotDto loadLatestSnapshot(String sessionId);
}
