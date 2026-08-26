package com.huawei.coworkdata.service;

import java.util.Map;

public interface DbInitService {

    String resolveDbUrl(String url);

    void createTables();

    Map<String, Object> createSessionFactoryInfo(String databaseUrl);

    Map<String, Object> initDb(String databaseUrl);
}
