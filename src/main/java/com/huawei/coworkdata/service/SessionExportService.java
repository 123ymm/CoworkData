package com.huawei.coworkdata.service;

import java.util.Map;

public interface SessionExportService {

    /**
     * 导出单会话相关表行（Host 侧组装 SQLite 用）。
     * key = 表名，value = 行列表（列名 snake_case）。
     */
    Map<String, Object> exportBundle(String sessionId);
}
