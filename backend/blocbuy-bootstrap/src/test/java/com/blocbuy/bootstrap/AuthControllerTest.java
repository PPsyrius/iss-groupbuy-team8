package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.repository.RoleRepository;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.identity.service.OAuthLoginAppService.GoogleAuthorizeUrl;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.identity.service.UserAppService.CurrentUser;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
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

    @Test
    void callbackSuccessOpensSessionAndRedirectsToFrontend() throws Exception {
        when(oAuthLogin.loginWithGoogle("code", "state", null))
                .thenReturn(new OAuthLoginResult(USER, "Tester", "https://img.example/a.png"));

        MvcResult callback =
                mvc.perform(get(CALLBACK_PATH).param("code", "code").param("state", "state"))
                        .andExpect(status().isFound())
                        .andExpect(
                                header().string(HttpHeaders.LOCATION, FRONTEND + "/auth/callback"))
                        .andReturn();

        Cookie session = callback.getResponse().getCookie("blocbuy-token");
        assertThat(session).as("Sa-Token session cookie issued on login").isNotNull();

        when(users.currentUser(USER))
                .thenReturn(new CurrentUser(USER, "Tester", "https://img.example/a.png"));
        mvc.perform(get(ME_PATH).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.userId").value(USER.toString()))
                .andExpect(jsonPath("$.data.nickname").value("Tester"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://img.example/a.png"));
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
    }
}
