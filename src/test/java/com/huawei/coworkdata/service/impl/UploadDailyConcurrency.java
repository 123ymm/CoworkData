package com.huawei.coworkdata.service.impl;

import com.huawei.coworkdata.service.SessionUploadDailyService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Force both first UPDATEs to return zero; all SQL still runs on real connections. */
final class UploadDailyConcurrency {
    static void assertFirstInsertRace(DataSource source) throws Exception {
        CyclicBarrier missingRows = new CyclicBarrier(2);
        AtomicInteger emptyUpdates = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger savepointRollbacks = new AtomicInteger();
        DataSource coordinated = new DelegatingDataSource(source) {
            @Override public Connection getConnection() throws SQLException {
                Connection actual = super.getConnection();
                return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                            Object value = invoke(actual, method, args);
                            if ("rollback".equals(method.getName()) && args != null
                                    && args.length == 1 && args[0] instanceof Savepoint) {
                                savepointRollbacks.incrementAndGet();
                            }
                            if (!"prepareStatement".equals(method.getName())) return value;
                            String sql = (String) args[0];
                            PreparedStatement statement = (PreparedStatement) value;
                            return Proxy.newProxyInstance(getClass().getClassLoader(),
                                    new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                                        Object result;
                                        try {
                                            result = invoke(statement, m, a);
                                        } catch (SQLException error) {
                                            if ("23505".equals(error.getSQLState())) conflicts.incrementAndGet();
                                            throw error;
                                        }
                                        if ("executeUpdate".equals(m.getName())
                                                && sql.startsWith("UPDATE session_upload_daily")
                                                && Integer.valueOf(0).equals(result)
                                                && emptyUpdates.getAndIncrement() < 2) {
                                            try {
                                                missingRows.await(10, TimeUnit.SECONDS);
                                            } catch (Exception error) {
                                                throw new SQLException("Could not coordinate first uploads", error);
                                            }
                                        }
                                        return result;
                                    });
                        });
            }
        };
        JdbcTemplate jdbc = new JdbcTemplate(coordinated);
        jdbc.execute("CREATE TABLE upload_tx_probe (id VARCHAR(64) PRIMARY KEY)");
        SessionUploadDailyService service = new SessionUploadDailyService(jdbc);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(coordinated));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> write(tx, jdbc, service, "first"));
            Future<?> second = workers.submit(() -> write(tx, jdbc, service, "second"));
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
            assertEquals(1, conflicts.get(), "Both inserts must actually race on the unique key");
            assertEquals(1, savepointRollbacks.get(), "The failed insert must restore its savepoint");
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM session_upload_daily WHERE session_id='first-insert-race'", Integer.class));
            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM upload_tx_probe", Integer.class),
                    "Writes before and after the recovered conflict must commit in both transactions");
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private static void write(TransactionTemplate tx, JdbcTemplate jdbc, SessionUploadDailyService service, String id) {
        tx.execute(status -> {
            jdbc.update("INSERT INTO upload_tx_probe VALUES(?)", id + "-before");
            service.record("first-insert-race");
            jdbc.update("INSERT INTO upload_tx_probe VALUES(?)", id + "-after");
            return null;
        });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
