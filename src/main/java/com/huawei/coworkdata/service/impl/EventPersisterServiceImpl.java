package com.huawei.coworkdata.service.impl;

import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.service.EventPersisterService;
import com.huawei.coworkdata.service.PostgresEventStoreService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventPersisterServiceImpl implements EventPersisterService {

    private static final Logger log = LoggerFactory.getLogger(EventPersisterServiceImpl.class);

    private static final Set<String> TRANSIENT_EVENT_TYPES = Set.of(
            "text_delta", "reasoning_delta", "TextDelta", "ReasoningDelta"
    );

    private final PostgresEventStoreService eventStore;

    @Override
    public void onEvent(EventDto event) {
        if (event.getType() != null && TRANSIENT_EVENT_TYPES.contains(event.getType())) {
            return;
        }
        try {
            eventStore.append(event);
        } catch (Exception e) {
            log.error("EventPersister: failed to append event {} ({})", event.getId(), event.getType(), e);
        }
    }
}
