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
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 整库流式下载（ZIP 直写响应，不落盘）。
 */
@RestController
@RequestMapping("/api/db")
@RequiredArgsConstructor
public class DbExportController {

    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final DbExportService dbExportService;

    @GetMapping("/export-by-upload-date")
    public ResponseEntity<StreamingResponseBody> exportByUploadDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }
        String filename = "coworkdata-upload-" + startDate + "_" + endDate + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Accel-Buffering", "no")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(out -> dbExportService.writeByUploadDate(out, startDate, endDate));
    }

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
