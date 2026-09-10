package com.huawei.coworkdata.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class MemoryIngestDto {

    private String id;
    private String sessionId;
    private String taskId;
    private String agentId;
    private String layer;
    /** 写入 type 列（kind 或 legacy 字符串） */
    private String type;
    private String role;
    private String topic;
    private String content;
    private Map<String, Object> metadata;
    private OffsetDateTime timestamp;
    /** 地端透传：与本地 memory_events.seq_no 对齐；缺省由云端自增 */
    private Integer seqNo;
    /** 地端透传：与本地 topic_seq_no 对齐 */
    private Integer topicSeqNo;
    /** 地端透传：是否已 fold 标记 */
    private Boolean isSuperseded;
    /** PUBLICATION 写入前 supersede 同 topic 旧行 */
    private boolean supersedePublicationTopic;
}
