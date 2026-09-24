package com.blocbuy.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.blocbuy.common.util.UuidV7;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The lease SQL against real PostgreSQL 18: takeover, single winner under a race, and the 3 s
 * statement timeout. Calls go through {@link SqlSessionTemplate}, as in production, so failures
 * surface as Spring {@link DataAccessException}s exactly as {@code LeasedJobRunner} sees them.
 */
@Testcontainers
class SchedulerLeaseRepositoryIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final Duration LEASE = Duration.ofMinutes(2);

    private static DriverManagerDataSource source;
    private static JdbcTemplate jdbc;
    private static SchedulerLeaseRepositoryImpl leases;

    /** V13 alone: the lease table references nothing else. */
    @BeforeAll
    static void migrate() throws Exception {
        String url = POSTGRES.getJdbcUrl();
        source =
                new DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=domain",
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA domain");
        Path migrations =
                Path.of(
                        System.getProperty("basedir"),
                        "..",
                        "blocbuy-bootstrap",
                        "src",
                        "main",
                        "resources",
                        "db",
                        "migration");
        new ResourceDatabasePopulator(
                        new FileSystemResource(migrations.resolve("V13__scheduler_lock.sql")))
                .execute(source);
        var config =
                new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        String resource = "mapper/common/SysSchedulerLockMapper.xml";
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
        }
        var template = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        leases = new SchedulerLeaseRepositoryImpl(template.getMapper(SysSchedulerLockMapper.class));
    }

    @Test
    void firstAcquireInsertsTheLeaseRow() {
        String key = key();

        assertThat(leases.tryAcquire(key, "a", LEASE)).isTrue();

        assertThat(owner(key)).isEqualTo("a");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT locked_until > clock_timestamp() + interval '100 seconds'"
                                        + " FROM sys_scheduler_lock WHERE lock_key = ?",
                                Boolean.class,
                                key))
                .isTrue();
    }

    @Test
    void aLiveLeaseKeepsOtherOwnersOutButLetsItsOwnerBackIn() {
        String key = key();
        assertThat(leases.tryAcquire(key, "a", LEASE)).isTrue();

        assertThat(leases.tryAcquire(key, "b", LEASE)).isFalse();
        assertThat(leases.tryAcquire(key, "a", LEASE)).isTrue();

        assertThat(owner(key)).isEqualTo("a");
        assertThat(rows(key)).isEqualTo(1);
    }

    @Test
    void anExpiredLeaseIsTakenOverAndTheOldOwnerCannotReleaseIt() {
        String key = key();
        assertThat(leases.tryAcquire(key, "a", LEASE)).isTrue();
        jdbc.update(
                "UPDATE sys_scheduler_lock SET locked_until = clock_timestamp() - interval '1 second'"
                        + " WHERE lock_key = ?",
                key);

        assertThat(leases.tryAcquire(key, "b", LEASE)).isTrue();

        assertThat(owner(key)).isEqualTo("b");
        assertThat(leases.release(key, "a")).isFalse();
        assertThat(leases.tryAcquire(key, "a", LEASE)).isFalse();
        assertThat(rows(key)).isEqualTo(1);
    }

    @Test
    void releaseLetsTheNextOwnerInWithoutWaitingForTheLease() {
        String key = key();
        assertThat(leases.tryAcquire(key, "a", LEASE)).isTrue();

        assertThat(leases.release(key, "a")).isTrue();

        assertThat(leases.tryAcquire(key, "b", LEASE)).isTrue();
    }

    @Test
    void renewalExtendsOnlyAStillLiveOwnedLease() {
        String key = key();
        assertThat(leases.tryAcquire(key, "a", LEASE)).isTrue();

        assertThat(leases.renew(key, "a", Duration.ofMinutes(3))).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT locked_until > clock_timestamp() + interval '150 seconds'"
                                        + " FROM sys_scheduler_lock WHERE lock_key = ?",
                                Boolean.class,
                                key))
                .isTrue();

        jdbc.update(
                "UPDATE sys_scheduler_lock SET locked_until = clock_timestamp() - interval '1 second'"
                        + " WHERE lock_key = ?",
                key);
        assertThat(leases.renew(key, "a", LEASE)).isFalse();
    }

    @Test
    void contendedReacquireStartsItsDeadlineAfterTheRowLockIsObtained() throws Exception {
        String key = key();
        assertThat(leases.tryAcquire(key, "a", LEASE)).isTrue();
        try (Connection holder = source.getConnection();
                var pool = Executors.newSingleThreadExecutor()) {
            holder.setAutoCommit(false);
            int holderPid;
            try (var lock =
                    holder.prepareStatement(
                            "SELECT pg_backend_pid() FROM sys_scheduler_lock"
                                    + " WHERE lock_key = ? FOR UPDATE")) {
                lock.setString(1, key);
                try (var row = lock.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    holderPid = row.getInt(1);
                }
            }
            Future<Boolean> reacquired =
                    pool.submit(() -> leases.tryAcquire(key, "a", Duration.ofSeconds(5)));

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS (SELECT 1 FROM pg_stat_activity"
                                    + " WHERE wait_event_type = 'Lock'"
                                    + " AND ? = ANY(pg_blocking_pids(pid))"
                                    + " AND query LIKE '%INSERT INTO sys_scheduler_lock%')",
                            Boolean.class, holderPid))) {
                assertThat(reacquired.isDone()).as("acquire must wait for the held row").isFalse();
                if (System.nanoTime() >= deadline) {
                    throw new AssertionError("Timed out waiting for the lease-row lock contention");
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            // Hold the row lock beyond the old pre-lock deadline calculation, on the DB clock.
            try (var delay = holder.createStatement()) {
                delay.execute("SELECT pg_sleep(1.5)");
            }
            holder.rollback();

            assertThat(reacquired.get()).isTrue();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT locked_until > clock_timestamp() + interval '4 seconds'"
                                        + " FROM sys_scheduler_lock WHERE lock_key = ?",
                                Boolean.class,
                                key))
                .isTrue();
    }

    @Test
    void ofManyRacingOwnersExactlyOneWins() throws Exception {
        String key = key();
        int racers = 8;
        var start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(racers)) {
            for (int i = 0; i < racers; i++) {
                String owner = "racer-" + i;
                results.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return leases.tryAcquire(key, owner, LEASE);
                                }));
            }
            start.countDown();
            int winners = 0;
            for (Future<Boolean> result : results) {
                winners += result.get() ? 1 : 0;
            }
            assertThat(winners).isEqualTo(1);
        }
        assertThat(rows(key)).isEqualTo(1);
    }

    /**
     * R6: a session sitting on the lease row must cost one failed run, not the scheduler thread.
     */
    @Test
    void aLongHeldRowLockFailsTheAcquireAfterTheStatementTimeout() throws Exception {
        String key = key();
        assertThat(leases.tryAcquire(key, "a", LEASE)).isTrue();
        try (Connection holder = source.getConnection()) {
            holder.setAutoCommit(false);
            try (var lock =
                    holder.prepareStatement(
                            "SELECT 1 FROM sys_scheduler_lock WHERE lock_key = ? FOR UPDATE")) {
                lock.setString(1, key);
                lock.executeQuery().close();
            }

            long started = System.nanoTime();
            assertTimeoutPreemptively(
                    Duration.ofSeconds(10),
                    () ->
                            assertThatThrownBy(() -> leases.tryAcquire(key, "a", LEASE))
                                    .isInstanceOf(DataAccessException.class)
                                    .rootCause()
                                    .isInstanceOf(SQLException.class)
                                    .extracting(e -> ((SQLException) e).getSQLState())
                                    .isEqualTo("57014"));
            assertThat(Duration.ofNanos(System.nanoTime() - started))
                    .isGreaterThanOrEqualTo(Duration.ofMillis(2500));

            holder.rollback();
        }

        assertThat(leases.tryAcquire(key, "a", LEASE)).isTrue();
    }

    private static String key() {
        return "it-" + UuidV7.next();
    }

    private static String owner(String key) {
        return jdbc.queryForObject(
                "SELECT owner FROM sys_scheduler_lock WHERE lock_key = ?", String.class, key);
    }

    private static int rows(String key) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM sys_scheduler_lock WHERE lock_key = ?", Integer.class, key);
    }
}
