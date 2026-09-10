package com.huawei.coworkdata.service;

import java.util.Map;

public interface DbInitService {

    String resolveDbUrl(String url);

    void createTables();

    Map<String, Object> createSessionFactoryInfo(String databaseUrl);

    Map<String, Object> initDb(String databaseUrl);

    /**
     * 幂等执行 classpath:db/migration/V2…V6（存量库补列/DEFAULT）。
     * 不改业务数据、不做身份映射。
     */
    Map<String, Object> migrateSchema();
}
