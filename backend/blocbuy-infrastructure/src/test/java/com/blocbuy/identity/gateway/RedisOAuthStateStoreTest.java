package com.blocbuy.identity.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** One-time OAuth state store: TTL on issue, atomic single consumption. */
class RedisOAuthStateStoreTest {

    private static final GoogleOAuthProperties PROPS =
            new GoogleOAuthProperties("cid", "secret", "https://cb", null, null, 300);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);

    private final Environment environment = mock(Environment.class);
    private final RedisOAuthStateStore store = new RedisOAuthStateStore(redis, PROPS, environment);

    @Test
    void issueStateStoresWithTtl() {
        when(redis.opsForValue()).thenReturn(ops);
        when(environment.getProperty("spring.profiles.active", "local")).thenReturn("local");

        String state = store.issueState();

        assertThat(state).isNotBlank();
        verify(ops)
                .set(
                        eq("blocbuy:local:identity:oauth-state:" + state),
                        eq("1"),
                        eq(Duration.ofSeconds(300)));
    }

    @Test
    void consumeSucceedsExactlyOnce() {
        when(redis.opsForValue()).thenReturn(ops);
        when(environment.getProperty("spring.profiles.active", "local")).thenReturn("local");
        when(ops.getAndDelete("blocbuy:local:identity:oauth-state:s")).thenReturn("1");

        assertThat(store.consume("s")).isTrue();
    }

    @Test
    void consumeRejectsUnknownOrBlank() {
        when(redis.opsForValue()).thenReturn(ops);
        when(environment.getProperty("spring.profiles.active", "local")).thenReturn("local");
        when(ops.getAndDelete("blocbuy:local:identity:oauth-state:gone")).thenReturn(null);

        assertThat(store.consume("gone")).isFalse();
        assertThat(store.consume(" ")).isFalse();
    }
}
