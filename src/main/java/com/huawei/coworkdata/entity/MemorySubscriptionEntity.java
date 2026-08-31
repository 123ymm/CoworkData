package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
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
}
