package com.blocbuy.bootstrap.fixture;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** Isolated PostgreSQL for CI, or the explicitly guarded private local test cluster. */
public final class StripeTestDatabase implements AutoCloseable {
    private final PostgreSQLContainer container;
    private final String url;
    private final String username;
    private final String password;

    public StripeTestDatabase(String database) {
        String local = System.getenv("STRIPE_TEST_DB_URL");
        if (local != null) {
            if (!local.equals(
                            "jdbc:postgresql://127.0.0.1:55439/blocbuy_stripe_test?currentSchema=domain")
                    || !(database.equals("blocbuy_stripe_test")
                            || database.equals("blocbuy_stripe_local"))) {
                throw new IllegalStateException("Refusing a non-isolated test database");
            }
            container = null;
            url = "jdbc:postgresql://127.0.0.1:55439/" + database + "?currentSchema=domain";
            username = "stripe_test";
            password = System.getenv("STRIPE_TEST_DB_PASSWORD");
        } else {
            container = new PostgreSQLContainer("postgres:18").withDatabaseName(database);
            container.start();
            url = container.getJdbcUrl() + "?currentSchema=domain";
            username = container.getUsername();
            password = container.getPassword();
        }
    }

    public String url() {
        return url;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    @Override
    public void close() {
        if (container != null) container.stop();
    }
}
