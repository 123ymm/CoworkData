package com.huawei.coworkdata.service;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 整库导出（白名单表）。
 */
public interface DbExportService {

    /**
     * 将白名单内全部表以流式 JSON 写入 {@code out}。
     * 形如：{@code {"exported_at":"...","tables":{"sessions":[...], ...}}}
     */
    void writeAllTables(OutputStream out) throws IOException;
}
