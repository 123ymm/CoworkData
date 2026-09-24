package com.huawei.coworkdata.export;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 内存中的导出任务。
 */
public class ExportJob {

    private final String jobId;
    private final Instant createdAt;
    private volatile ExportJobStatus status;
    private volatile String message;
    private volatile Instant finishedAt;
    private volatile Path zipPath;

    public ExportJob(String jobId) {
        this.jobId = jobId;
        this.createdAt = Instant.now();
        this.status = ExportJobStatus.RUNNING;
        this.message = "导出进行中";
    }

    public String getJobId() {
        return jobId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ExportJobStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Path getZipPath() {
        return zipPath;
    }

    public void markDone(Path zipPath) {
        this.zipPath = zipPath;
        this.status = ExportJobStatus.DONE;
        this.message = "导出完成";
        this.finishedAt = Instant.now();
    }

    public void markFailed(String message) {
        this.status = ExportJobStatus.FAILED;
        this.message = message == null ? "导出失败" : message;
        this.finishedAt = Instant.now();
    }
}
