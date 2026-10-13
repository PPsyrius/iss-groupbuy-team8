package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Rehearses the deployed V26 outbox schema upgrading to notification persistence without repair.
 */
@Testcontainers
class NotificationMigrationIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Test
    void upgradesV26ToV27PreservingExistingOutboxFacts() {
        String url = POSTGRES.getJdbcUrl();
        var dataSource =
                new DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=domain",
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword());
        var configuration =
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .defaultSchema("domain")
                        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"));
        var v26 = configuration.target("26").load();
        v26.migrate();
        assertThat(v26.info().current().getVersion().getVersion()).isEqualTo("26");
        var jdbc = new JdbcTemplate(dataSource);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT to_regclass('domain.biz_notification')::text",
                                String.class))
                .isNull();
        var eventId =
                jdbc.queryForObject(
                        """
                INSERT INTO sys_outbox (event_type, event_version, aggregate_id, payload, trace_id)
                VALUES ('TeamSucceeded', 1, uuidv7(), '{"size":2}', 'migration-test') RETURNING id
                """,
                        java.util.UUID.class);

        var v27 = configuration.target("27").load();
        assertThat(v27.migrate().migrationsExecuted).isEqualTo(1);
        v27.validate();
        assertThat(v27.info().current().getVersion().getVersion()).isEqualTo("27");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT payload->>'size' FROM sys_outbox WHERE id = ?",
                                String.class,
                                eventId))
                .isEqualTo("2");
        jdbc.update(
                """
                INSERT INTO biz_notification (event_id, user_id, reference_id, notification_type, title, content)
                VALUES (?, uuidv7(), uuidv7(), 'TeamSucceeded', 'Team succeeded', 'Your team succeeded.')
                """,
                eventId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_notification WHERE event_id = ?",
                                Long.class,
                                eventId))
                .isEqualTo(1);
        assertThat(v27.migrate().migrationsExecuted).isZero();
    }
}
