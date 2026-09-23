package com.huawei.coworkdata.controller;

import com.huawei.coworkdata.service.DbExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 整库数据下载。
 */
@RestController
@RequestMapping("/api/db")
@RequiredArgsConstructor
public class DbExportController {

    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final DbExportService dbExportService;

    @GetMapping("/export-all")
    public ResponseEntity<StreamingResponseBody> exportAll() {
        String filename = "coworkdata-" + FILE_TS.format(Instant.now()) + ".zip";
        StreamingResponseBody body = dbExportService::writeAllTables;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }
}
