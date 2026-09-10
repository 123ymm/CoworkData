package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huawei.coworkdata.util.Strings;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("memory_subscriptions")
public class MemorySubscriptionEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String sessionId;
    private String taskId;
    private String topic;
    private Integer cursor;
    private String intent;
    private OffsetDateTime createdAt;

    public void setSessionId(String sessionId) {
        this.sessionId = Strings.nz(sessionId);
    }

    public void setTaskId(String taskId) {
        this.taskId = Strings.nz(taskId);
    }

    public void setTopic(String topic) {
        this.topic = Strings.nz(topic);
    }

    public void setIntent(String intent) {
        this.intent = Strings.nz(intent);
    }

    public void setCursor(Integer cursor) {
        this.cursor = cursor != null ? cursor : 0;
    }
}
