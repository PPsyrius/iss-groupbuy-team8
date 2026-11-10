package com.blocbuy.identity.gateway;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.SessionRbac;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Opaque Sa-Token session JSON, atomically published only by the latest refresh attempt. */
@Component
@RequiredArgsConstructor
public class RedisLoginSnapshotGateway implements LoginSnapshotGateway {
    // One short-lived fence and one authorization session per account; no permanent counters.
    private static final Duration REFRESH_TIMEOUT = Duration.ofMinutes(2);
    private static final DefaultRedisScript<Long> PUBLISH =
            new DefaultRedisScript<>(
                    """
                    if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
                    local ttl = tonumber(ARGV[3])
                    if ttl == -1 then
                        redis.call('SET', KEYS[2], ARGV[2])
                    elseif ttl > 0 then
                        redis.call('SET', KEYS[2], ARGV[2], 'EX', ttl)
                    else
                        return 0
                    end
                    redis.call('DEL', KEYS[1])
                    return 1
                    """,
                    Long.class);

    private final StringRedisTemplate redis;

    @Override
    public String beginRefresh(UUID userId) {
        String refresh = UUID.randomUUID().toString();
        redis.opsForValue().set(sessionId(userId) + ":refresh", refresh, REFRESH_TIMEOUT);
        return refresh;
    }

    @Override
    public boolean publish(
            UUID userId,
            String refresh,
            List<String> roles,
            List<String> permissions,
            long timeoutSeconds) {
        String id = sessionId(userId);
        SaSession session =
                new SaSession(id).setLoginId(userId).setLoginType(StpUtil.getLoginType());
        // Do not call SaSession.set: it persists before our atomic freshness check.
        session.getDataMap()
                .put(
                        SessionRbac.SNAPSHOT,
                        Map.of(
                                SessionRbac.ROLES,
                                List.copyOf(roles),
                                SessionRbac.PERMISSIONS,
                                List.copyOf(permissions)));
        String serialized = SaManager.getSaSerializerTemplate().objectToString(session);
        Long result =
                redis.execute(
                        PUBLISH,
                        List.of(id + ":refresh", id),
                        refresh,
                        serialized,
                        Long.toString(timeoutSeconds));
        return Long.valueOf(1).equals(result);
    }

    private static String sessionId(UUID userId) {
        return SessionRbac.sessionId(StpUtil.getTokenName(), StpUtil.getLoginType(), userId);
    }
}
