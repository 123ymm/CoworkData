package com.huawei.coworkdata.service.impl;

import com.huawei.coworkdata.config.ExportAsyncConfig;
import com.huawei.coworkdata.dto.ExportJobDto;
import com.huawei.coworkdata.export.ExportJob;
import com.huawei.coworkdata.export.ExportJobStatus;
import com.huawei.coworkdata.service.DbExportService;
import com.huawei.coworkdata.service.ExportJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

@Service
public class ExportJobServiceImpl implements ExportJobService {

    private static final Logger log = LoggerFactory.getLogger(ExportJobServiceImpl.class);

    /** 任务与文件保留时长。 */
    private static final Duration JOB_TTL = Duration.ofHours(1);

    private final DbExportService dbExportService;
    private final TaskExecutor exportExecutor;
    private final ConcurrentHashMap<String, ExportJob> jobs = new ConcurrentHashMap<String, ExportJob>();

    public ExportJobServiceImpl(
            DbExportService dbExportService,
            @Qualifier(ExportAsyncConfig.EXPORT_EXECUTOR) TaskExecutor exportExecutor) {
        this.dbExportService = dbExportService;
        this.exportExecutor = exportExecutor;
    }

    @Override
    public ExportJobDto createJob() {
        cleanupExpired();

        final String jobId = UUID.randomUUID().toString().replace("-", "");
        final ExportJob job = new ExportJob(jobId);
        jobs.put(jobId, job);

        try {
            exportExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    runExport(jobId);
                }
            });
        } catch (TaskRejectedException ex) {
            jobs.remove(jobId);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "已有导出任务在执行，请稍后再试（最多同时 2 个）");
        } catch (RejectedExecutionException ex) {
            jobs.remove(jobId);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "已有导出任务在执行，请稍后再试（最多同时 2 个）");
        }

        return toDto(job);
    }

    @Override
    public ExportJobDto getJob(String jobId) {
        ExportJob job = jobs.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导出任务不存在或已过期");
        }
        return toDto(job);
    }

    @Override
    public Resource openDownload(String jobId) {
        ExportJob job = jobs.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导出任务不存在或已过期");
        }
        if (job.getStatus() != ExportJobStatus.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "导出尚未完成: " + job.getStatus());
        }
        Path zipPath = job.getZipPath();
        if (zipPath == null || !Files.isRegularFile(zipPath)) {
            removeJob(jobId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导出文件不存在");
        }
        return new DeletingJobResource(zipPath.toFile(), jobId);
    }

    @Override
    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void cleanupExpired() {
        Instant cutoff = Instant.now().minus(JOB_TTL);
        Iterator<Map.Entry<String, ExportJob>> it = jobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ExportJob> entry = it.next();
            ExportJob job = entry.getValue();
            Instant anchor = job.getFinishedAt() != null ? job.getFinishedAt() : job.getCreatedAt();
            if (anchor.isBefore(cutoff)) {
                it.remove();
                deleteQuietly(job.getZipPath());
                log.info("Cleaned expired export job {}", job.getJobId());
            }
        }
    }

    private void runExport(String jobId) {
        ExportJob job = jobs.get(jobId);
        if (job == null) {
            return;
        }
        Path zipPath = null;
        try {
            zipPath = Files.createTempFile("coworkdata-export-" + jobId + "-", ".zip");
            OutputStream out = Files.newOutputStream(zipPath);
            try {
                dbExportService.writeAllTables(out);
            } finally {
                out.close();
            }
            if (jobs.get(jobId) == job) {
                job.markDone(zipPath);
                log.info("Export job {} done, file={}", jobId, zipPath);
            } else {
                deleteQuietly(zipPath);
            }
        } catch (Exception ex) {
            log.error("Export job {} failed", jobId, ex);
            deleteQuietly(zipPath);
            if (jobs.get(jobId) == job) {
                String msg = ex.getMessage();
                job.markFailed(msg == null || msg.trim().isEmpty() ? ex.getClass().getSimpleName() : msg);
            }
        }
    }

    void removeJob(String jobId) {
        ExportJob removed = jobs.remove(jobId);
        if (removed != null) {
            deleteQuietly(removed.getZipPath());
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static ExportJobDto toDto(ExportJob job) {
        ExportJobDto dto = new ExportJobDto();
        dto.setJobId(job.getJobId());
        dto.setStatus(job.getStatus());
        dto.setMessage(job.getMessage());
        dto.setCreatedAt(job.getCreatedAt().toString());
        if (job.getFinishedAt() != null) {
            dto.setFinishedAt(job.getFinishedAt().toString());
        }
        return dto;
    }

    /** 下载流关闭后删除任务与临时文件。 */
    private final class DeletingJobResource extends FileSystemResource {

        private final String jobId;

        private DeletingJobResource(File file, String jobId) {
            super(file);
            this.jobId = jobId;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            final InputStream in = super.getInputStream();
            return new FilterInputStream(in) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        removeJob(jobId);
                    }
                }
            };
        }
    }
}
