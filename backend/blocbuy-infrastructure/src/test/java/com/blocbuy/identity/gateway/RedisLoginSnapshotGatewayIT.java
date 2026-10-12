package com.blocbuy.identity.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import cn.dev33.satoken.json.SaJsonTemplate;
import cn.dev33.satoken.json.SaJsonTemplateForJackson3;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.config.SessionStpInterface;
import com.blocbuy.common.constant.SessionRbac;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisLoginSnapshotGatewayIT {
    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.10").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static RedisLoginSnapshotGateway gateway;
    private static SaTokenDaoForRedisTemplate sessions;
    private static SaJsonTemplate originalJson;

    @BeforeAll
    static void connect() {
        // A direct gateway test does not run Boot's Sa-Token JSON bean injection.
        originalJson = SaManager.getSaJsonTemplate();
        SaManager.setSaJsonTemplate(new SaJsonTemplateForJackson3());
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        gateway = new RedisLoginSnapshotGateway(redis);
        sessions = new SaTokenDaoForRedisTemplate();
        sessions.init(factory);
    }

    @AfterAll
    static void disconnect() {
        if (factory != null) {
            factory.destroy();
        }
        SaManager.setSaJsonTemplate(originalJson);
    }

    @Test
    void newerRefreshWinsAndFrameworkMetadataCannotOverwriteIt() {
        UUID user = UUID.randomUUID();
        String older = gateway.beginRefresh(user);
        String newer = gateway.beginRefresh(user);
        assertThat(gateway.publish(user, newer, List.of("buyer"), List.of("order:read"), 60))
                .isTrue();
        assertThat(gateway.publish(user, older, List.of("admin"), List.of("payment:read"), 60))
                .isFalse();
        var metadata = new SaSession(StpUtil.getStpLogic().splicingKeySession(user));
        metadata.getDataMap()
                .put(
                        SessionRbac.SNAPSHOT,
                        java.util.Map.of(
                                "roles", List.of("admin"), "permissions", List.of("payment:read")));
        sessions.setSession(metadata, 60);
        var rbac = new SessionStpInterface(sessions);
        assertThat(rbac.getRoleList(user, StpUtil.getLoginType())).containsExactly("buyer");
        assertThat(rbac.getPermissionList(user, StpUtil.getLoginType()))
                .containsExactly("order:read");
        assertThat(redis.getExpire(id(user))).isBetween(1L, 60L);
        assertThat(redis.hasKey(id(user) + ":refresh")).isFalse();
    }

    @Test
    void expiredFenceAndInvalidTtlNeverPublish() {
        UUID user = UUID.randomUUID();
        String refresh = gateway.beginRefresh(user);
        assertThat(redis.getExpire(id(user) + ":refresh")).isBetween(1L, 120L);
        assertThat(gateway.publish(user, refresh, List.of(), List.of(), 0)).isFalse();
        assertThat(gateway.publish(user, refresh, List.of(), List.of(), -2)).isFalse();
        redis.delete(id(user) + ":refresh");
        assertThat(gateway.publish(user, refresh, List.of("admin"), List.of("payment:read"), 60))
                .isFalse();
        assertThat(sessions.getSession(id(user))).isNull();
    }

    @Test
    void supportsTheFrameworksExplicitNonExpiringSessionSetting() {
        UUID user = UUID.randomUUID();
        String refresh = gateway.beginRefresh(user);
        assertThat(gateway.publish(user, refresh, List.of("admin"), List.of("payment:read"), -1))
                .isTrue();
        assertThat(redis.getExpire(id(user))).isEqualTo(-1L);
        assertThat(gateway.publish(user, refresh, List.of("buyer"), List.of(), 60)).isFalse();
    }

    private static String id(UUID user) {
        return SessionRbac.sessionId(StpUtil.getTokenName(), StpUtil.getLoginType(), user);
    }
}
