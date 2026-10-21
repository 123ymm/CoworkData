package com.huawei.coworkdata.service.impl;

import com.huawei.coworkdata.service.SessionUploadDailyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UploadDailyJdbcTest {
    private DriverManagerDataSource ds;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private SessionUploadDailyService service;

    @BeforeEach void setup() {
        ds = new DriverManagerDataSource("jdbc:h2:mem:daily_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
                + ";INIT=SET TIME ZONE '-05:00'", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE ALIAS statement_timestamp FOR "
                + "'com.huawei.coworkdata.service.impl.UploadTestClock.now'");
        jdbc.execute("CREATE TABLE session_upload_daily (session_id VARCHAR(64) NOT NULL, upload_date DATE NOT NULL, "
                + "first_uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL, last_uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "PRIMARY KEY(session_id, upload_date))");
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        service = new SessionUploadDailyService(jdbc);
    }

    @AfterEach void cleanup() throws Exception {
        UploadTestClock.clear();
        try (java.sql.Connection connection = ds.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    private void recordAt(String time, String sessionId) {
        UploadTestClock.set(time);
        tx.execute(status -> { service.record(sessionId); return null; });
    }

    @Test void preservesEarliestAndLatestEvenWhenWritesArriveOutOfOrder() {
        recordAt("2026-10-20T03:00:00Z", "s");
        recordAt("2026-10-20T01:00:00Z", "s");
        recordAt("2026-10-20T05:00:00Z", "s");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_daily", Integer.class));
        assertEquals(Instant.parse("2026-10-20T01:00:00Z"), jdbc.queryForObject(
                "SELECT first_uploaded_at FROM session_upload_daily", Timestamp.class).toInstant());
        assertEquals(Instant.parse("2026-10-20T05:00:00Z"), jdbc.queryForObject(
                "SELECT last_uploaded_at FROM session_upload_daily", Timestamp.class).toInstant());

        UploadTestClock.set("2026-10-20T06:00:00Z");
        tx.execute(status -> {
            service.record("s");
            service.record("rolled-back");
            status.setRollbackOnly();
            return null;
        });
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_daily", Integer.class));
        assertEquals(Instant.parse("2026-10-20T05:00:00Z"), jdbc.queryForObject(
                "SELECT last_uploaded_at FROM session_upload_daily", Timestamp.class).toInstant());
    }

    @Test void shanghaiMidnightIsIndependentOfJvmAndDatabaseTimezones() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            // The datasource INIT sets every connection to UTC-05:00.
            recordAt("2026-10-19T15:59:59Z", "s");
            recordAt("2026-10-19T16:00:00Z", "s");
            assertEquals(java.util.Arrays.asList("2026-10-19", "2026-10-20"), jdbc.query(
                    "SELECT upload_date FROM session_upload_daily ORDER BY upload_date",
                    (rs, row) -> rs.getString(1)));
            assertEquals(Instant.parse("2026-10-19T16:00:00Z"), jdbc.queryForObject(
                    "SELECT last_uploaded_at FROM session_upload_daily WHERE upload_date=DATE '2026-10-20'",
                    Timestamp.class).toInstant());
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test void concurrentFirstInsertsRecoverSavepointAndCommitBusinessWrites() throws Exception {
        UploadDailyConcurrency.assertFirstInsertRace(ds);
    }

    @Test void nonUniqueConstraintFailureIsNotSwallowedAndRollsBackBusinessWrites() {
        jdbc.execute("CREATE TABLE business_probe (id INTEGER PRIMARY KEY)");
        jdbc.execute("ALTER TABLE session_upload_daily ADD CONSTRAINT reject_session CHECK(session_id <> 'rejected')");
        assertThrows(DataIntegrityViolationException.class, () -> tx.execute(status -> {
            jdbc.update("INSERT INTO business_probe VALUES(1)");
            service.record("rejected");
            return null;
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM business_probe", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_daily", Integer.class));
    }

    @Test void refusesWritesWithoutATransaction() {
        assertThrows(IllegalStateException.class, () -> service.record("s"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM session_upload_daily", Integer.class));
    }
}
