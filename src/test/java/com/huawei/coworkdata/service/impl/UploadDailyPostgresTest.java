package com.huawei.coworkdata.service.impl;

import com.huawei.coworkdata.service.SessionUploadDailyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in PostgreSQL/openGauss test; creates and removes only its own isolated schema. */
@EnabledIf("databaseConfigured")
class UploadDailyPostgresTest {
    private static String databaseVariable(String suffix) {
        String prefix = System.getenv("COWORK_TEST_DATABASE_URL") != null
                ? "COWORK_TEST_DATABASE_" : "COWORK_TEST_POSTGRES_";
        return System.getenv(prefix + suffix);
    }

    static boolean databaseConfigured() {
        String url = databaseVariable("URL");
        return url != null && !url.trim().isEmpty();
    }

    @Test void concurrentUpsertTimezoneAndRollback() throws Exception {
        String schema = "upload_test_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource ds = new DriverManagerDataSource(
                databaseVariable("URL"), databaseVariable("USER"), databaseVariable("PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE SCHEMA " + schema);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            Properties properties = new Properties(); properties.setProperty("currentSchema", schema);
            ds.setConnectionProperties(properties);
            jdbc.execute("CREATE TABLE session_upload_daily (session_id VARCHAR(64), upload_date DATE, "
                    + "first_uploaded_at TIMESTAMPTZ NOT NULL, last_uploaded_at TIMESTAMPTZ NOT NULL, "
                    + "PRIMARY KEY(session_id,upload_date))");
            SessionUploadDailyService service = new SessionUploadDailyService(jdbc);
            TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
            tx.execute(s -> { service.record("s"); return null; });
            Object first = jdbc.queryForObject("SELECT first_uploaded_at FROM session_upload_daily", Object.class);
            List<Future<?>> results = new ArrayList<>();
            for (int i=0; i<12; i++) {
                results.add(workers.submit(() -> tx.execute(s -> { service.record("s"); return null; })));
            }
            for (Future<?> result : results) result.get(30, TimeUnit.SECONDS);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_daily", Integer.class));
            assertEquals(first, jdbc.queryForObject("SELECT first_uploaded_at FROM session_upload_daily", Object.class));
            assertTrue(jdbc.queryForObject("SELECT upload_date = CAST(last_uploaded_at AT TIME ZONE 'Asia/Shanghai' AS DATE) "
                    + "AND first_uploaded_at <= last_uploaded_at FROM session_upload_daily", Boolean.class));
            tx.execute(s -> { service.record("rolled-back"); s.setRollbackOnly(); return null; });
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_daily WHERE session_id='rolled-back'", Integer.class));
            jdbc.update("INSERT INTO session_upload_daily VALUES('s', DATE '2000-01-01', CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_daily WHERE session_id='s'", Integer.class));
            assertEquals(java.sql.Date.valueOf("2026-10-20"), jdbc.queryForObject(
                    "SELECT CAST(TIMESTAMPTZ '2026-10-19 16:00:00+00' AT TIME ZONE 'Asia/Shanghai' AS DATE)", java.sql.Date.class));
            // Legacy migration freezes its population and never overwrites real activity.
            jdbc.execute("CREATE TABLE sessions (id VARCHAR(64) PRIMARY KEY)");
            jdbc.update("INSERT INTO sessions VALUES('s'),('legacy-a'),('legacy-b')");
            org.springframework.jdbc.datasource.init.ResourceDatabasePopulator migration =
                    new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                            new org.springframework.core.io.FileSystemResource(
                                    "docs/migrations/20261019-backfill-legacy-upload-date.sql"));
            migration.execute(ds);
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_legacy_baseline", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_daily WHERE upload_date=DATE '2026-10-18'", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_legacy_baseline WHERE session_id='s'", Integer.class));
            jdbc.update("INSERT INTO sessions VALUES('later-untracked')");
            migration.execute(ds);
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_legacy_baseline", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_daily WHERE session_id='later-untracked'", Integer.class));
            tx.execute(status -> { service.record("legacy-a"); return null; });
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_daily WHERE session_id='legacy-a'", Integer.class));
            UploadDailyConcurrency.assertFirstInsertRace(ds);
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(30, TimeUnit.SECONDS);
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
