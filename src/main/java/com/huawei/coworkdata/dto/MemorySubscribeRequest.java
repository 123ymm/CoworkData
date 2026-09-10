package com.huawei.coworkdata.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class MemorySubscribeRequest {

    /** 地端透传主键；缺省云端生成 */
    private String id;
    private String sessionId;
    private String taskId;
    private String topic;
    private String intent;
    /** 地端透传 cursor；缺省 0 */
    private Integer cursor;
    /** 地端透传创建时间 */
    private OffsetDateTime createdAt;
}
