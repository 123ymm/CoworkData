package com.huawei.coworkdata.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 地端 cowork 增量上传会话事件。
 * <p>
 * {@code uploadIndex} 为本次批次结束后的新水位（通常为事件序号或本地游标）。
 * 服务端会拒绝 {@code uploadIndex <= 当前水位} 的回退（允许相等的幂等重试且 events 为空）。
 */
@Data
public class SessionIncrementalUploadRequest {

    /** 会话所属用户；首次上传建议带上，写入 sessions.user_id */
    private String userId;

    /** 本次上传后的新水位；若为空，则用「当前水位 + 成功写入事件数」 */
    private Integer uploadIndex;

    /** 增量事件列表（按发生顺序） */
    private List<EventDto> events = new ArrayList<>();
}
