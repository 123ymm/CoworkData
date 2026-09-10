package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.EventDto;

public interface ProjectionUpdaterService {

    /** 实时管线：投影失败只记日志，不抛出。 */
    void onEvent(EventDto event);

    /**
     * 增量上传等需与事件写入同事务的路径：投影失败抛出，以便整批回滚、水位不前进，地端可重试。
     */
    void onEventOrThrow(EventDto event);
}
