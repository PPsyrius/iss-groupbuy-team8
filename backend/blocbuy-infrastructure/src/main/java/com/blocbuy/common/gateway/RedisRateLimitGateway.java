package com.blocbuy.common.gateway;

import com.blocbuy.common.config.RedisKeys;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis fixed-window {@link RateLimitGateway}. One counter per scope and subject, keyed {@code
 * blocbuy:{env}:common:rate-limit:{scope}:{subject digest}}. The first request of a window creates
 * the counter with a TTL of one window, in the same Lua call as the increment, so no counter ever
 * lives without a TTL; the window ends when the key expires.
 *
 * <p>Owner: common (rate limiting). TTL: one window. Cardinality: at most one key per scope and
 * distinct subject seen within a window. The subject is stored only as a name-based UUID digest,
 * which keeps client addresses out of key listings; it is pseudonymisation, not anonymisation.
 *
 * <p>Fail-open: when Redis is unreachable the request is allowed and a WARN is logged, so an outage
 * never blocks a legitimate caller.
 */
@Slf4j
@Component
public class RedisRateLimitGateway implements RateLimitGateway {

    private static final String DOMAIN = "common";
    private static final String TYPE = "rate-limit";
    private static final RedisScript<Long> COUNT =
            RedisScript.of(
                    """
                    local n = redis.call('INCR', KEYS[1])
                    if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
                    return n
                    """,
                    Long.class);

    private final StringRedisTemplate redis;
    private final Environment environment;
    private final MeterRegistry meters;

    // Spring-managed collaborators, not external mutable input — defensive copies impossible/wrong
    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public RedisRateLimitGateway(
            StringRedisTemplate redis, Environment environment, MeterRegistry meters) {
        this.redis = redis;
        this.environment = environment;
        this.meters = meters;
    }

    @Override
    public boolean tryAcquire(RateLimit limit, String subject) {
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(subject, "subject");
        try {
            Long count =
                    redis.execute(
                            COUNT,
                            List.of(key(limit, subject)),
                            Long.toString(limit.window().toMillis()));
            if (count == null) {
                return allowWithoutStore(limit, "MissingScriptResult");
            }
            return count <= limit.maxRequests();
        } catch (DataAccessException ex) {
            return allowWithoutStore(limit, ex.getClass().getSimpleName());
        }
    }

    private boolean allowWithoutStore(RateLimit limit, String cause) {
        log.warn(
                "Rate limit store unavailable; allowing the request (scope={}, cause={})",
                limit.scope(),
                cause);
        meters.counter("blocbuy.rate_limit.fail_open", "scope", limit.scope()).increment();
        return true;
    }

    private String key(RateLimit limit, String subject) {
        String digest = UUID.nameUUIDFromBytes(subject.getBytes(StandardCharsets.UTF_8)).toString();
        return RedisKeys.key(
                environment.getProperty("spring.profiles.active", "local"),
                DOMAIN,
                TYPE,
                limit.scope() + ":" + digest);
    }
}
