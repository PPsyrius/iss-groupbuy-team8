package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.SessionRbac;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.gateway.LoginSnapshotGateway;
import com.blocbuy.identity.repository.RoleRepository;
import com.blocbuy.identity.service.LoginAuthorizationService;
import com.blocbuy.identity.service.LoginAuthorizationService.Snapshot;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.identity.service.OAuthLoginAppService.GoogleAuthorizeUrl;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.identity.service.UserAppService.CurrentUser;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Browser handshake contract of the Google OAuth flow: the callback never answers the browser with
 * a JSON body — success and failure both 302 to the configured frontend targets (local profile: the
 * Vite dev server), and the session token rides only the HttpOnly cookie. {@code /me} stays a plain
 * JSON endpoint guarded by the Sa-Token interceptor. Only application services and the RBAC
 * repository are mocked; the test-only session store never connects to a developer's Redis.
 */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/blocbuy_placeholder?currentSchema=domain",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1"
        })
@AutoConfigureMockMvc
@Import(AuthControllerTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerTest {
    private static final String CALLBACK_PATH = "/api/v1/auth/oauth2/google/callback";
    private static final String ME_PATH = "/api/v1/auth/me";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";
    private static final UUID USER = UUID.fromString("a0000000-0000-7000-8000-000000000042");
    private static final String FRONTEND = "http://localhost:5173";

    @Autowired private MockMvc mvc;
    @MockitoBean private OAuthLoginAppService oAuthLogin;
    @MockitoBean private UserAppService users;
    @MockitoBean private RoleRepository roles;
    @MockitoBean private LoginAuthorizationService authorization;

    @BeforeEach
    void defaultAuthorization() {
        when(authorization.snapshot(USER)).thenReturn(new Snapshot(List.of(), List.of()));
    }

    @Test
    void callbackSuccessOpensSessionAndRedirectsToFrontend() throws Exception {
        when(oAuthLogin.loginWithGoogle("code", "state", "binding-123"))
                .thenReturn(new OAuthLoginResult(USER, "Tester", "https://img.example/a.png"));
        when(roles.findRoleCodesByUserId(USER)).thenReturn(List.of("buyer"));
        when(roles.findPermissionCodesByUserId(USER)).thenReturn(List.of("order:read"));
        when(authorization.snapshot(USER))
                .thenReturn(new Snapshot(List.of("buyer"), List.of("order:read")));

        MvcResult callback =
                mvc.perform(
                                get(CALLBACK_PATH)
                                        .param("code", "code")
                                        .param("state", "state")
                                        .cookie(new Cookie("blocbuy-oauth-state", "binding-123")))
                        .andExpect(status().isFound())
                        .andExpect(
                                header().string(HttpHeaders.LOCATION, FRONTEND + "/auth/callback"))
                        .andReturn();
        verify(oAuthLogin).loginWithGoogle("code", "state", "binding-123");

        Cookie session = callback.getResponse().getCookie("blocbuy-token");
        assertThat(session).as("Sa-Token session cookie issued on login").isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getSecure()).isFalse();
        assertThat(session.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(authorizationSession(USER).get(SessionRbac.SNAPSHOT))
                .isEqualTo(Map.of("roles", List.of("buyer"), "permissions", List.of("order:read")));

        when(users.currentUser(USER))
                .thenReturn(new CurrentUser(USER, "Tester", "https://img.example/a.png"));
        when(roles.findRoleCodesByUserId(USER)).thenReturn(List.of("buyer"));
        mvc.perform(get(ME_PATH).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.userId").value(USER.toString()))
                .andExpect(jsonPath("$.data.nickname").value("Tester"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://img.example/a.png"))
                .andExpect(jsonPath("$.data.roles[0]").value("buyer"));
    }

    @Test
    void callbackFailureRedirectsToFrontendWithErrorCode() throws Exception {
        when(oAuthLogin.loginWithGoogle(any(), any(), isNull()))
                .thenThrow(new BizException(IdentityErrorCode.OAUTH_STATE_INVALID));

        mvc.perform(get(CALLBACK_PATH).param("code", "bad").param("state", "bad"))
                .andExpect(status().isFound())
                .andExpect(
                        header().string(
                                        HttpHeaders.LOCATION,
                                        FRONTEND + "/login?error=IDENTITY_401_001"));
    }

    @Test
    void authorizationFailureNeverOpensASession() throws Exception {
        when(oAuthLogin.loginWithGoogle("code", "state", null))
                .thenReturn(new OAuthLoginResult(UUID.randomUUID(), "Tester", null));
        when(authorization.snapshot(any()))
                .thenThrow(new IllegalStateException("snapshot unavailable"));
        var response =
                mvc.perform(get(CALLBACK_PATH).param("code", "code").param("state", "state"))
                        .andExpect(status().isFound())
                        .andExpect(
                                header().string(
                                                HttpHeaders.LOCATION,
                                                FRONTEND + "/login?error=COMMON_500_001"))
                        .andReturn()
                        .getResponse();
        assertThat(response.getCookie("blocbuy-token")).isNull();
    }

    @Test
    void nextLoginReplacesTheSharedAccountSnapshotForExistingTokens() throws Exception {
        UUID user = UUID.randomUUID();
        when(oAuthLogin.loginWithGoogle("code", "state", null))
                .thenReturn(new OAuthLoginResult(user, "Tester", null));
        when(authorization.snapshot(user))
                .thenReturn(new Snapshot(List.of("admin"), List.of("payment:read")));
        var first =
                mvc.perform(get(CALLBACK_PATH).param("code", "code").param("state", "state"))
                        .andExpect(status().isFound())
                        .andReturn()
                        .getResponse()
                        .getCookie("blocbuy-token");
        var session = authorizationSession(user);
        assertThat(session.get(SessionRbac.SNAPSHOT))
                .isEqualTo(
                        Map.of("roles", List.of("admin"), "permissions", List.of("payment:read")));
        when(authorization.snapshot(user)).thenReturn(new Snapshot(List.of("buyer"), List.of()));
        var second =
                mvc.perform(get(CALLBACK_PATH).param("code", "code").param("state", "state"))
                        .andExpect(status().isFound())
                        .andReturn()
                        .getResponse()
                        .getCookie("blocbuy-token");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        assertThat(StpUtil.getLoginIdByToken(first.getValue())).hasToString(user.toString());
        assertThat(authorizationSession(user).get(SessionRbac.SNAPSHOT))
                .isEqualTo(Map.of("roles", List.of("buyer"), "permissions", List.of()));
    }

    /** Consent denial arrives as ?error=access_denied with no code — still a browser 302. */
    @Test
    void callbackAccessDeniedRedirectsWithFixedErrorCode() throws Exception {
        MvcResult denied =
                mvc.perform(get(CALLBACK_PATH).param("error", "access_denied").param("state", "x"))
                        .andExpect(status().isFound())
                        .andExpect(
                                header().string(
                                                HttpHeaders.LOCATION,
                                                FRONTEND + "/login?error=IDENTITY_401_003"))
                        .andReturn();

        assertThat(denied.getResponse().getCookie("blocbuy-token"))
                .as("denial never opens a session")
                .isNull();
        verifyNoInteractions(oAuthLogin);
    }

    /** A malformed hit missing code redirects too — the callback never answers raw 400 JSON. */
    @Test
    void callbackMissingCodeRedirectsInsteadOfRaw400() throws Exception {
        mvc.perform(get(CALLBACK_PATH).param("state", "x"))
                .andExpect(status().isFound())
                .andExpect(
                        header().string(
                                        HttpHeaders.LOCATION,
                                        FRONTEND + "/login?error=COMMON_400_001"));
        mvc.perform(get(CALLBACK_PATH).param("code", " ").param("state", "x"))
                .andExpect(status().isFound())
                .andExpect(
                        header().string(
                                        HttpHeaders.LOCATION,
                                        FRONTEND + "/login?error=COMMON_400_001"));
        mvc.perform(get(CALLBACK_PATH).param("code", "c"))
                .andExpect(status().isFound())
                .andExpect(
                        header().string(
                                        HttpHeaders.LOCATION,
                                        FRONTEND + "/login?error=COMMON_400_001"));
        mvc.perform(get(CALLBACK_PATH).param("code", "c").param("state", " "))
                .andExpect(status().isFound())
                .andExpect(
                        header().string(
                                        HttpHeaders.LOCATION,
                                        FRONTEND + "/login?error=COMMON_400_001"));
        verifyNoInteractions(oAuthLogin);
    }

    /** Provider error values other than access_denied map to the provider-failure code. */
    @Test
    void callbackProviderErrorRedirectsWithProviderErrorCode() throws Exception {
        mvc.perform(get(CALLBACK_PATH).param("error", "server_error").param("state", "x"))
                .andExpect(status().isFound())
                .andExpect(
                        header().string(
                                        HttpHeaders.LOCATION,
                                        FRONTEND + "/login?error=IDENTITY_502_001"));
        verifyNoInteractions(oAuthLogin);
    }

    /**
     * The authorize hop 302s to Google and sets the HttpOnly, SameSite=Lax state-binding cookie.
     */
    @Test
    void authorizeRedirectsToGoogleAndSetsStateBindingCookie() throws Exception {
        when(oAuthLogin.createGoogleAuthorizeUrl())
                .thenReturn(
                        new GoogleAuthorizeUrl(
                                "https://accounts.google.com/o/oauth2/v2/auth?state=abc",
                                "binding-123"));

        MvcResult authorize =
                mvc.perform(get("/api/v1/auth/oauth2/google"))
                        .andExpect(status().isFound())
                        .andExpect(
                                header().string(
                                                HttpHeaders.LOCATION,
                                                "https://accounts.google.com/o/oauth2/v2/auth?state=abc"))
                        .andReturn();

        String setCookie = authorize.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains("blocbuy-oauth-state=binding-123")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/api/v1/auth")
                // local profile is plain HTTP (state-cookie-secure=false) — no Secure attribute
                .doesNotContain("Secure");
    }

    @Test
    void authorizeStateCookiePathIncludesServletContextPath() throws Exception {
        when(oAuthLogin.createGoogleAuthorizeUrl())
                .thenReturn(new GoogleAuthorizeUrl("https://accounts.google.com", "binding-123"));

        MvcResult authorize =
                mvc.perform(get("/gateway/api/v1/auth/oauth2/google").contextPath("/gateway"))
                        .andExpect(status().isFound())
                        .andReturn();

        assertThat(authorize.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("Path=/gateway/api/v1/auth");
    }

    /** When the authorization URL cannot be built, the browser still lands on the failure page. */
    @Test
    void authorizeFailureRedirectsToFrontendWithErrorCode() throws Exception {
        when(oAuthLogin.createGoogleAuthorizeUrl())
                .thenThrow(new BizException(IdentityErrorCode.IDENTITY_MISCONFIGURED));

        MvcResult authorize =
                mvc.perform(get("/api/v1/auth/oauth2/google"))
                        .andExpect(status().isFound())
                        .andExpect(
                                header().string(
                                                HttpHeaders.LOCATION,
                                                FRONTEND + "/login?error=IDENTITY_500_001"))
                        .andReturn();

        assertThat(authorize.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .as("no state cookie is issued when the flow never starts")
                .isNull();
    }

    @Test
    void meWithoutSessionIsRejectedByTheInterceptor() throws Exception {
        mvc.perform(get(ME_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401_001"));
        verifyNoInteractions(users);
    }

    /** Logout invalidates the token: the same session cookie is rejected afterwards. */
    @Test
    void logoutInvalidatesTheSession() throws Exception {
        when(oAuthLogin.loginWithGoogle("code", "state", null))
                .thenReturn(new OAuthLoginResult(USER, "Tester", "https://img.example/a.png"));

        MvcResult callback =
                mvc.perform(get(CALLBACK_PATH).param("code", "code").param("state", "state"))
                        .andExpect(status().isFound())
                        .andReturn();
        Cookie session = callback.getResponse().getCookie("blocbuy-token");
        assertThat(session).as("session opened before logout").isNotNull();

        mvc.perform(post(LOGOUT_PATH).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"));

        mvc.perform(get(ME_PATH).cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401_001"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SessionStore {
        @Bean(initMethod = "init", destroyMethod = "destroy")
        @Primary
        SaTokenDao testSessionDao() {
            return new SaTokenDaoDefaultImpl();
        }

        @Bean
        @Primary
        LoginSnapshotGateway testSnapshots(SaTokenDao sessions) {
            return new LoginSnapshotGateway() {
                private final Map<UUID, String> refreshes = new ConcurrentHashMap<>();

                @Override
                public String beginRefresh(UUID userId) {
                    String refresh = UUID.randomUUID().toString();
                    refreshes.put(userId, refresh);
                    return refresh;
                }

                @Override
                public synchronized boolean publish(
                        UUID userId,
                        String refresh,
                        List<String> roles,
                        List<String> permissions,
                        long timeout) {
                    if (!refreshes.remove(userId, refresh)) {
                        return false;
                    }
                    var session =
                            new SaSession(
                                    SessionRbac.sessionId(
                                            StpUtil.getTokenName(),
                                            StpUtil.getLoginType(),
                                            userId));
                    session.getDataMap()
                            .put(
                                    SessionRbac.SNAPSHOT,
                                    Map.of("roles", roles, "permissions", permissions));
                    sessions.setSession(session, timeout);
                    return true;
                }
            };
        }
    }

    private static SaSession authorizationSession(UUID user) {
        return SaManager.getSaTokenDao()
                .getSession(
                        SessionRbac.sessionId(
                                StpUtil.getTokenName(), StpUtil.getLoginType(), user));
    }
}
