package com.blocbuy.groupbuy.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.common.persistence.UuidTypeHandler;
import com.blocbuy.groupbuy.model.GroupDeal;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
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

/** PostgreSQL verifies that best-offer reads return at most one eligible deal per product. */
@Testcontainers
class BestOpenDealRepositoryIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID OTHER_PRODUCT =
            UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final UUID UNREQUESTED = UUID.fromString("a0000000-0000-7000-8000-000000000003");
    private static final UUID UNAVAILABLE = UUID.fromString("a0000000-0000-7000-8000-000000000004");
    private static final UUID MISSING = UUID.fromString("a0000000-0000-7000-8000-000000000005");
    private static final UUID LOW_ID = UUID.fromString("b0000000-0000-7000-8000-000000000001");
    private static final UUID HIGH_ID = UUID.fromString("b0000000-0000-7000-8000-000000000002");
    private static final UUID OTHER_BEST = UUID.fromString("b0000000-0000-7000-8000-000000000003");
    private static final UUID LEADER = UUID.fromString("c0000000-0000-7000-8000-000000000001");

    private static SqlSessionFactory sessions;
    private static JdbcTemplate jdbc;

    /** Uses the committed schema and mapper with bounded SQL waits. */
    @BeforeAll
    static void migrateAndConfigureMyBatis() throws Exception {
        String url = POSTGRES.getJdbcUrl();
        var source =
                new DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=domain",
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
                        "V5__groupbuy.sql");
        new ResourceDatabasePopulator(new FileSystemResource(migration)).execute(source);
        var config =
                new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setDefaultStatementTimeout(5);
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        String resource = "mapper/groupbuy/BizGroupDealMapper.xml";
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    /** Higher IDs arrive first so equal-price selection cannot rely on insertion order. */
    @BeforeEach
    void fixtures() {
        jdbc.update("DELETE FROM biz_group_deal");
        deal(HIGH_ID, PRODUCT, "12.50", "open", false, true);
        deal(LOW_ID, PRODUCT, "12.50", "open", false, true);
        deal(OTHER_BEST, OTHER_PRODUCT, "8.20", "open", false, true);
    }

    @Test
    void hundredsOfOpenDealsStillReturnOneLowestPriceRowPerRequestedProduct() {
        jdbc.update(
                """
                INSERT INTO biz_group_deal
                    (id, deal_no, product_id, leader_user_id, deal_price, currency, target_size,
                     current_size, status, start_time, expire_time)
                SELECT md5(n::text)::uuid, 'BULK-' || n, CASE WHEN n <= 400 THEN ? ELSE ? END,
                       ?, 100 + n, 'sgd', 3, 1, 'open', CURRENT_TIMESTAMP - INTERVAL '1 hour',
                       CURRENT_TIMESTAMP + INTERVAL '1 day'
                FROM generate_series(1, 600) AS n
                """,
                PRODUCT,
                OTHER_PRODUCT,
                LEADER);
        try (SqlSession session = sessions.openSession()) {
            List<GroupDeal> deals =
                    repository(session)
                            .findBestOpenDealsByProductIds(List.of(PRODUCT, OTHER_PRODUCT));

            assertThat(deals).hasSize(2);
            assertThat(deals).extracting(GroupDeal::id).containsExactly(LOW_ID, OTHER_BEST);
            assertThat(deals)
                    .extracting(GroupDeal::dealPrice)
                    .containsExactly(new BigDecimal("12.50"), new BigDecimal("8.20"));
        }
    }

    @Test
    void equalPricesUseAscendingDealIdAndDuplicateProductIdsDoNotDuplicateRows() {
        try (SqlSession session = sessions.openSession()) {
            GroupDealRepositoryImpl repository = repository(session);

            assertThat(repository.findBestOpenDealsByProductIds(List.of(PRODUCT)))
                    .extracting(GroupDeal::id)
                    .containsExactly(LOW_ID);
            assertThat(
                            repository.findBestOpenDealsByProductIds(
                                    List.of(OTHER_PRODUCT, PRODUCT, PRODUCT, MISSING)))
                    .extracting(GroupDeal::id)
                    .containsExactly(LOW_ID, OTHER_BEST);
        }
    }

    @Test
    void expiredClosedDeletedAndUnrequestedDealsAreExcludedEvenWhenCheaper() {
        deal(UUID.randomUUID(), PRODUCT, "1.00", "open", false, false);
        deal(UUID.randomUUID(), PRODUCT, "2.00", "succeeded", false, true);
        deal(UUID.randomUUID(), PRODUCT, "3.00", "open", true, true);
        deal(UUID.randomUUID(), UNREQUESTED, "4.00", "open", false, true);
        deal(UUID.randomUUID(), UNAVAILABLE, "5.00", "expired", false, false);
        try (SqlSession session = sessions.openSession()) {
            assertThat(
                            repository(session)
                                    .findBestOpenDealsByProductIds(
                                            List.of(PRODUCT, OTHER_PRODUCT, UNAVAILABLE, MISSING)))
                    .extracting(GroupDeal::id)
                    .containsExactly(LOW_ID, OTHER_BEST);
        }
    }

    @Test
    void openDealAtTargetSizeRetainsTheExistingReadEligibility() {
        jdbc.update("UPDATE biz_group_deal SET current_size = target_size WHERE id = ?", LOW_ID);
        try (SqlSession session = sessions.openSession()) {
            assertThat(repository(session).findBestOpenDealsByProductIds(List.of(PRODUCT)))
                    .singleElement()
                    .satisfies(deal -> {
                        assertThat(deal.id()).isEqualTo(LOW_ID);
                        assertThat(deal.currentSize()).isEqualTo(deal.targetSize());
                    });
        }
    }

    private static GroupDealRepositoryImpl repository(SqlSession session) {
        return new GroupDealRepositoryImpl(session.getMapper(BizGroupDealMapper.class));
    }

    private static void deal(
            UUID id, UUID productId, String price, String status, boolean deleted, boolean future) {
        jdbc.update(
                """
                INSERT INTO biz_group_deal
                    (id, deal_no, product_id, leader_user_id, deal_price, currency, target_size,
                     current_size, status, start_time, expire_time, deleted)
                VALUES (?, ?, ?, ?, ?, 'sgd', 3, 1, ?, CURRENT_TIMESTAMP - INTERVAL '1 hour',
                        CURRENT_TIMESTAMP + (? * INTERVAL '1 day'), ?)
                """,
                id,
                "DEAL-" + id,
                productId,
                LEADER,
                new BigDecimal(price),
                status,
                future ? 1 : -1,
                deleted);
    }
}
