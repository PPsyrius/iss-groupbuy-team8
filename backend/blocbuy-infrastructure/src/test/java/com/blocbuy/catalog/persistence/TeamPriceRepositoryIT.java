package com.blocbuy.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.catalog.pricing.DualPricingStrategy;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import com.blocbuy.common.persistence.UuidTypeHandler;
import com.blocbuy.common.util.UuidV7;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.exceptions.PersistenceException;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real PostgreSQL 18 migrations, MyBatis round trips, rollback, uniqueness and product lock
 * protocol.
 */
@Testcontainers
class TeamPriceRepositoryIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static SqlSessionFactory sessions;
    private static JdbcTemplate jdbc;
    private UUID productId;
    private UUID ownerId;

    @BeforeAll
    static void migrateAndConfigureMyBatis() throws Exception {
        String url = POSTGRES.getJdbcUrl();
        var dataSource =
                new DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=domain",
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
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
        var scripts =
                new ResourceDatabasePopulator(
                        // V1 is comment-only; Spring's script runner rejects an empty SQL script.
                        new FileSystemResource(migrations.resolve("V2__payment.sql")),
                        new FileSystemResource(migrations.resolve("V3__identity.sql")),
                        new FileSystemResource(migrations.resolve("V4__catalog.sql")),
                        new FileSystemResource(migrations.resolve("V5__groupbuy.sql")),
                        new FileSystemResource(migrations.resolve("V6__catalog_team_price.sql")));
        scripts.execute(dataSource);
        var config =
                new Configuration(
                        new Environment("test", new JdbcTransactionFactory(), dataSource));
        config.setDefaultStatementTimeout(5);
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        loadMapper(config, "mapper/catalog/BizProductMapper.xml");
        loadMapper(config, "mapper/catalog/TeamPriceMapper.xml");
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    private static void loadMapper(Configuration config, String resource) throws Exception {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
    }

    @BeforeEach
    void independentFixtures() {
        productId = UuidV7.next();
        ownerId = UuidV7.next();
        jdbc.update(
                "INSERT INTO sys_user (id, register_source, status) VALUES (?, 'google', 'active')",
                ownerId);
        jdbc.update(
                """
                INSERT INTO biz_product (id, product_no, title, original_price, currency, status)
                VALUES (?, ?, 'Quotation test', 40.00, 'sgd', 'on_sale')
                """,
                productId,
                "QUOTE-" + productId);
    }

    @Test
    void migrationsUseUuidV7AndGrantManagementOnlyToAdmin() {
        try (SqlSession session = sessions.openSession()) {
            var repo = prices(session);
            Instant now = repo.currentTime();
            lock(session);
            repo.appendPolicy(policy("20", 1, now), ownerId, now);
            session.commit();
        }
        UUID id =
                jdbc.queryForObject(
                        "SELECT id FROM biz_team_price_policy WHERE product_id = ?",
                        UUID.class,
                        productId);
        assertThat(id.version()).isEqualTo(7);
        assertThat(
                        jdbc.queryForList(
                                """
                SELECT r.role_code FROM sys_role r
                JOIN rel_role_permission rp ON rp.role_id = r.id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE p.perm_code = 'catalog:product:write' AND rp.deleted = false
                """,
                                String.class))
                .containsExactly("admin");
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT count(*) FROM pg_constraint
                WHERE conrelid IN ('domain.biz_team_price_policy'::regclass,
                                   'domain.biz_team_price_quote'::regclass)
                  AND contype IN ('f', 'c')
                """,
                                Integer.class))
                .isZero();
    }

    @Test
    void firstTeamSnapshotSurvivesRevisionAndIsOwnerScoped() {
        TeamPriceQuote quote;
        try (SqlSession session = sessions.openSession()) {
            var product = lock(session);
            var repo = prices(session);
            Instant now = repo.currentTime();
            var policy = policy("20", 1, now);
            repo.appendPolicy(policy, ownerId, now);
            var storedPolicy = repo.findLatestPolicy(productId).orElseThrow();
            BigDecimal amount =
                    new DualPricingStrategy().quoteTeamPrice(product, storedPolicy, now);
            quote =
                    new TeamPriceQuote(
                            UuidV7.next(),
                            productId,
                            ownerId,
                            storedPolicy.version(),
                            amount,
                            storedPolicy.currency(),
                            now,
                            now.plusSeconds(900));
            repo.insertQuote(quote);
            session.commit();
        }
        try (SqlSession session = sessions.openSession()) {
            lock(session);
            var repo = prices(session);
            Instant now = repo.currentTime();
            repo.appendPolicy(policy("25", 2, now), ownerId, now);
            session.commit();
        }
        try (SqlSession session = sessions.openSession()) {
            var repo = prices(session);
            assertThat(repo.findLatestPolicy(productId).orElseThrow().amount())
                    .isEqualByComparingTo("25");
            assertThat(repo.findQuoteForOwner(quote.id(), ownerId)).contains(quote);
            assertThat(repo.findQuoteForOwner(quote.id(), productId)).isEmpty();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_group_deal WHERE product_id = ?",
                                Integer.class,
                                productId))
                .isZero();
    }

    @Test
    void rollbackLeavesNoQuoteAndDuplicatePolicyRevisionFails() {
        UUID quoteId = UuidV7.next();
        try (SqlSession session = sessions.openSession()) {
            lock(session);
            var repo = prices(session);
            Instant now = repo.currentTime();
            repo.appendPolicy(policy("20", 1, now), ownerId, now);
            session.commit();
            repo.insertQuote(
                    new TeamPriceQuote(
                            quoteId,
                            productId,
                            ownerId,
                            1,
                            new BigDecimal("20"),
                            "sgd",
                            now,
                            now.plusSeconds(60)));
            session.rollback();
            assertThat(repo.findQuoteForOwner(quoteId, ownerId)).isEmpty();
            assertThatThrownBy(() -> repo.appendPolicy(policy("25", 1, now), ownerId, now))
                    .isInstanceOf(PersistenceException.class);
            session.rollback();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_team_price_policy WHERE product_id = ?",
                                Integer.class,
                                productId))
                .isEqualTo(1);
    }

    @Test
    void competingQuoteWaitsForPolicyCommitThenSeesNewRevision() throws Exception {
        try (SqlSession writer = sessions.openSession();
                var executor = Executors.newSingleThreadExecutor()) {
            lock(writer);
            Instant beforeWait = prices(writer).currentTime();
            var started = new CountDownLatch(1);
            var pending =
                    executor.submit(
                            () -> {
                                try (SqlSession reader = sessions.openSession()) {
                                    started.countDown();
                                    lock(reader);
                                    var repo = prices(reader);
                                    Instant afterLock = repo.currentTime();
                                    var policy = repo.findLatestPolicy(productId).orElseThrow();
                                    assertThat(afterLock).isAfterOrEqualTo(beforeWait);
                                    return policy;
                                }
                            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> pending.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            prices(writer).appendPolicy(policy("25", 1, beforeWait), ownerId, beforeWait);
            writer.commit();
            assertThat(pending.get(5, TimeUnit.SECONDS).amount()).isEqualByComparingTo("25");
        }
    }

    private com.blocbuy.catalog.model.Product lock(SqlSession session) {
        return new ProductRepositoryImpl(session.getMapper(BizProductMapper.class))
                .lockById(productId)
                .orElseThrow();
    }

    private static TeamPriceRepositoryImpl prices(SqlSession session) {
        return new TeamPriceRepositoryImpl(session.getMapper(TeamPriceMapper.class));
    }

    private TeamPricePolicy policy(String amount, int version, Instant now) {
        return new TeamPricePolicy(
                productId,
                new BigDecimal(amount),
                "sgd",
                now.minusSeconds(1),
                now.plusSeconds(3600),
                version);
    }
}
