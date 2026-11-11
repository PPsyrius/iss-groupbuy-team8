package fixtures.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.config.SaTokenConfig;
import com.blocbuy.common.constant.SessionRbac;
import com.blocbuy.common.web.GlobalExceptionHandler;
import com.blocbuy.common.web.Result;
import com.blocbuy.identity.gateway.RedisLoginSnapshotGateway;
import com.blocbuy.identity.service.LoginAuthorizationService;
import com.blocbuy.identity.service.LoginAuthorizationService.Snapshot;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.identity.web.AuthController;
import com.blocbuy.identity.web.LoginSessionPublisher;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Actual callback, production Redis DAO/publisher and distributed permission checks. */
@SpringBootTest(
        classes = LoginSessionRedisIT.Assembly.class,
        properties = {
            "blocbuy.topology=distributed",
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/unused",
            "spring.datasource.username=test",
            "spring.datasource.password=test"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class LoginSessionRedisIT {
    private static final String CALLBACK = "/api/v1/auth/oauth2/google/callback";
    private static final String FAILED = "http://localhost:5173/login?error=COMMON_500_001";

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.10").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry properties) {
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.data.redis.password", () -> "");
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private OAuthLoginAppService oauth;
    @MockitoBean private UserAppService users;
    @MockitoBean private LoginAuthorizationService authorization;
    @MockitoSpyBean private RedisLoginSnapshotGateway snapshots;
    private UUID user;

    @BeforeEach
    void loginIdentity() {
        user = UUID.randomUUID();
        when(oauth.loginWithGoogle(any(), any(), any()))
                .thenReturn(new OAuthLoginAppService.OAuthLoginResult(user, "Tester", null));
        when(authorization.snapshot(user))
                .thenReturn(new Snapshot(List.of("admin"), List.of("payment:read")));
    }

    @Test
    void actualCallbackAuthorizesThroughRedisAndReloginRefreshesAllTokens() throws Exception {
        Cookie first = login();
        assertThat(first.isHttpOnly()).isTrue();
        mvc.perform(get("/api/v1/admin/probe").cookie(first)).andExpect(status().isOk());
        when(authorization.snapshot(user))
                .thenReturn(new Snapshot(List.of("buyer"), List.of("order:read")));
        Cookie second = login();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        mvc.perform(get("/api/v1/admin/probe").cookie(first)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/session/probe").cookie(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("buyer"));
    }

    @Test
    void snapshotReadFailureRedirectsWithoutCreatingAToken() throws Exception {
        when(authorization.snapshot(user))
                .thenThrow(new IllegalStateException("database unavailable"));
        var response =
                mvc.perform(callback())
                        .andExpect(status().isFound())
                        .andExpect(header().string(HttpHeaders.LOCATION, FAILED))
                        .andReturn()
                        .getResponse();
        assertThat(response.getCookie("blocbuy-token")).isNull();
        assertThat(StpUtil.getTokenValueListByLoginId(user)).isEmpty();
    }

    @Test
    void snapshotWriteFailureRevokesTheNewTokenAndPreservesTheExistingLogin() throws Exception {
        Cookie existing = login();
        AtomicReference<String> rejected = new AtomicReference<>();
        doAnswer(
                        invocation -> {
                            rejected.set(
                                    StpUtil.getTokenValueListByLoginId(user).stream()
                                            .filter(token -> !token.equals(existing.getValue()))
                                            .findFirst()
                                            .orElseThrow());
                            throw new IllegalStateException("injected Redis publication failure");
                        })
                .when(snapshots)
                .publish(eq(user), anyString(), anyList(), anyList(), anyLong());
        var response =
                mvc.perform(callback())
                        .andExpect(status().isFound())
                        .andExpect(header().string(HttpHeaders.LOCATION, FAILED))
                        .andReturn()
                        .getResponse();
        assertThat(response.getCookie("blocbuy-token")).isNull();
        assertThat(rejected.get()).isNotNull();
        assertThat(StpUtil.getLoginIdByToken(rejected.get())).isNull();
        assertThat(StpUtil.getTokenValueListByLoginId(user)).containsExactly(existing.getValue());
        mvc.perform(get("/api/v1/admin/probe").cookie(existing)).andExpect(status().isOk());
    }

    @Test
    void delayedOlderCallbackCannotRestorePrivilegesAfterANewerLogin() throws Exception {
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(authorization.snapshot(user))
                .thenAnswer(invocation -> {
                    if (calls.incrementAndGet() == 1) {
                        firstRead.countDown();
                        if (!releaseFirst.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test callback timed out");
                        }
                        return new Snapshot(List.of("admin"), List.of("payment:read"));
                    }
                    return new Snapshot(List.of("buyer"), List.of("order:read"));
                });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var older = executor.submit(() -> mvc.perform(callback()).andReturn().getResponse());
            try {
                assertThat(firstRead.await(10, TimeUnit.SECONDS)).isTrue();
                Cookie newer = login();
                releaseFirst.countDown();
                var failed = older.get(20, TimeUnit.SECONDS);
                assertThat(failed.getStatus()).isEqualTo(302);
                assertThat(failed.getHeader(HttpHeaders.LOCATION)).isEqualTo(FAILED);
                assertThat(failed.getCookie("blocbuy-token")).isNull();
                assertThat(StpUtil.getTokenValueListByLoginId(user))
                        .containsExactly(newer.getValue());
                mvc.perform(get("/api/v1/session/probe").cookie(newer))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data[0]").value("buyer"));
                mvc.perform(get("/api/v1/admin/probe").cookie(newer))
                        .andExpect(status().isForbidden());
            } finally {
                releaseFirst.countDown();
            }
        }
    }

    @Test
    void partialMalformedSnapshotCannotAuthorizeAPermissionOnlyEndpoint() throws Exception {
        Cookie cookie = login();
        var dao = SaManager.getSaTokenDao();
        var session = dao.getSession(sessionId());
        session.getDataMap()
                .put(
                        SessionRbac.SNAPSHOT,
                        Map.of("roles", "malformed", "permissions", List.of("payment:read")));
        dao.updateSession(session);
        mvc.perform(get("/api/v1/permission/probe").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingAuthorizationSessionNeverFallsBackToAccountMetadata() throws Exception {
        Cookie cookie = login();
        SaManager.getSaTokenDao().deleteSession(sessionId());
        StpUtil.getSessionByLoginId(user, false)
                .set(
                        SessionRbac.SNAPSHOT,
                        Map.of("roles", List.of("admin"), "permissions", List.of("payment:read")));
        mvc.perform(get("/api/v1/admin/probe").cookie(cookie)).andExpect(status().isForbidden());
    }

    @Test
    void refreshBetweenRoleAndPermissionChecksCannotMixPrivileges() throws Exception {
        when(authorization.snapshot(user)).thenReturn(new Snapshot(List.of("admin"), List.of()));
        Cookie cookie = login();
        mvc.perform(get("/api/v1/mixed/probe").cookie(cookie)).andExpect(status().isForbidden());
        // The refresh really happened, but only subsequent requests see it.
        mvc.perform(get("/api/v1/permission/probe").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/probe").cookie(cookie)).andExpect(status().isForbidden());
    }

    private Cookie login() throws Exception {
        return mvc.perform(callback())
                .andExpect(status().isFound())
                .andExpect(
                        header().string(
                                        HttpHeaders.LOCATION,
                                        "http://localhost:5173/auth/callback"))
                .andReturn()
                .getResponse()
                .getCookie("blocbuy-token");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            callback() {
        return get(CALLBACK).param("code", "code").param("state", "state");
    }

    private String sessionId() {
        return SessionRbac.sessionId(StpUtil.getTokenName(), StpUtil.getLoginType(), user);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({
        AuthController.class,
        LoginSessionPublisher.class,
        RedisLoginSnapshotGateway.class,
        SaTokenConfig.class,
        GlobalExceptionHandler.class,
        ProtectedEndpoints.class
    })
    static class Assembly {}

    @RestController
    @RequiredArgsConstructor
    static class ProtectedEndpoints {
        private final RedisLoginSnapshotGateway snapshots;

        @GetMapping("/api/v1/mixed/probe")
        Result<Void> mixed() {
            StpUtil.checkRole("admin");
            UUID user = UUID.fromString(StpUtil.getLoginIdAsString());
            snapshots.publish(
                    user,
                    snapshots.beginRefresh(user),
                    List.of("buyer"),
                    List.of("payment:read"),
                    StpUtil.getStpLogic().getSessionTimeoutByLoginId(user));
            StpUtil.checkPermission("payment:read");
            return Result.ok();
        }

        @GetMapping("/api/v1/admin/probe")
        Result<Void> admin() {
            StpUtil.checkRole("admin");
            StpUtil.checkPermission("payment:read");
            return Result.ok();
        }

        @GetMapping("/api/v1/permission/probe")
        Result<Void> permission() {
            StpUtil.checkPermission("payment:read");
            return Result.ok();
        }

        @GetMapping("/api/v1/session/probe")
        Result<List<String>> roles() {
            return Result.ok(StpUtil.getRoleList());
        }
    }
}
