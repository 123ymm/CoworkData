package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.ExportJobDto;
import org.springframework.core.io.Resource;

/**
 * 整库导出后台任务。
 */
public interface ExportJobService {

    /**
     * 创建导出任务并异步执行。忙时抛出过多请求。
     */
    ExportJobDto createJob();

    ExportJobDto getJob(String jobId);

    /**
     * 返回已完成任务的 ZIP；流关闭后删除任务与文件。
     */
    Resource openDownload(String jobId);

    /** 定时清理过期任务。 */
    void cleanupExpired();
}
