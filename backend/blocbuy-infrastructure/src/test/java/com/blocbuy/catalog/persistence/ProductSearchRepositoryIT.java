package com.blocbuy.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.catalog.model.Product;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
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

/** Real PostgreSQL coverage for UC1-ext matching, visibility, count, order, and pagination. */
@Testcontainers
class ProductSearchRepositoryIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final UUID NEWER_COFFEE =
            UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID OLDER_COFFEE =
            UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final UUID LITERAL = UUID.fromString("a0000000-0000-7000-8000-000000000003");
    private static final UUID SUBTITLE = UUID.fromString("a0000000-0000-7000-8000-000000000010");
    private static final UUID TIE_HIGH = UUID.fromString("a0000000-0000-7000-8000-000000000009");
    private static final UUID TIE_LOW = UUID.fromString("a0000000-0000-7000-8000-000000000008");

    private static SqlSessionFactory sessions;
    private static JdbcTemplate jdbc;

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
        new ResourceDatabasePopulator(new FileSystemResource(migrations.resolve("V4__catalog.sql")))
                .execute(dataSource);

        var config =
                new Configuration(
                        new Environment("test", new JdbcTransactionFactory(), dataSource));
        config.setDefaultStatementTimeout(5);
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        try (InputStream input =
                new ClassPathResource("mapper/catalog/BizProductMapper.xml").getInputStream()) {
            new XMLMapperBuilder(
                            input,
                            config,
                            "mapper/catalog/BizProductMapper.xml",
                            config.getSqlFragments())
                    .parse();
        }
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    @BeforeEach
    void fixtures() {
        jdbc.update("DELETE FROM biz_product");
        insert(
                NEWER_COFFEE,
                "COFFEE-NEW",
                "Premium Coffee Beans",
                "Fresh roast for breakfast",
                "grocery",
                "on_sale",
                false,
                Instant.parse("2026-09-17T03:00:00Z"));
        insert(
                OLDER_COFFEE,
                "COFFEE-OLD",
                "Kitchen companion",
                "A quiet COFFEE grinder",
                "grocery",
                "on_sale",
                false,
                Instant.parse("2026-09-17T02:00:00Z"));
        insert(
                UUID.fromString("a0000000-0000-7000-8000-000000000004"),
                "COFFEE-DRAFT",
                "Coffee draft",
                "Not public",
                "grocery",
                "draft",
                false,
                Instant.parse("2026-09-17T05:00:00Z"));
        insert(
                UUID.fromString("a0000000-0000-7000-8000-000000000005"),
                "COFFEE-DELETED",
                "Coffee deleted",
                "Not public",
                "grocery",
                "on_sale",
                true,
                Instant.parse("2026-09-17T04:00:00Z"));
        insert(
                UUID.fromString("a0000000-0000-7000-8000-000000000006"),
                "COFFEE-ELECTRONICS",
                "Coffee maker",
                "Different category",
                "electronics",
                "on_sale",
                false,
                Instant.parse("2026-09-17T01:00:00Z"));
        insert(
                LITERAL,
                "LITERAL",
                "100% Cotton_Soft\\Blanket",
                "Literal wildcard characters",
                "home",
                "on_sale",
                false,
                Instant.parse("2026-09-17T00:00:00Z"));
        insert(
                UUID.fromString("a0000000-0000-7000-8000-000000000007"),
                "WILDCARD-DECOY",
                "100X CottonXSoft\\Blanket",
                "Must not match escaped wildcards",
                "home",
                "on_sale",
                false,
                Instant.parse("2026-09-16T23:00:00Z"));
        insertWithSubtitle(
                SUBTITLE,
                "SUBTITLE",
                "Unrelated title",
                "Quiet ergonomic keyboard",
                "Unrelated description",
                "electronics",
                Instant.parse("2026-09-16T22:00:00Z"));
    }

    @Test
    void matchesTitleAndDescriptionCaseInsensitively() {
        try (SqlSession session = sessions.openSession()) {
            ProductRepositoryImpl repository = repository(session);

            assertThat(
                            repository
                                    .searchOnSale(new PageQuery(1, 20), null, List.of("PREMIUM"))
                                    .items())
                    .extracting(Product::id)
                    .containsExactly(NEWER_COFFEE);
            assertThat(
                            repository
                                    .searchOnSale(new PageQuery(1, 20), null, List.of("grinder"))
                                    .items())
                    .extracting(Product::id)
                    .containsExactly(OLDER_COFFEE);
        }
    }

    @Test
    void combinesKeywordCategoryAndPaginationWithConsistentCountAndOrder() {
        try (SqlSession session = sessions.openSession()) {
            PageResult<Product> result =
                    repository(session)
                            .searchOnSale(new PageQuery(2, 1), "grocery", List.of("coffee"));

            assertThat(result.total()).isEqualTo(2);
            assertThat(result.page()).isEqualTo(2);
            assertThat(result.size()).isEqualTo(1);
            assertThat(result.items()).extracting(Product::id).containsExactly(OLDER_COFFEE);
        }
    }

    @Test
    void treatsLikeMetacharactersAsLiteralData() {
        try (SqlSession session = sessions.openSession()) {
            ProductRepositoryImpl repository = repository(session);

            assertThat(repository.searchOnSale(new PageQuery(1, 20), null, List.of("100%")).items())
                    .extracting(Product::id)
                    .containsExactly(LITERAL);
            assertThat(
                            repository
                                    .searchOnSale(
                                            new PageQuery(1, 20),
                                            null,
                                            List.of("Cotton_Soft\\Blanket"))
                                    .items())
                    .extracting(Product::id)
                    .containsExactly(LITERAL);
        }
    }

    @Test
    void allTokensMustMatchAndNoMatchReturnsEmptyPage() {
        try (SqlSession session = sessions.openSession()) {
            ProductRepositoryImpl repository = repository(session);

            assertThat(
                            repository
                                    .searchOnSale(
                                            new PageQuery(1, 20),
                                            "grocery",
                                            List.of("coffee", "fresh"))
                                    .items())
                    .extracting(Product::id)
                    .containsExactly(NEWER_COFFEE);
            PageResult<Product> empty =
                    repository.searchOnSale(
                            new PageQuery(1, 20), "grocery", List.of("coffee", "missing"));
            assertThat(empty.items()).isEmpty();
            assertThat(empty.total()).isZero();
        }
    }

    @Test
    void matchesSubtitleOnly() {
        try (SqlSession session = sessions.openSession()) {
            assertThat(
                            repository(session)
                                    .searchOnSale(
                                            new PageQuery(1, 20),
                                            "electronics",
                                            List.of("ergonomic"))
                                    .items())
                    .extracting(Product::id)
                    .containsExactly(SUBTITLE);
        }
    }

    @Test
    void equalCreationTimesUseDescendingIdTieBreaker() {
        Instant sameTime = Instant.parse("2026-09-17T06:00:00Z");
        insert(
                TIE_LOW,
                "TIE-LOW",
                "Tie ordering sample",
                "tie-order",
                "test",
                "on_sale",
                false,
                sameTime);
        insert(
                TIE_HIGH,
                "TIE-HIGH",
                "Tie ordering sample",
                "tie-order",
                "test",
                "on_sale",
                false,
                sameTime);

        try (SqlSession session = sessions.openSession()) {
            assertThat(
                            repository(session)
                                    .searchOnSale(
                                            new PageQuery(1, 20), "test", List.of("tie-order"))
                                    .items())
                    .extracting(Product::id)
                    .containsExactly(TIE_HIGH, TIE_LOW);
        }
    }

    private static ProductRepositoryImpl repository(SqlSession session) {
        return new ProductRepositoryImpl(session.getMapper(BizProductMapper.class));
    }

    private static void insert(
            UUID id,
            String productNo,
            String title,
            String description,
            String category,
            String status,
            boolean deleted,
            Instant createdAt) {
        jdbc.update(
                """
                INSERT INTO biz_product
                    (id, product_no, title, description, category, original_price, currency,
                     status, deleted, create_time)
                VALUES (?, ?, ?, ?, ?, 10.00, 'sgd', ?, ?, ?)
                """,
                id,
                productNo,
                title,
                description,
                category,
                status,
                deleted,
                Timestamp.from(createdAt));
    }

    private static void insertWithSubtitle(
            UUID id,
            String productNo,
            String title,
            String subtitle,
            String description,
            String category,
            Instant createdAt) {
        jdbc.update(
                """
                INSERT INTO biz_product
                    (id, product_no, title, subtitle, description, category, original_price,
                     currency, status, deleted, create_time)
                VALUES (?, ?, ?, ?, ?, ?, 10.00, 'sgd', 'on_sale', false, ?)
                """,
                id,
                productNo,
                title,
                subtitle,
                description,
                category,
                Timestamp.from(createdAt));
    }
}
