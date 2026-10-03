package com.blocbuy.groupbuy.gateway;

import com.blocbuy.common.config.RedisKeys;
import com.blocbuy.groupbuy.query.OpenDealOffer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Groupbuy-owned derived offers, one key per product requested within the last 25 seconds. Positive
 * entries live 15-25 seconds; absences live 5-10 seconds, with jitter on every key. Reads use one
 * MGET; writes use one bounded Lua batch with SET PX on every entry. Redis failures never change
 * source results or purchase admission. Values are versioned JSON, not Java objects.
 */
@Slf4j
@Component
public class RedisOpenDealOfferCache implements OpenDealOfferCache {
    private static final String ACTIVE_PROFILE_PROPERTY = "spring.profiles.active";
    private static final String DEFAULT_PROFILE = "local";
    private static final RedisScript<Long> POPULATE =
            RedisScript.of(
                    """
            for i = 1, #KEYS do
                redis.call('SET', KEYS[i], ARGV[2*i-1], 'PX', ARGV[2*i])
            end
            return #KEYS
            """,
                    Long.class);
    private final StringRedisTemplate redis;
    private final Environment environment;
    private final ObjectMapper json;

    @SuppressFBWarnings("EI_EXPOSE_REP2") // Spring-owned mutable collaborators.
    public RedisOpenDealOfferCache(
            StringRedisTemplate redis, Environment environment, ObjectMapper json) {
        this.redis = redis;
        this.environment = environment;
        this.json = json;
    }

    record Entry(int version, OpenDealOffer offer) {}

    @Override
    public Lookup findByProductIds(Collection<UUID> productIds) {
        if (productIds.isEmpty()) return new Lookup(Map.of(), true);
        List<UUID> ids = productIds.stream().distinct().toList();
        try {
            String profile = environment.getProperty(ACTIVE_PROFILE_PROPERTY, DEFAULT_PROFILE);
            List<String> values =
                    redis.opsForValue().multiGet(ids.stream().map(id -> key(profile, id)).toList());
            if (values == null) return new Lookup(Map.of(), false);
            Map<UUID, Optional<OpenDealOffer>> hits = new HashMap<>();
            Instant now = Instant.now();
            for (int i = 0; i < ids.size(); i++) {
                String value = values.get(i);
                if (value != null) {
                    addValidHit(hits, ids.get(i), value, now);
                }
            }
            return new Lookup(hits, true);
        } catch (DataAccessException unavailable) {
            unavailable("read", unavailable);
            return new Lookup(Map.of(), false);
        }
    }

    private void addValidHit(
            Map<UUID, Optional<OpenDealOffer>> hits, UUID productId, String value, Instant now) {
        try {
            Entry entry = json.readValue(value, Entry.class);
            if (entry == null || entry.version() != 1) return;
            OpenDealOffer offer = entry.offer();
            if (offer == null
                    || (productId.equals(offer.productId())
                            && now.isBefore(offer.expireTime())
                            && offer.currentSize() < offer.targetSize())) {
                hits.put(productId, Optional.ofNullable(offer));
            }
        } catch (JacksonException malformed) {
            log.warn(
                    "Invalid best-offer cache value; loading from PostgreSQL (cause={})",
                    malformed.getClass().getSimpleName());
        }
    }

    @Override
    public void putAll(Map<UUID, Optional<OpenDealOffer>> offers) {
        if (offers.isEmpty()) return;
        try {
            String profile = environment.getProperty(ACTIVE_PROFILE_PROPERTY, DEFAULT_PROFILE);
            List<String> keys = new ArrayList<>(offers.size());
            List<String> arguments = new ArrayList<>(offers.size() * 2);
            for (var item : offers.entrySet()) {
                keys.add(key(profile, item.getKey()));
                arguments.add(json.writeValueAsString(new Entry(1, item.getValue().orElse(null))));
                boolean present = item.getValue().isPresent();
                arguments.add(
                        Long.toString(
                                ThreadLocalRandom.current()
                                        .nextLong(
                                                present ? 15_000 : 5_000,
                                                present ? 25_001 : 10_001)));
            }
            redis.execute(POPULATE, keys, arguments.toArray());
        } catch (DataAccessException | JacksonException unavailable) {
            unavailable("populate", unavailable);
        }
    }

    @Override
    public void evict(Collection<UUID> productIds) {
        if (productIds.isEmpty()) return;
        try {
            String profile = environment.getProperty(ACTIVE_PROFILE_PROPERTY, DEFAULT_PROFILE);
            redis.delete(productIds.stream().distinct().map(id -> key(profile, id)).toList());
        } catch (DataAccessException unavailable) {
            unavailable("evict", unavailable);
        }
    }

    private static String key(String profile, UUID productId) {
        return RedisKeys.key(profile, "groupbuy", "best-open-deal", productId.toString());
    }

    private static void unavailable(String operation, RuntimeException failure) {
        log.warn(
                "Best-offer cache unavailable; PostgreSQL remains authoritative (operation={}, cause={})",
                operation,
                failure.getClass().getSimpleName());
    }
}
