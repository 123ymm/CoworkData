package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huawei.coworkdata.util.Strings;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("session_sse_events")
public class SessionSseEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private String eventJson;

    public void setSessionId(String sessionId) {
        this.sessionId = Strings.nz(sessionId);
    }

    public void setEventJson(String eventJson) {
        this.eventJson = Strings.nz(eventJson);
    }
}
