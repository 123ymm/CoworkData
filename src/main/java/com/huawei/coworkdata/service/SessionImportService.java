package com.huawei.coworkdata.service;

import java.util.Map;

public interface SessionImportService {

    /** 插入 Host 重写好 id 的 bundle，返回新 session id。 */
    String importBundle(Map<String, Object> bundle);
}
