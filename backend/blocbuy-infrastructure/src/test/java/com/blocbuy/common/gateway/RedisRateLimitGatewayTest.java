package com.blocbuy.common.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blocbuy.common.gateway.RateLimitGateway.RateLimit;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.env.MockEnvironment;

/** Failure policy of the Redis limiter: an unreachable store never blocks a request. */
class RedisRateLimitGatewayTest {

    private static final RateLimit LIMIT =
            new RateLimit("invite-resolve", 1, Duration.ofMinutes(1));

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisRateLimitGateway gateway =
            new RedisRateLimitGateway(redis, new MockEnvironment());

    @Test
    @SuppressWarnings("unchecked")
    void anUnreachableStoreAllowsTheRequest() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(gateway.tryAcquire(LIMIT, "198.51.100.10")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aMissingCountAllowsTheRequest() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(null);

        assertThat(gateway.tryAcquire(LIMIT, "198.51.100.10")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aCountAboveTheLimitRejectsTheRequest() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(2L);

        assertThat(gateway.tryAcquire(LIMIT, "198.51.100.10")).isFalse();
    }
}
