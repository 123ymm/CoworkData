package com.huawei.coworkdata.service;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 整库导出（白名单表）。
 */
public interface DbExportService {

    /**
     * 流式写入 ZIP（不落盘）：{@code manifest.json} + 每表一个 {@code {table}.json}。
     */
    void writeByUploadDate(OutputStream out, java.time.LocalDate start, java.time.LocalDate end) throws IOException;

    void writeAllTables(OutputStream out) throws IOException;
}
