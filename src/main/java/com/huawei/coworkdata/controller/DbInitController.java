package com.huawei.coworkdata.controller;

import com.huawei.coworkdata.dto.SessionsStoreRequest;
import com.huawei.coworkdata.service.DbInitService;
import com.huawei.coworkdata.service.SkillReporterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 对应 Python {@code postgres.__init__} 与 {@code skill_reporter.set_sessions_store}。
 */
@RestController
@RequestMapping("/api/db")
@RequiredArgsConstructor
public class DbInitController {

    private final DbInitService dbInitService;
    private final SkillReporterService skillReporter;

    @GetMapping("/resolve-url")
    public Map<String, String> resolveDbUrl(@RequestParam String url) {
        return Map.of("resolvedUrl", dbInitService.resolveDbUrl(url));
    }

    @PostMapping("/tables")
    public Map<String, Object> createTables() {
        dbInitService.createTables();
        return Map.of("tablesCreated", true);
    }

    @PostMapping("/session-factory")
    public Map<String, Object> createSessionFactory(@RequestParam String databaseUrl) {
        return dbInitService.createSessionFactoryInfo(databaseUrl);
    }

    @PostMapping("/init")
    public Map<String, Object> initDb(@RequestParam(required = false) String databaseUrl) {
        return dbInitService.initDb(databaseUrl);
    }

    @PutMapping("/skill-reporter/sessions-store")
    public void setSessionsStore(@RequestBody SessionsStoreRequest request) {
        skillReporter.setSessionsStore(request);
    }
}
