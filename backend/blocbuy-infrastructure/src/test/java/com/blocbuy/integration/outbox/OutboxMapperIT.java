package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.common.persistence.UuidTypeHandler;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
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
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        String resource = "mapper/integration/OutboxMapper.xml";
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        sessions = new SqlSessionFactoryBuilder().build(config);
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
        jdbc.update("UPDATE sys_outbox SET failed_at = CURRENT_TIMESTAMP WHERE id = ?", failed);

        try (SqlSession session = sessions.openSession(true)) {
            assertThat(session.getMapper(OutboxMapper.class).deletePublishedBefore(cutoff, 100))
                    .isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_outbox", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT id FROM sys_outbox", UUID.class))
                .containsExactlyInAnyOrder(recentPublished, failed);
    }

    private static UUID id(String eventType) {
        return jdbc.queryForObject(
                "SELECT id FROM sys_outbox WHERE event_type = ?", UUID.class, eventType);
    }
}
