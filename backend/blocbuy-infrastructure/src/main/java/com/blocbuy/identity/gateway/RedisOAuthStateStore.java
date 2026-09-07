package com.blocbuy.identity.gateway;

import com.blocbuy.common.config.RedisKeys;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed {@link OAuthStateStore}. Keys follow {@code
 * blocbuy:{env}:identity:oauth-state:{state}} with a short TTL (default 5 min); consumption is
 * atomic via GETDEL so a state authenticates exactly once. Derived/short-lived state only —
 * PostgreSQL stays the source of truth for accounts.
 */
@Component
public class RedisOAuthStateStore implements OAuthStateStore {

    private static final String DOMAIN = "identity";
    private static final String TYPE = "oauth-state";
    private static final String PRESENT = "1";

    private final StringRedisTemplate redis;
    private final GoogleOAuthProperties properties;
    private final Environment environment;

    // Spring-managed collaborators, not external mutable input — defensive copies impossible/wrong
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public RedisOAuthStateStore(
            StringRedisTemplate redis, GoogleOAuthProperties properties, Environment environment) {
        this.redis = redis;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public String issueState() {
        String state = UUID.randomUUID().toString();
        redis.opsForValue()
                .set(key(state), PRESENT, Duration.ofSeconds(properties.stateTtlSeconds()));
        return state;
    }

    @Override
    public boolean consume(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        return redis.opsForValue().getAndDelete(key(state)) != null;
    }

    private String key(String state) {
        return RedisKeys.key(
                environment.getProperty("spring.profiles.active", "local"), DOMAIN, TYPE, state);
    }
}
