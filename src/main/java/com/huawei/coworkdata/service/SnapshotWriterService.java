package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.EventDto;

public interface SnapshotWriterService {

    void onEvent(EventDto event);

    void close();
}
