package com.huawei.coworkdata.service;

import java.util.Map;

public interface IdentityMigrationService {

    /**
     * 执行 V4 DDL（幂等）+ 工号 user_id → surrogate_id 数据迁移。
     */
    Map<String, Object> migrateIdentity();
}
