package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("session_sse_events")
public class SessionSseEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private String eventJson;
}
