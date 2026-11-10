package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.service.ProductAppService;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.web.Result;
import com.blocbuy.groupbuy.service.ShareInviteAppService;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.repository.RoleRepository;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.payment.service.PaymentAppService;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Pins the anonymous API surface (issue #536). Every handler registered under {@code /api/v1} is
 * called with no session; each must answer 401 {@code COMMON_401_001} unless it is on the explicit
 * {@link #ANONYMOUS_ALLOW_LIST}. A new endpoint that is public by accident therefore fails this
 * test, whether the leak comes from {@code SaTokenConfig} (the interceptor lets GETs under {@code
 * /api/v1/products/**} through) or from a missing in-controller check. Each probe must resolve to
 * its enumerated handler; conditional or overlapping mappings cannot silently reuse another
 * handler's result.
 *
 * <p>Only the {@code /api/v1/**} prefix is guarded by the Sa-Token interceptor, so a second test
 * requires every project controller to sit under it. Springdoc, the actuator and {@code /error} are
 * served outside the prefix and are not covered here (issue #536, non-goals). Only the application
 * services behind the allow-listed endpoints and the RBAC repository are mocked, and the test-only
 * session store never connects to Redis, so no Docker is needed.
 *
 * <p>Scope limits: this is the monolith topology, so {@code InternalUserController} ({@code
 * /internal/users/**}, distributed topology only) is not in this context. Router functions are not
 * enumerated, and a mapping with no HTTP method is probed with GET only, not the other methods.
 */
@SpringBootTest(
        properties = {
            "blocbuy.topology=monolith",
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/blocbuy_placeholder?currentSchema=domain",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1"
        })
@AutoConfigureMockMvc
@Import(EndpointAuthSurfaceTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EndpointAuthSurfaceTest {
    private static final String API_V1 = "/api/v1/";
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^/}]+}");
    private static final String SAMPLE_ID = "00000000-0000-7000-8000-000000000001";
    private static final String SAMPLE_INVITE_REF = "a".repeat(22);
    private static final String UNAUTHENTICATED = "COMMON_401_001";

    /**
     * Endpoints that may answer a caller with no session, each with the reason it is public. This
     * list is derived from {@code SaTokenConfig} ({@code ANONYMOUS_GET_PATHS}, {@code
     * PUBLIC_PATHS}) and the controllers. Add an entry only for an endpoint that is meant to be
     * public; everything else must stay behind login.
     */
    private static final Map<String, AnonymousExpectation> ANONYMOUS_ALLOW_LIST =
            new TreeMap<>(
                    Map.of(
                            "GET /api/v1/products",
                            new AnonymousExpectation(
                                    "anonymous catalog browse (ANONYMOUS_GET_PATHS)", 200),
                            "GET /api/v1/products/{id}",
                            new AnonymousExpectation(
                                    "anonymous listing detail (ANONYMOUS_GET_PATHS)", 404),
                            "GET /api/v1/invites/{ref}",
                            new AnonymousExpectation(
                                    "invite resolve before sign-in (ANONYMOUS_GET_PATHS, #201)",
                                    404),
                            "GET /api/v1/auth/oauth2/google",
                            new AnonymousExpectation(
                                    "starts the Google login redirect (PUBLIC_PATHS oauth2/**)",
                                    302),
                            "GET /api/v1/auth/oauth2/google/callback",
                            new AnonymousExpectation(
                                    "provider callback opens the session (PUBLIC_PATHS oauth2/**)",
                                    302),
                            "POST /api/v1/webhooks/stripe",
                            new AnonymousExpectation(
                                    "Stripe callback, signature-verified (PUBLIC_PATHS webhooks/**)",
                                    200)));

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Autowired private MockMvc mvc;
    @MockitoBean private RoleRepository roles;
    @MockitoBean private ProductAppService products;
    @MockitoBean private ShareInviteAppService invites;
    @MockitoBean private OAuthLoginAppService oauth;
    @MockitoBean private PaymentAppService payments;

    @BeforeEach
    void stubAllowListedBusinessCalls() {
        when(products.pageListings(any(), any(), any()))
                .thenReturn(PageResult.of(List.of(), 0, PageQuery.of(null, null)));
        when(products.getListingDetail(any()))
                .thenThrow(new BizException(CatalogErrorCode.PRODUCT_NOT_FOUND));
        when(invites.resolve(any(), any())).thenThrow(new BizException(CommonErrorCode.NOT_FOUND));
        when(oauth.createGoogleAuthorizeUrl())
                .thenThrow(new BizException(IdentityErrorCode.OAUTH_PROVIDER_ERROR));
    }

    @Test
    void everyApiV1EndpointRejectsAnonymousCallersUnlessAllowListed() {
        List<Endpoint> protectedEndpoints =
                apiV1Endpoints().stream()
                        .filter(endpoint -> !ANONYMOUS_ALLOW_LIST.containsKey(endpoint.key()))
                        .toList();
        // a floor (25 at the time of writing) rather than a count: it catches a partial or empty
        // enumeration, which would otherwise pass vacuously, without failing on every new endpoint
        assertThat(protectedEndpoints).hasSizeGreaterThanOrEqualTo(25);

        List<String> offenders = new ArrayList<>();
        for (Endpoint endpoint : protectedEndpoints) {
            try {
                MvcResult result = mvc.perform(endpoint.request()).andReturn();
                int httpStatus = result.getResponse().getStatus();
                String code = errorCode(result);
                if (!endpoint.handles(result)) {
                    offenders.add(endpoint.key() + " -> probe reached a different handler");
                } else if (httpStatus != 401 || !UNAUTHENTICATED.equals(code)) {
                    offenders.add(endpoint.key() + " -> " + httpStatus + " " + code);
                }
            } catch (Exception ex) {
                offenders.add(endpoint.key() + " -> threw " + ex);
            }
        }
        assertThat(offenders)
                .as("anonymous calls that were not rejected with 401 %s", UNAUTHENTICATED)
                .isEmpty();
        // the rejection happens before the mocked services behind the allow-listed endpoints
        verifyNoInteractions(products, invites, oauth, payments);
    }

    @Test
    void allowListedEndpointsAreReachableAnonymously() {
        List<String> offenders = new ArrayList<>();
        for (Endpoint endpoint : apiV1Endpoints()) {
            if (!ANONYMOUS_ALLOW_LIST.containsKey(endpoint.key())) {
                continue;
            }
            try {
                MvcResult result = mvc.perform(endpoint.request()).andReturn();
                int httpStatus = result.getResponse().getStatus();
                int expectedStatus = ANONYMOUS_ALLOW_LIST.get(endpoint.key()).status();
                if (!endpoint.handles(result)) {
                    offenders.add(endpoint.key() + " -> probe reached a different handler");
                } else if (httpStatus != expectedStatus) {
                    offenders.add(
                            endpoint.key()
                                    + " -> "
                                    + httpStatus
                                    + " (expected "
                                    + expectedStatus
                                    + ")");
                }
            } catch (Exception ex) {
                offenders.add(endpoint.key() + " -> threw " + ex);
            }
        }
        assertThat(offenders)
                .as("allow-listed endpoints with unexpected responses: %s", ANONYMOUS_ALLOW_LIST)
                .isEmpty();
    }

    @Test
    void allowListMatchesRegisteredEndpoints() {
        Set<String> registered = new TreeSet<>();
        apiV1Endpoints().forEach(endpoint -> registered.add(endpoint.key()));

        Set<String> stale = new TreeSet<>(ANONYMOUS_ALLOW_LIST.keySet());
        stale.removeAll(registered);
        assertThat(stale).as("allow-list entries with no matching endpoint").isEmpty();
    }

    @Test
    void noProjectControllerIsMappedOutsideTheGuardedPrefix() {
        List<String> offenders = new ArrayList<>();
        mappings.getHandlerMethods()
                .forEach((info, handler) -> {
                    String type = handler.getBeanType().getName();
                    for (String pattern : patterns(info)) {
                        boolean inPrefix = pattern.startsWith(API_V1);
                        boolean framework =
                                type.startsWith("org.springdoc.")
                                        || type.startsWith("org.springframework.boot.");
                        boolean project = type.startsWith("com.blocbuy.");
                        if (project ? !inPrefix : !(framework || inPrefix)) {
                            offenders.add(pattern + " (" + type + ")");
                        }
                    }
                });
        assertThat(offenders)
                .as("controllers outside /api/v1, which the Sa-Token interceptor does not cover")
                .isEmpty();
    }

    @Test
    void teamPricePolicyReadIsProtectedByTheControllerNotTheInterceptor() throws Exception {
        // GET /api/v1/products/** is anonymous in SaTokenConfig.ANONYMOUS_GET_PATHS, so only the
        // controller login/permission checks keep this admin read behind login.
        mvc.perform(get(API_V1 + "products/" + SAMPLE_ID + "/team-price-policy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHENTICATED));
    }

    @Test
    void protectedSweepRejectsAProbeThatReachesAnotherHandler() throws Exception {
        RequestMappingInfo variant =
                RequestMappingInfo.paths(API_V1 + "products/{productId}/team-price-policy")
                        .methods(RequestMethod.GET)
                        .consumes(MediaType.TEXT_PLAIN_VALUE)
                        .options(mappings.getBuilderConfiguration())
                        .build();
        mappings.registerMapping(
                variant, new AnonymousVariant(), AnonymousVariant.class.getMethod("read"));
        try {
            mvc.perform(
                            get(API_V1 + "products/" + SAMPLE_ID + "/team-price-policy")
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .content("probe"))
                    .andExpect(status().isOk());
            assertThatThrownBy(this::everyApiV1EndpointRejectsAnonymousCallersUnlessAllowListed)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("probe reached a different handler");
        } finally {
            mappings.unregisterMapping(variant);
        }
    }

    @Test
    void publicSweepRejectsServerErrorsInsteadOfCountingThemAsReachable() {
        when(products.pageListings(any(), any(), any()))
                .thenThrow(new IllegalStateException("public route failure fixture"));

        assertThatThrownBy(this::allowListedEndpointsAreReachableAnonymously)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("GET /api/v1/products -> 500");
    }

    @Test
    void publicInviteProbeReachesTheServiceWithAValidReference() {
        allowListedEndpointsAreReachableAnonymously();

        verify(invites).resolve(eq(SAMPLE_INVITE_REF), any());
    }

    private List<Endpoint> apiV1Endpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        mappings.getHandlerMethods()
                .forEach((info, handler) -> {
                    Set<String> methods = new TreeSet<>();
                    info.getMethodsCondition()
                            .getMethods()
                            .forEach(method -> methods.add(method.name()));
                    if (methods.isEmpty()) {
                        methods.add("GET");
                    }
                    for (String pattern : patterns(info)) {
                        if (pattern.startsWith(API_V1)) {
                            methods.forEach(method ->
                                    endpoints.add(new Endpoint(method, pattern, handler)));
                        }
                    }
                });
        endpoints.forEach(endpoint -> assertThat(endpoint.path()).doesNotContain("{"));
        return endpoints;
    }

    private static Set<String> patterns(RequestMappingInfo info) {
        return info.getPathPatternsCondition().getPatternValues();
    }

    private static String errorCode(MvcResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
        } catch (Exception ex) {
            return "<no code in body>";
        }
    }

    /** Expected status for the controlled fixture, not a complete public endpoint contract. */
    private record AnonymousExpectation(String reason, int status) {}

    /** One mapped HTTP method and path, retaining the handler that the probe must select. */
    private record Endpoint(String method, String pattern, HandlerMethod handler) {
        String key() {
            return method + " " + pattern;
        }

        String path() {
            return PATH_VARIABLE
                    .matcher(pattern.replace("{ref}", SAMPLE_INVITE_REF))
                    .replaceAll(SAMPLE_ID);
        }

        boolean handles(MvcResult result) {
            return result.getHandler() instanceof HandlerMethod actual
                    && handler.getBeanType().equals(actual.getBeanType())
                    && handler.getMethod().equals(actual.getMethod());
        }

        MockHttpServletRequestBuilder request() {
            // JSON body for every method also satisfies consumes=JSON (the Stripe webhook); the
            // signature header is only read by that one handler and is ignored elsewhere.
            return MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .header("Stripe-Signature", "t=1,v1=anonymous-probe");
        }
    }

    /** Registered only by the shadow-handler regression, never by component scanning. */
    static class AnonymousVariant {
        @ResponseBody
        public Result<Void> read() {
            return Result.ok();
        }
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
