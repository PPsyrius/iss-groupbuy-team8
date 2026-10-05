package com.blocbuy.groupbuy.persistence;

import com.blocbuy.common.config.RedisKeys;
import com.blocbuy.groupbuy.model.ShareInvite;
import com.blocbuy.groupbuy.repository.ShareInviteRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Derived invite-ref lookup for JOIN attribution and the anonymous invite landing page. PostgreSQL
 * remains the authority; only active, well-formed references are cached before their team deadline.
 * All other repository operations continue through the original MyBatis implementation.
 */
@Slf4j
@Primary
@Repository
@RequiredArgsConstructor
public class RedisCachedShareInviteRepository implements ShareInviteRepository {
    private static final int VALUE_VERSION = 1;
    private static final long MIN_JITTER_MILLIS = 20_000;
    private static final long MAX_JITTER_MILLIS_EXCLUSIVE = 30_001;

    private final ShareInviteRepositoryImpl source;
    private final StringRedisTemplate redis;
    private final Environment environment;
    private final ObjectMapper json;

    record Entry(int version, ShareInvite invite, Instant expiresAt) {}

    @Override
    public Optional<ShareInvite> findByRef(String ref) {
        if (!ShareInvite.isWellFormedRef(ref)) {
            return source.findByRef(ref);
        }
        String key = RedisKeys.key(profile(), "groupbuy", "invite-ref", ref);
        boolean available = true;
        try {
            String value = redis.opsForValue().get(key);
            if (value != null) {
                Entry entry = json.readValue(value, Entry.class);
                if (valid(entry, ref)) {
                    return Optional.of(entry.invite());
                }
            }
        } catch (DataAccessException unavailable) {
            available = false;
            logUnavailable("read", unavailable);
        } catch (JacksonException malformed) {
            log.warn(
                    "Invalid invite cache value; loading from PostgreSQL (cause={})",
                    malformed.getClass().getSimpleName());
        }
        Optional<ShareInviteRepositoryImpl.InviteWithDeadline> loaded =
                source.findByRefWithDeadline(ref);
        if (available) {
            loaded.ifPresent(candidate -> populate(key, candidate));
        }
        return loaded.map(ShareInviteRepositoryImpl.InviteWithDeadline::invite);
    }

    @Override
    public Optional<ShareInvite> findByDealAndSharer(UUID dealId, UUID sharerUserId) {
        return source.findByDealAndSharer(dealId, sharerUserId);
    }

    @Override
    public boolean insertIfAbsent(ShareInvite candidate) {
        return source.insertIfAbsent(candidate);
    }

    private void populate(String key, ShareInviteRepositoryImpl.InviteWithDeadline candidate) {
        Instant expiry = candidate.expireTime();
        if (expiry == null) return;
        long remainingMillis = Duration.between(Instant.now(), expiry).toMillis();
        long jitterMillis =
                ThreadLocalRandom.current()
                        .nextLong(MIN_JITTER_MILLIS, MAX_JITTER_MILLIS_EXCLUSIVE);
        long ttlMillis = Math.min(remainingMillis, jitterMillis);
        if (ttlMillis <= 0) return;
        try {
            redis.opsForValue()
                    .set(
                            key,
                            json.writeValueAsString(
                                    new Entry(VALUE_VERSION, candidate.invite(), expiry)),
                            Duration.ofMillis(ttlMillis));
        } catch (DataAccessException | JacksonException unavailable) {
            logUnavailable("populate", unavailable);
        }
    }

    private static boolean valid(Entry entry, String ref) {
        return entry != null
                && entry.version() == VALUE_VERSION
                && entry.invite() != null
                && ref.equals(entry.invite().ref())
                && entry.expiresAt() != null
                && Instant.now().isBefore(entry.expiresAt());
    }

    private String profile() {
        return environment.getProperty("spring.profiles.active", "local");
    }

    private static void logUnavailable(String operation, RuntimeException failure) {
        log.warn(
                "Invite-ref cache unavailable; PostgreSQL remains authoritative (operation={}, cause={})",
                operation,
                failure.getClass().getSimpleName());
    }
}
