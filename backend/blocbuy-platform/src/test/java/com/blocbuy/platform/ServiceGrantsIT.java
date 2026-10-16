package com.blocbuy.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Per-service grants (#455 step A4, backend ADR 0008). Each service database is prepared exactly
 * like {@code infra/swe5001/db-init/init.sh} does on RDS (owner and runtime role, default
 * privileges that grant every new table to the runtime role), migrated as the owner with the full
 * stream plus {@code db/swe5001}, and then probed as the runtime role.
 */
@Testcontainers
class ServiceGrantsIT {

    private static final List<String> SERVICES = List.of("identity", "commerce", "notification");
    private static final String OWNER_PASSWORD = "owner-test-only";
    private static final String APP_PASSWORD = "app-test-only";
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Container
    @SuppressWarnings("resource") // Testcontainers owns this disposable database.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @BeforeAll
    static void prepareServiceDatabases() throws SQLException {
        for (String service : SERVICES) {
            initDatabase(service);
            migrate(service);
        }
    }

    @Test
    void commerceRuntimeRoleIsDeniedIdentityTables() throws SQLException {
        assertDenied("commerce", "select count(*) from domain.sys_user");
        assertDenied("commerce", "select count(*) from domain.rel_user_role");
        assertAllowed("commerce", "select count(*) from domain.biz_product");
        assertAllowed("commerce", "select count(*) from domain.sys_scheduler_lock");
        assertDenied("commerce", "select count(*) from domain.biz_notification");
        assertDenied("commerce", "select count(*) from domain.sys_consumed_message");
        assertAllowed("commerce", "select nextval('domain.seq_biz_product_product_no')");
    }

    @Test
    void notificationRuntimeRoleIsDeniedCommerceAndIdentityTables() throws SQLException {
        assertAllowed("notification", "select count(*) from domain.biz_notification");
        assertAllowed(
                "notification",
                """
                insert into domain.biz_notification (event_id, user_id, reference_id, notification_type, title, content)
                values (uuidv7(), uuidv7(), uuidv7(), 'TeamSucceeded', 'Team succeeded', 'Your team succeeded.')
                returning id
                """);
        // #446 consumed-message ledger; executeQuery needs a result set, hence RETURNING.
        assertAllowed(
                "notification",
                "insert into domain.sys_consumed_message (event_id, consumer)"
                        + " values (uuidv7(), 'notification') returning event_id");
        assertAllowed("notification", "select count(*) from domain.sys_consumed_message");
        assertDenied("notification", "select count(*) from domain.biz_product");
        assertDenied("notification", "select count(*) from domain.biz_order");
        assertDenied("notification", "select count(*) from domain.sys_user");
        assertDenied("notification", "select nextval('domain.seq_biz_product_product_no')");
    }

    @Test
    void identityRuntimeRoleOwnsOnlyIdentityTables() throws SQLException {
        assertAllowed("identity", "select count(*) from domain.sys_user");
        assertAllowed("identity", "select count(*) from domain.rel_role_permission");
        assertDenied("identity", "select count(*) from domain.biz_order");
        assertDenied("identity", "select count(*) from domain.biz_notification");
        assertDenied("identity", "select count(*) from domain.sys_consumed_message");
        assertDenied("identity", "select count(*) from domain.sys_scheduler_lock");
    }

    @Test
    void noRuntimeRoleTouchesTheMigrationHistory() throws SQLException {
        for (String service : SERVICES) {
            assertDenied(service, "select count(*) from domain.flyway_schema_history");
        }
    }

    /**
     * A table added by a later migration is granted to every runtime role by db-init's default
     * privileges; the next migrate (every service start) takes it back from the non-owners.
     */
    @Test
    void tablesAddedLaterAreReconciledOnTheNextMigrate() throws SQLException {
        for (String service : SERVICES) {
            asOwner(service, "create table domain.biz_later_probe (id int)");
        }
        assertAllowed("identity", "select count(*) from domain.biz_later_probe");

        for (String service : SERVICES) {
            migrate(service);
        }

        assertDenied("identity", "select count(*) from domain.biz_later_probe");
        assertDenied("notification", "select count(*) from domain.biz_later_probe");
        assertAllowed("commerce", "select count(*) from domain.biz_later_probe");
    }

    /** Mirrors infra/swe5001/db-init/init.sh for one database. */
    private static void initDatabase(String service) throws SQLException {
        String owner = owner(service);
        String app = app(service);
        try (Connection admin =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                Statement sql = admin.createStatement()) {
            sql.execute("create role " + owner + " login password '" + OWNER_PASSWORD + "'");
            sql.execute("create role " + app + " login password '" + APP_PASSWORD + "'");
            sql.execute("grant " + owner + " to current_user");
            sql.execute("create database " + service + " owner " + owner);
            sql.execute("revoke all on database " + service + " from public");
            sql.execute("grant connect on database " + service + " to " + app);
        }
        try (Connection admin =
                        DriverManager.getConnection(
                                url(service), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement sql = admin.createStatement()) {
            sql.execute("create schema domain authorization " + owner);
            sql.execute("revoke all on schema public from public");
            sql.execute("grant usage on schema domain to " + app);
            sql.execute(
                    "alter default privileges for role "
                            + owner
                            + " in schema domain grant select, insert, update, delete on tables to "
                            + app);
            sql.execute(
                    "alter default privileges for role "
                            + owner
                            + " in schema domain grant usage, select on sequences to "
                            + app);
        }
    }

    /** What a service start does: the full stream and the grants callback, as the owner. */
    private static void migrate(String service) {
        Flyway.configure()
                .dataSource(url(service), owner(service), OWNER_PASSWORD)
                .locations("classpath:db/migration", "classpath:db/swe5001")
                .defaultSchema("domain")
                .placeholders(Map.of("service", service, "approle", app(service)))
                // V23 builds an index concurrently (as in application.yml)
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .load()
                .migrate();
    }

    private static void asOwner(String service, String statement) throws SQLException {
        try (Connection owner =
                        DriverManager.getConnection(url(service), owner(service), OWNER_PASSWORD);
                Statement sql = owner.createStatement()) {
            sql.execute(statement);
        }
    }

    private static void assertAllowed(String service, String query) {
        assertThatCode(() -> asApp(service, query)).doesNotThrowAnyException();
    }

    private static void assertDenied(String service, String query) {
        assertThatThrownBy(() -> asApp(service, query))
                .isInstanceOf(SQLException.class)
                .satisfies(
                        failure ->
                                assertThat(((SQLException) failure).getSQLState())
                                        .as("%s: %s", app(service), query)
                                        .isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    private static void asApp(String service, String query) throws SQLException {
        try (Connection app =
                        DriverManager.getConnection(url(service), app(service), APP_PASSWORD);
                Statement sql = app.createStatement()) {
            sql.executeQuery(query).close();
        }
    }

    private static String url(String database) {
        return "jdbc:postgresql://"
                + POSTGRES.getHost()
                + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                + "/"
                + database;
    }

    private static String owner(String service) {
        return "blocbuy_" + service + "_owner";
    }

    private static String app(String service) {
        return "blocbuy_" + service + "_app";
    }
}
