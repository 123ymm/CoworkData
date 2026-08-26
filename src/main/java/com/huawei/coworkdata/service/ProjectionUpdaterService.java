package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.EventDto;

public interface ProjectionUpdaterService {

    void onEvent(EventDto event);
}
