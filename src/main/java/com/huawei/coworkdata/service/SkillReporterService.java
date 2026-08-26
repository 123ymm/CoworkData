package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.EventDto;
import com.huawei.coworkdata.dto.SessionsStoreRequest;

public interface SkillReporterService {

    void setSessionsStore(SessionsStoreRequest request);

    void onEvent(EventDto event);

    void close();
}
