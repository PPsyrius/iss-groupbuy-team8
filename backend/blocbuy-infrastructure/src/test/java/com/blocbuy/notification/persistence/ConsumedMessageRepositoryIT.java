package com.blocbuy.notification.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.common.persistence.UuidTypeHandler;
import com.blocbuy.common.util.UuidV7;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ConsumedMessageRepositoryIT {
    private static final String CONSUMER = "notification";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static SqlSessionFactory sessions;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void configure() throws Exception {
        String url = POSTGRES.getJdbcUrl();
        var dataSource =
                new DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=domain",
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA domain");
        var migration =
                Path.of(
                        System.getProperty("basedir"),
                        "..",
                        "blocbuy-bootstrap",
                        "src",
                        "main",
                        "resources",
                        "db",
                        "migration",
                        "V28__sys_consumed_message.sql");
        new ResourceDatabasePopulator(new FileSystemResource(migration)).execute(dataSource);
        var config =
                new Configuration(
                        new Environment("test", new JdbcTransactionFactory(), dataSource));
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        String resource = "mapper/notification/SysConsumedMessageMapper.xml";
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    @Test
    void firstMarkWinsAndRepeatsAreNoopsPerConsumer() {
        UUID event = UuidV7.next();
        try (var session = sessions.openSession()) {
            var ledger = repository(session);
            assertThat(ledger.markConsumed(event, CONSUMER)).isTrue();
            assertThat(ledger.markConsumed(event, CONSUMER)).isFalse();
            // Another consumer of the same event keeps its own ledger entry.
            assertThat(ledger.markConsumed(event, "audit")).isTrue();
            session.commit();
        }
        try (var session = sessions.openSession()) {
            assertThat(repository(session).markConsumed(event, CONSUMER)).isFalse();
        }
        assertThat(count(event)).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT consumed_at IS NOT NULL FROM sys_consumed_message"
                                        + " WHERE event_id = ? AND consumer = ?",
                                Boolean.class,
                                event,
                                CONSUMER))
                .isTrue();
    }

    @Test
    void rolledBackMarkLeavesTheEventUnconsumed() {
        UUID event = UuidV7.next();
        try (var session = sessions.openSession()) {
            assertThat(repository(session).markConsumed(event, CONSUMER)).isTrue();
            session.rollback();
        }
        assertThat(count(event)).isZero();
        try (var session = sessions.openSession()) {
            assertThat(repository(session).markConsumed(event, CONSUMER)).isTrue();
            session.commit();
        }
        assertThat(count(event)).isEqualTo(1);
    }

    @Test
    void concurrentFirstDeliveriesInsertExactlyOneMarker() throws Exception {
        UUID event = UuidV7.next();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> mark =
                    () -> {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test start timed out");
                        }
                        try (var session = sessions.openSession()) {
                            boolean inserted = repository(session).markConsumed(event, CONSUMER);
                            session.commit();
                            return inserted;
                        }
                    };
            var first = executor.submit(mark);
            var second = executor.submit(mark);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(count(event)).isEqualTo(1);
    }

    private static ConsumedMessageRepositoryImpl repository(SqlSession session) {
        return new ConsumedMessageRepositoryImpl(session.getMapper(SysConsumedMessageMapper.class));
    }

    private static long count(UUID event) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM sys_consumed_message WHERE event_id = ?", Long.class, event);
    }
}
