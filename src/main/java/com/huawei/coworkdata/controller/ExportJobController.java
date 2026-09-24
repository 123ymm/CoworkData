package com.huawei.coworkdata.controller;

import com.huawei.coworkdata.dto.ExportJobDto;
import com.huawei.coworkdata.service.ExportJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 整库导出：后台任务 + 轮询 + 下载。
 */
@RestController
@RequestMapping("/api/db/export-jobs")
@RequiredArgsConstructor
public class ExportJobController {

    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final ExportJobService exportJobService;

    @PostMapping
    public ExportJobDto create() {
        return exportJobService.createJob();
    }

    @GetMapping("/{jobId}")
    public ExportJobDto status(@PathVariable String jobId) {
        return exportJobService.getJob(jobId);
    }

    @GetMapping("/{jobId}/file")
    public ResponseEntity<Resource> download(@PathVariable String jobId) {
        Resource body = exportJobService.openDownload(jobId);
        String filename = "coworkdata-" + FILE_TS.format(Instant.now()) + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }
}
