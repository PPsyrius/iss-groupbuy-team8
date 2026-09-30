package com.blocbuy.common.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.blocbuy.common.gateway.RateLimitGateway.RateLimit;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real Redis 8.10 fixed-window counting: the limit, the TTL on every key, and the reset. */
@Testcontainers
class RedisRateLimitGatewayIT {
    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.10").withExposedPorts(6379);

    private static final String PREFIX = "blocbuy:it:common:rate-limit:";
    private static final RateLimit PER_MINUTE =
            new RateLimit("invite-resolve", 60, Duration.ofMinutes(1));

    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;
    private static RedisRateLimitGateway gateway;

    @BeforeAll
    static void connect() {
        connections =
                new LettuceConnectionFactory(
                        new RedisStandaloneConfiguration(
                                REDIS.getHost(), REDIS.getMappedPort(6379)));
        connections.afterPropertiesSet();
        connections.start();
        redis = new StringRedisTemplate(connections);
        gateway =
                new RedisRateLimitGateway(
                        redis, new MockEnvironment().withProperty("spring.profiles.active", "it"));
    }

    @AfterAll
    static void disconnect() {
        connections.destroy();
    }

    @BeforeEach
    void flush() {
        redis.execute(
                (RedisCallback<Void>)
                        connection -> {
                            connection.serverCommands().flushDb();
                            return null;
                        });
    }

    @Test
    void theLimitAllowsMaxRequestsThenRejects() {
        assertThat(IntStream.range(0, 60).allMatch(i -> gateway.tryAcquire(PER_MINUTE, "a")))
                .isTrue();

        assertThat(gateway.tryAcquire(PER_MINUTE, "a")).isFalse();
        assertThat(gateway.tryAcquire(PER_MINUTE, "a")).isFalse();
    }

    @Test
    void theFirstRequestCreatesTheCounterWithATtlOfOneWindow() {
        gateway.tryAcquire(PER_MINUTE, "198.51.100.10");

        Set<String> keys = redis.keys(PREFIX + "*");
        assertThat(keys).hasSize(1);
        String key = keys.iterator().next();
        assertThat(key).startsWith(PREFIX + "invite-resolve:").doesNotContain("198.51.100.10");
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(60_000);
        assertThat(redis.opsForValue().get(key)).isEqualTo("1");
    }

    @Test
    void subjectsAndScopesCountIndependently() {
        RateLimit one = new RateLimit("invite-resolve", 1, Duration.ofMinutes(1));
        RateLimit other = new RateLimit("join-intent", 1, Duration.ofMinutes(1));

        assertThat(gateway.tryAcquire(one, "a")).isTrue();
        assertThat(gateway.tryAcquire(one, "a")).isFalse();
        assertThat(gateway.tryAcquire(one, "b")).isTrue();
        assertThat(gateway.tryAcquire(other, "a")).isTrue();
        assertThat(redis.keys(PREFIX + "*")).hasSize(3);
    }

    @Test
    void aNewWindowStartsOnceTheCounterExpires() {
        RateLimit perSecond = new RateLimit("invite-resolve", 1, Duration.ofSeconds(1));
        assertThat(gateway.tryAcquire(perSecond, "a")).isTrue();
        assertThat(gateway.tryAcquire(perSecond, "a")).isFalse();

        await().atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> redis.keys(PREFIX + "*").isEmpty());

        assertThat(gateway.tryAcquire(perSecond, "a")).isTrue();
    }
}
