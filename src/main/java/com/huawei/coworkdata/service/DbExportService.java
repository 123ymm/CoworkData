package com.huawei.coworkdata.service;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 整库导出（白名单表）。
 */
public interface DbExportService {

    /**
     * 将白名单内全部表写入 ZIP（压缩文件夹）：
     * {@code manifest.json} + 每表一个 {@code {table}.json} 数组文件。
     */
    void writeAllTables(OutputStream out) throws IOException;
}
