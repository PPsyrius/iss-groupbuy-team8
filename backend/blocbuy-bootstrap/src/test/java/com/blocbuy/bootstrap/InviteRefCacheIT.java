package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.common.util.UuidV7;
import com.blocbuy.groupbuy.model.ShareInvite;
import com.blocbuy.groupbuy.query.ResolvedInviteView;
import com.blocbuy.groupbuy.service.GroupDealPurchaseSupport;
import com.blocbuy.groupbuy.service.ShareInviteAppService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Both JOIN attribution and the public invite read share one real Redis/PG cache-aside lookup. */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InviteRefCacheIT {
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

    @Autowired private GroupDealPurchaseSupport purchase;
    @Autowired private ShareInviteAppService invites;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void flushRedis() {
        redis.execute(
                (RedisCallback<Void>)
                        connection -> {
                            connection.serverCommands().flushDb();
                            return null;
                        });
    }

    @Test
    void repeatedJoinAttributionAndLandingReadShareTheCacheThenReloadAfterExpiry() {
        UUID deal = UuidV7.next();
        UUID product = UuidV7.next();
        UUID originalSharer = UuidV7.next();
        UUID laterSharer = UuidV7.next();
        UUID buyer = UuidV7.next();
        Instant deadline = Instant.now().plusSeconds(120);
        ShareInvite invite = seed(deal, product, originalSharer, deadline);
        user(laterSharer, "Later sharer");
        String key = key(invite.ref());

        assertThat(purchase.resolveSharer(invite.ref(), deal, buyer)).contains(originalSharer);
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(30_000);
        // A changed source row is visible only after eviction, proving warm JOIN reads skip PG.
        jdbc.update(
                "UPDATE biz_share_invite SET sharer_user_id=? WHERE id=?",
                laterSharer,
                invite.id());
        assertThat(purchase.resolveSharer(invite.ref(), deal, buyer)).contains(originalSharer);
        ResolvedInviteView landing = invites.resolve(invite.ref(), "198.51.100.21");
        assertThat(landing.sharerNickname()).isEqualTo("Original sharer");
        assertThat(purchase.resolveSharer(invite.ref(), UUID.randomUUID(), buyer)).isEmpty();
        assertThat(purchase.resolveSharer(invite.ref(), deal, originalSharer)).isEmpty();

        redis.expire(key, Duration.ofMillis(1));
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(() -> !redis.hasKey(key));
        assertThat(purchase.resolveSharer(invite.ref(), deal, buyer)).contains(laterSharer);
        assertThat(invites.resolve(invite.ref(), "198.51.100.21").sharerNickname())
                .isEqualTo("Later sharer");
    }

    @Test
    void nearDeadlineTtlIsBoundedAndEndedInvitesStillResolveFromPostgres() {
        UUID deal = UuidV7.next();
        UUID product = UuidV7.next();
        UUID sharer = UuidV7.next();
        UUID buyer = UuidV7.next();
        ShareInvite invite = seed(deal, product, sharer, Instant.now().plusSeconds(15));

        assertThat(purchase.resolveSharer(invite.ref(), deal, buyer)).contains(sharer);
        assertThat(redis.getExpire(key(invite.ref()), TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(15_000);

        assertThat(
                        jdbc.update(
                                "UPDATE biz_group_deal SET expire_time=? WHERE id=?",
                                Timestamp.from(Instant.now().minusSeconds(10)),
                                deal))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                        "SELECT expire_time FROM biz_group_deal WHERE id=?",
                                        Timestamp.class,
                                        deal)
                                .toInstant())
                .isBefore(Instant.now());
        assertThat(redis.delete(key(invite.ref()))).isTrue();
        assertThat(invites.resolve(invite.ref(), "198.51.100.22").dealId()).isEqualTo(deal);
        assertThat(redis.hasKey(key(invite.ref()))).isFalse();
        assertThat(purchase.resolveSharer(invite.ref(), deal, buyer)).contains(sharer);
    }

    @Test
    void absentReferenceFallsBackWithoutCachingOrChangingAttribution() {
        UUID deal = UuidV7.next();
        UUID buyer = UuidV7.next();
        String missing = "CCCCCCCCCCCCCCCCCCCCCC";
        assertThat(purchase.resolveSharer(missing, deal, buyer)).isEmpty();
        assertThat(purchase.resolveSharer(missing, deal, buyer)).isEmpty();
        assertThat(redis.hasKey(key(missing))).isFalse();
    }

    private ShareInvite seed(UUID deal, UUID product, UUID sharer, Instant deadline) {
        user(sharer, "Original sharer");
        jdbc.update(
                """
                INSERT INTO biz_group_deal
                    (id,deal_no,product_id,leader_user_id,deal_price,currency,target_size,
                     current_size,status,start_time,expire_time)
                VALUES (?, ?, ?, ?, ?, 'sgd', 3, 1, 'open', CURRENT_TIMESTAMP-INTERVAL '1 minute', ?)
                """,
                deal,
                "INVITE-CACHE-" + deal,
                product,
                sharer,
                new BigDecimal("12.00"),
                Timestamp.from(deadline));
        ShareInvite invite = ShareInvite.mint(deal, sharer, new SecureRandom());
        jdbc.update(
                "INSERT INTO biz_share_invite(id,invite_ref,deal_id,sharer_user_id) VALUES(?,?,?,?)",
                invite.id(),
                invite.ref(),
                invite.dealId(),
                invite.sharerUserId());
        return invite;
    }

    private void user(UUID id, String nickname) {
        jdbc.update(
                "INSERT INTO sys_user(id,nickname,register_source,status) VALUES(?,?,'local','active')",
                id,
                nickname);
    }

    private static String key(String ref) {
        return "blocbuy:local:groupbuy:invite-ref:" + ref;
    }
}
