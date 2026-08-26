package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.EventDto;

public interface EventPersisterService {

    void onEvent(EventDto event);
}
