package com.huawei.coworkdata.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DbInitService {

    private final DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String configuredUrl;

    public String resolveDbUrl(String url) {
        if ("sqlite".equals(url) || "sqlite://".equals(url)) {
            Path dbPath = Path.of(System.getProperty("user.dir"), "data", "ipmc-dev.db");
            return "sqlite+aiosqlite:///" + dbPath.toString().replace('\\', '/');
        }
        if (url.startsWith("sqlite:///") && !url.contains("+aiosqlite")) {
            return "sqlite+aiosqlite:///" + url.substring("sqlite:///".length());
        }
        if (url.startsWith("postgresql://") && !url.contains("+asyncpg")) {
            return "postgresql+asyncpg://" + url.substring("postgresql://".length());
        }
        return url;
    }

    public void createTables() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema.sql"));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }

    public Map<String, Object> createSessionFactoryInfo(String databaseUrl) {
        String resolved = resolveDbUrl(databaseUrl);
        Map<String, Object> info = new HashMap<>();
        info.put("resolvedUrl", resolved);
        info.put("driverHint", resolved.contains("postgresql") ? "postgresql" : "sqlite");
        return info;
    }

    public Map<String, Object> initDb(String databaseUrl) {
        String resolved = databaseUrl != null && !databaseUrl.isBlank()
                ? resolveDbUrl(databaseUrl)
                : configuredUrl;
        createTables();
        Map<String, Object> result = new HashMap<>();
        result.put("resolvedUrl", resolved);
        result.put("tablesCreated", true);
        return result;
    }
}
