package com.huawei.coworkdata.service.impl;

import com.huawei.coworkdata.service.DbInitService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DbInitServiceImpl implements DbInitService {

    private final DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String configuredUrl;

    @Override
    public String resolveDbUrl(String url) {
        if ("sqlite".equals(url) || "sqlite://".equals(url)) {
            String dbPath = Paths.get(System.getProperty("user.dir"), "data", "ipmc-dev.db").toString();
            return "sqlite+aiosqlite:///" + dbPath.replace('\\', '/');
        }
        if (url.startsWith("sqlite:///") && !url.contains("+aiosqlite")) {
            return "sqlite+aiosqlite:///" + url.substring("sqlite:///".length());
        }
        if (url.startsWith("postgresql://") && !url.contains("+asyncpg")) {
            return "postgresql+asyncpg://" + url.substring("postgresql://".length());
        }
        return url;
    }

    @Override
    public void createTables() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema.sql"));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }

    @Override
    public Map<String, Object> createSessionFactoryInfo(String databaseUrl) {
        String resolved = resolveDbUrl(databaseUrl);
        Map<String, Object> info = new HashMap<>();
        info.put("resolvedUrl", resolved);
        info.put("driverHint", resolved.contains("postgresql") ? "postgresql" : "sqlite");
        return info;
    }

    @Override
    public Map<String, Object> initDb(String databaseUrl) {
        String resolved = databaseUrl != null && !databaseUrl.trim().isEmpty()
                ? resolveDbUrl(databaseUrl)
                : configuredUrl;
        createTables();
        Map<String, Object> result = new HashMap<>();
        result.put("resolvedUrl", resolved);
        result.put("tablesCreated", true);
        return result;
    }

    @Override
    public Map<String, Object> migrateSchema() {
        // continueOnError：列已存在 / DEFAULT 已设时部分语句会失败，整体仍应继续
        String[] scripts = {
                "db/migration/V2__sessions_upload_and_soft_delete.sql",
                "db/migration/V3__sessions_user_prompt_default.sql",
                "db/migration/V4__surrogate_source_cowork.sql",
                "db/migration/V5__sessions_title.sql",
                "db/migration/V6__tasks_not_null_defaults.sql",
        };
        java.util.List<String> applied = new java.util.ArrayList<>();
        for (String path : scripts) {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource(path));
            populator.setContinueOnError(true);
            populator.setIgnoreFailedDrops(true);
            populator.execute(dataSource);
            applied.add(path);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("applied", applied);
        result.put("ok", true);
        return result;
    }
}
