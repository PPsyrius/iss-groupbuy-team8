package com.blocbuy.identity.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.identity.gateway.OAuthStateStore.IssuedState;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** One-time OAuth state store: TTL on issue, atomic single consumption, browser-session binding. */
class RedisOAuthStateStoreTest {

    private static final GoogleOAuthProperties PROPS =
            new GoogleOAuthProperties("cid", "secret", "https://cb", null, null, 300);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);

    private final Environment environment = mock(Environment.class);
    private final RedisOAuthStateStore store = new RedisOAuthStateStore(redis, PROPS, environment);

    /** Issues distinct state/binding pair with 300s TTL under the oauth-state key. */
    @Test
    void issueStateStoresBindingWithTtl() {
        when(redis.opsForValue()).thenReturn(ops);
        when(environment.getProperty("spring.profiles.active", "local")).thenReturn("local");

        IssuedState issued = store.issueState();

        assertThat(issued.state()).isNotBlank();
        assertThat(issued.binding()).isNotBlank().isNotEqualTo(issued.state());
        verify(ops)
                .set(
                        "blocbuy:local:identity:oauth-state:" + issued.state(),
                        issued.binding(),
                        Duration.ofSeconds(300));
    }

    @Test
    void consumeMatchesBindingViaAtomicGetAndDelete() {
        when(redis.opsForValue()).thenReturn(ops);
        when(environment.getProperty("spring.profiles.active", "local")).thenReturn("local");
        when(ops.getAndDelete("blocbuy:local:identity:oauth-state:s")).thenReturn("b");

        assertThat(store.consume("s", "b")).isTrue();
    }

    @Test
    void consumeRejectsMismatchedOrMissingBinding() {
        when(redis.opsForValue()).thenReturn(ops);
        when(environment.getProperty("spring.profiles.active", "local")).thenReturn("local");
        when(ops.getAndDelete("blocbuy:local:identity:oauth-state:s")).thenReturn("b");

        assertThat(store.consume("s", "other")).isFalse();
        assertThat(store.consume("s", null)).isFalse();
        assertThat(store.consume("s", " ")).isFalse();
    }

    @Test
    void consumeRejectsUnknownOrBlank() {
        when(redis.opsForValue()).thenReturn(ops);
        when(environment.getProperty("spring.profiles.active", "local")).thenReturn("local");
        when(ops.getAndDelete("blocbuy:local:identity:oauth-state:gone")).thenReturn(null);

        assertThat(store.consume("gone", "b")).isFalse();
        assertThat(store.consume(" ", "b")).isFalse();
    }

    @Test
    void consumeRejectsNullStateWithoutTouchingRedis() {
        assertThat(store.consume(null, "b")).isFalse();
        verifyNoInteractions(redis);
    }
}
