package com.blocbuy.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PostgreSQL smoke test (D20): proves the pinned driver can talk to a real PostgreSQL 18 and that
 * the {@code domain} schema convention works. Runs in {@code verify} via failsafe; requires Docker.
 */
@Testcontainers
class PgSmokeIT {

    @Container
    @SuppressWarnings("resource") // container lifecycle managed by Testcontainers
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Test
    void connectsAndCreatesDomainSchema() throws Exception {
        try (Connection conn =
                        java.sql.DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS domain");
            try (ResultSet rs =
                    stmt.executeQuery(
                            "SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'domain'")) {
                assertThat(rs.next()).as("domain schema exists").isTrue();
            }
        }
    }
}
