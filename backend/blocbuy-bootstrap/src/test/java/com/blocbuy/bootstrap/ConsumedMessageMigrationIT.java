package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Rehearses V27 (notification persistence) upgrading to the V28 consumed-message ledger (#446). */
@Testcontainers
class ConsumedMessageMigrationIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Test
    void upgradesV27ToV28PreservingNotificationsAndEnforcingTheLedgerKey() {
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
        configuration.target("27").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT to_regclass('domain.sys_consumed_message')::text",
                                String.class))
                .isNull();
        UUID eventId = UUID.fromString("01990000-0000-7000-8000-0000000000aa");
        jdbc.update(
                """
                INSERT INTO biz_notification (event_id, user_id, reference_id, notification_type, title, content)
                VALUES (?, uuidv7(), uuidv7(), 'TeamSucceeded', 'Team succeeded', 'Your team succeeded.')
                """,
                eventId);

        var v28 = configuration.target("28").load();
        assertThat(v28.migrate().migrationsExecuted).isEqualTo(1);
        v28.validate();
        assertThat(v28.info().current().getVersion().getVersion()).isEqualTo("28");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_notification WHERE event_id = ?",
                                Long.class,
                                eventId))
                .isEqualTo(1);
        String insert =
                "INSERT INTO sys_consumed_message (event_id, consumer) VALUES (?, 'notification')"
                        + " ON CONFLICT (event_id, consumer) DO NOTHING";
        assertThat(jdbc.update(insert, eventId)).isEqualTo(1);
        assertThat(jdbc.update(insert, eventId)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT conname FROM pg_constraint"
                                        + " WHERE conrelid = 'domain.sys_consumed_message'::regclass"
                                        + " AND contype = 'p'",
                                String.class))
                .isEqualTo("pk_sys_consumed_message");
        // Team DDL rule: structural constraints only, no foreign keys or CHECK constraints.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pg_constraint WHERE conrelid ="
                                        + " 'domain.sys_consumed_message'::regclass AND contype IN"
                                        + " ('f', 'c')",
                                Long.class))
                .isZero();
        assertThat(v28.migrate().migrationsExecuted).isZero();
    }
}
