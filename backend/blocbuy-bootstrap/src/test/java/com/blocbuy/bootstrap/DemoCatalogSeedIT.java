package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Local-only repeatable seed makes every demo listing startable without changing EC2 data. */
@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1"
        })
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoCatalogSeedIT {
    @Container
    @SuppressWarnings("resource") // Testcontainers owns this disposable database.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add(
                "spring.datasource.url",
                () -> {
                    String url = POSTGRES.getJdbcUrl();
                    return url + (url.contains("?") ? "&" : "?") + "currentSchema=domain";
                });
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void everyDemoProductHasCurrentPolicyAndTermsAndSeedCanRunAgain() {
        assertReady();
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/R__demo_catalog_seed.sql"))
                .execute(dataSource);
        assertReady();
    }

    private void assertReady() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_group_deal WHERE deal_no LIKE 'V4-EXPIRY-%'",
                                Integer.class))
                .isZero();
        Integer ready =
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM biz_product product
                        JOIN biz_team_price_policy policy ON policy.product_id = product.id
                        WHERE product.product_no LIKE 'DEMO-P-%'
                          AND product.status = 'on_sale'
                          AND product.stock > 0
                          AND product.team_target_size >= 2
                          AND product.team_lifetime_seconds BETWEEN 2400 AND 86400
                          AND policy.version = 1
                          AND policy.amount > 0
                          AND policy.amount < product.original_price
                          AND policy.valid_from <= CURRENT_TIMESTAMP
                          AND policy.valid_until > CURRENT_TIMESTAMP + INTERVAL '15 minutes'
                        """,
                        Integer.class);
        assertThat(ready).isEqualTo(10);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_team_price_policy WHERE version = 1",
                                Integer.class))
                .isEqualTo(10);
    }
}
