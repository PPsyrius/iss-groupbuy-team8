package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.blocbuy.catalog.service.ProductAppService;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.groupbuy.event.MemberJoined;
import com.blocbuy.groupbuy.event.TeamExpired;
import com.blocbuy.groupbuy.event.TeamSucceeded;
import com.blocbuy.groupbuy.service.GroupDealQueryService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL selection, Redis population/expiry and commit-bound event invalidation. */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BestOpenDealCacheIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.10").withExposedPorts(6379);

    @DynamicPropertySource
    static void stores(DynamicPropertyRegistry properties) {
        properties.add(
                "spring.datasource.url",
                () -> {
                    String url = POSTGRES.getJdbcUrl();
                    return url + (url.contains("?") ? "&" : "?") + "currentSchema=domain";
                });
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private GroupDealQueryService query;
    @Autowired private ProductAppService products;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactions;
    private UUID product;
    private UUID deal;

    enum Change {
        JOINED,
        SUCCEEDED,
        EXPIRED
    }

    @BeforeEach
    void seed() {
        redis.execute(
                (RedisCallback<Void>)
                        connection -> {
                            connection.serverCommands().flushDb();
                            return null;
                        });
        product = UuidV7.next();
        deal = UuidV7.next();
        jdbc.update(
                """
                INSERT INTO biz_group_deal
                    (id, deal_no, product_id, leader_user_id, deal_price, currency,
                     target_size, current_size, status, start_time, expire_time)
                VALUES (?, ?, ?, ?, 89.00, 'sgd', 5, 1, 'open',
                        CURRENT_TIMESTAMP - INTERVAL '1 minute', CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """,
                deal,
                "CACHE-" + deal,
                product,
                UuidV7.next());
    }

    @Test
    void populatedOfferIsReadFromRedisUntilExpiryThenReloadedFromPostgres() {
        UUID absent = UuidV7.next();
        String absentKey = "blocbuy:local:groupbuy:best-open-deal:" + absent;
        var first = query.findBestOpenDealsByProductIds(List.of(product, absent));
        assertThat(first).containsOnlyKeys(product);
        assertThat(first.get(product).dealPrice()).isEqualByComparingTo("89.00");
        assertThat(redis.getExpire(key(), TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(25_000);
        assertThat(redis.getExpire(absentKey, TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(10_000);
        jdbc.update("UPDATE biz_group_deal SET deal_price = 100.00 WHERE id = ?", deal);
        assertThat(query.findBestOpenDealsByProductIds(List.of(product, absent))).isEqualTo(first);
        // Exercise real Redis expiration without waiting out every production TTL.
        redis.expire(key(), Duration.ofMillis(100));
        await().atMost(Duration.ofSeconds(3)).until(() -> !redis.hasKey(key()));
        assertThat(query.findBestOpenDealsByProductIds(List.of(product)).get(product).dealPrice())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @ParameterizedTest
    @EnumSource(Change.class)
    void committedEventsEvictOnlyAfterTheTransaction(Change change) {
        query.findBestOpenDealsByProductIds(List.of(product));
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            switch (change) {
                                case JOINED ->
                                        jdbc.update(
                                                "UPDATE biz_group_deal SET current_size = 2 WHERE id = ?",
                                                deal);
                                case SUCCEEDED ->
                                        jdbc.update(
                                                "UPDATE biz_group_deal SET current_size = 5, status = 'succeeded' WHERE id = ?",
                                                deal);
                                case EXPIRED ->
                                        jdbc.update(
                                                "UPDATE biz_group_deal SET status = 'expired' WHERE id = ?",
                                                deal);
                            }
                            events.publishEvent(event(change));
                            assertThat(redis.hasKey(key())).isTrue();
                        });
        assertThat(redis.hasKey(key())).isFalse();
        var refreshed = query.findBestOpenDealsByProductIds(List.of(product));
        if (change == Change.JOINED) {
            assertThat(refreshed.get(product).currentSize()).isEqualTo(2);
        } else {
            assertThat(refreshed).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(Change.class)
    void rolledBackEventsDoNotEvict(Change change) {
        var first = query.findBestOpenDealsByProductIds(List.of(product));
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            events.publishEvent(event(change));
                            status.setRollbackOnly();
                        });
        assertThat(redis.hasKey(key())).isTrue();
        assertThat(query.findBestOpenDealsByProductIds(List.of(product))).isEqualTo(first);
    }

    /** Issue #245: a committed join refreshes the catalog card's progress on the next read. */
    @Test
    void committedJoinRefreshesCatalogCardProgress() {
        String category = "cache-" + product;
        jdbc.update(
                """
                INSERT INTO biz_product (id, product_no, title, original_price, currency, stock,
                    status, category)
                VALUES (?, ?, 'Cached tea', 120.00, 'sgd', 5, 'on_sale', ?)
                """,
                product,
                category,
                category);
        assertThat(products.pageListings(1, 20, category).items().get(0).deal().currentSize())
                .isEqualTo(1);
        assertThat(redis.hasKey(key())).isTrue();
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            jdbc.update(
                                    "UPDATE biz_group_deal SET current_size = 2 WHERE id = ?",
                                    deal);
                            events.publishEvent(event(Change.JOINED));
                        });
        assertThat(redis.hasKey(key())).isFalse();
        var card = products.pageListings(1, 20, category).items().get(0);
        assertThat(card.deal().dealId()).isEqualTo(deal);
        assertThat(card.deal().currentSize()).isEqualTo(2);
    }

    @Test
    void aFactOutsideATransactionDoesNotEvict() {
        query.findBestOpenDealsByProductIds(List.of(product));
        events.publishEvent(event(Change.JOINED));
        assertThat(redis.hasKey(key())).isTrue();
    }

    private Object event(Change change) {
        return switch (change) {
            case JOINED ->
                    new MemberJoined(
                            deal, product, UuidV7.next(), UuidV7.next(), null, Instant.now());
            case SUCCEEDED -> new TeamSucceeded(deal, product, 5, Instant.now());
            case EXPIRED -> new TeamExpired(deal, product, Instant.now());
        };
    }

    @Test
    void absentOffersHaveShortTtlsAndReloadAfterExpiry() {
        UUID absent = UuidV7.next();
        String absentKey = "blocbuy:local:groupbuy:best-open-deal:" + absent;
        assertThat(query.findBestOpenDealsByProductIds(List.of(absent))).isEmpty();
        assertThat(redis.getExpire(absentKey)).isPositive().isLessThanOrEqualTo(10);
        jdbc.update("UPDATE biz_group_deal SET product_id = ? WHERE id = ?", absent, deal);
        assertThat(query.findBestOpenDealsByProductIds(List.of(absent))).isEmpty();
        redis.expire(absentKey, Duration.ofMillis(100));
        await().atMost(Duration.ofSeconds(3)).until(() -> !redis.hasKey(absentKey));
        assertThat(query.findBestOpenDealsByProductIds(List.of(absent))).containsOnlyKeys(absent);
    }

    @Test
    void unreachableRedisReturnsSourceOffersAndCannotUndoACommittedFact() {
        var expected = query.findBestOpenDealsByProductIds(List.of(product));
        RedisConnectionFactory original = redis.getConnectionFactory();
        var unavailable =
                new LettuceConnectionFactory(
                        new RedisStandaloneConfiguration("127.0.0.1", 1),
                        LettuceClientConfiguration.builder()
                                .commandTimeout(Duration.ofMillis(200))
                                .shutdownTimeout(Duration.ZERO)
                                .build());
        unavailable.afterPropertiesSet();
        unavailable.start();
        try {
            redis.setConnectionFactory(unavailable);
            assertThat(query.findBestOpenDealsByProductIds(List.of(product))).isEqualTo(expected);
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            status -> {
                                jdbc.update(
                                        "UPDATE biz_group_deal SET deal_price = 100.00 WHERE id = ?",
                                        deal);
                                events.publishEvent(event(Change.JOINED));
                            });
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT deal_price FROM biz_group_deal WHERE id = ?",
                                    BigDecimal.class,
                                    deal))
                    .isEqualByComparingTo("100.00");
            assertThat(
                            query.findBestOpenDealsByProductIds(List.of(product))
                                    .get(product)
                                    .dealPrice())
                    .isEqualByComparingTo("100.00");
        } finally {
            redis.setConnectionFactory(original);
            unavailable.destroy();
        }
    }

    private String key() {
        return "blocbuy:local:groupbuy:best-open-deal:" + product;
    }
}
