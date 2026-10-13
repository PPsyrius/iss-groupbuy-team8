package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import com.blocbuy.common.persistence.UuidTypeHandler;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PostgreSQL 18 verification of UUIDv7 outbox writes and published-only retention. */
@Testcontainers
class OutboxMapperIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static SqlSessionFactory sessions;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateAndLoadMapper() throws Exception {
        var source =
                new DriverManagerDataSource(
                        POSTGRES.getJdbcUrl()
                                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                                + "currentSchema=domain",
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA domain");
        Path migration =
                Path.of(
                        System.getProperty("basedir"),
                        "..",
                        "blocbuy-bootstrap",
                        "src",
                        "main",
                        "resources",
                        "db",
                        "migration",
                        "V26__transactional_outbox.sql");
        new ResourceDatabasePopulator(new FileSystemResource(migration)).execute(source);
        var config =
                new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setDefaultStatementTimeout(5);
        config.setMapUnderscoreToCamelCase(true);
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        String resource = "mapper/integration/OutboxMapper.xml";
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    @BeforeEach
    void clearOutbox() {
        jdbc.execute("TRUNCATE sys_outbox");
    }

    @Test
    void insertUsesGeneratedUuidV7AndRetentionDeletesOnlyPublishedRowsOlderThanCutoff() {
        UUID aggregate = UUID.randomUUID();
        try (SqlSession session = sessions.openSession(true)) {
            OutboxMapper mapper = session.getMapper(OutboxMapper.class);
            assertThat(
                            mapper.insert(
                                    new OutboxInsert(
                                            "TeamSucceeded",
                                            1,
                                            aggregate,
                                            "{\"dealId\":\"" + aggregate + "\"}",
                                            "trace-it")))
                    .isEqualTo(1);
            mapper.insert(new OutboxInsert("TeamExpired", 1, aggregate, "{}", "trace-it"));
            mapper.insert(new OutboxInsert("PaymentSucceeded", 1, aggregate, "{}", "trace-it"));
        }

        UUID oldPublished = id("TeamSucceeded");
        UUID recentPublished = id("TeamExpired");
        UUID failed = id("PaymentSucceeded");
        assertThat(oldPublished.version()).isEqualTo(7);
        Instant cutoff = Instant.parse("2026-10-12T00:00:00Z");
        jdbc.update(
                "UPDATE sys_outbox SET published_at = ? WHERE id = ?",
                Timestamp.from(cutoff.minusSeconds(1)),
                oldPublished);
        jdbc.update(
                "UPDATE sys_outbox SET published_at = ? WHERE id = ?",
                Timestamp.from(cutoff.plusSeconds(1)),
                recentPublished);
        jdbc.update(
                "UPDATE sys_outbox SET failed_at = CURRENT_TIMESTAMP, published_at = ? WHERE id = ?",
                Timestamp.from(cutoff.minusSeconds(1)),
                failed);

        try (SqlSession session = sessions.openSession(true)) {
            assertThat(session.getMapper(OutboxMapper.class).deletePublishedBefore(cutoff, 100))
                    .isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_outbox", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT id FROM sys_outbox", UUID.class))
                .containsExactlyInAnyOrder(recentPublished, failed);
    }

    @Test
    void relaySelectsOnlyDueUnpublishedRowsAndRecordsSuccessRetryAndTerminalFailure() {
        UUID aggregate = UUID.randomUUID();
        try (SqlSession session = sessions.openSession(true)) {
            OutboxMapper mapper = session.getMapper(OutboxMapper.class);
            mapper.insert(new OutboxInsert("TeamSucceeded", 1, aggregate, "{}", "trace-one"));
            mapper.insert(new OutboxInsert("TeamExpired", 1, aggregate, "{}", "trace-two"));
            mapper.insert(new OutboxInsert("PaymentSucceeded", 1, aggregate, "{}", "trace-three"));
        }

        UUID successId = id("TeamSucceeded");
        UUID retryId = id("TeamExpired");
        UUID terminalId = id("PaymentSucceeded");
        jdbc.update("UPDATE sys_outbox SET attempts = 9 WHERE id = ?", terminalId);
        jdbc.update(
                "UPDATE sys_outbox SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1 hour' WHERE id = ?",
                retryId);

        try (SqlSession session = sessions.openSession(true)) {
            OutboxMapper mapper = session.getMapper(OutboxMapper.class);
            List<OutboxEvent> due = mapper.findDue(100);
            assertThat(due).extracting(OutboxEvent::id).containsExactly(successId, terminalId);
            assertThat(due.getFirst().payloadJson()).isEqualTo("{}");
            assertThat(due.getFirst().createdAt()).isNotNull();
            assertThat(
                            mapper.recordResults(
                                    List.of(
                                            OutboxPublishResult.published(successId),
                                            OutboxPublishResult.failed(terminalId, "unavailable")),
                                    10))
                    .isEqualTo(2);
        }

        assertThat(
                        jdbc.queryForObject(
                                "SELECT published_at IS NOT NULL FROM sys_outbox WHERE id = ?",
                                Boolean.class,
                                successId))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT attempts = 10 AND failed_at IS NOT NULL FROM sys_outbox WHERE id = ?",
                                Boolean.class,
                                terminalId))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT attempts = 0 AND failed_at IS NULL FROM sys_outbox WHERE id = ?",
                                Boolean.class,
                                retryId))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT next_attempt_at > CURRENT_TIMESTAMP FROM sys_outbox WHERE id = ?",
                                Boolean.class,
                                terminalId))
                .isTrue();

        jdbc.update(
                "UPDATE sys_outbox SET next_attempt_at = CURRENT_TIMESTAMP WHERE id = ?", retryId);
        try (SqlSession session = sessions.openSession(true)) {
            assertThat(session.getMapper(OutboxMapper.class).findDue(100))
                    .extracting(OutboxEvent::id)
                    .containsExactly(retryId);
            assertThat(
                            session.getMapper(OutboxMapper.class)
                                    .recordResults(
                                            List.of(OutboxPublishResult.failed(retryId, "again")),
                                            10))
                    .isEqualTo(1);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT attempts = 1 AND failed_at IS NULL AND next_attempt_at > CURRENT_TIMESTAMP FROM sys_outbox WHERE id = ?",
                                Boolean.class,
                                retryId))
                .isTrue();

        // The retry budget is the caller's: the same row with a budget of 2 becomes terminal.
        jdbc.update(
                "UPDATE sys_outbox SET next_attempt_at = CURRENT_TIMESTAMP WHERE id = ?", retryId);
        try (SqlSession session = sessions.openSession(true)) {
            assertThat(
                            session.getMapper(OutboxMapper.class)
                                    .recordResults(
                                            List.of(OutboxPublishResult.failed(retryId, "last")),
                                            2))
                    .isEqualTo(1);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT attempts = 2 AND failed_at IS NOT NULL FROM sys_outbox WHERE id = ?",
                                Boolean.class,
                                retryId))
                .isTrue();
    }

    private static UUID id(String eventType) {
        return jdbc.queryForObject(
                "SELECT id FROM sys_outbox WHERE event_type = ?", UUID.class, eventType);
    }
}
