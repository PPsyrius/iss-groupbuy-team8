package com.blocbuy.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import com.blocbuy.catalog.service.TeamPriceAppService;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.repository.RoleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Real HTTP binding, Sa-Token sessions/interceptor/RBAC adapter, and global errors. Only business
 * orchestration and the RBAC repository are mocked; the test-only session store never connects to a
 * developer's Redis. Database invariants have separate PostgreSQL integration coverage.
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
@Import(TeamPriceControllerTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TeamPriceControllerTest {
    private static final UUID PRODUCT = UUID.fromString("01990000-0000-7000-8000-000000000001");
    private static final UUID BUYER = UUID.fromString("01990000-0000-7000-8000-000000000002");
    private static final UUID ADMIN = UUID.fromString("01990000-0000-7000-8000-000000000003");
    private static final UUID OTHER_BUYER = UUID.fromString("01990000-0000-7000-8000-000000000004");
    private static final UUID QUOTE = UUID.fromString("01990000-0000-7000-8000-000000000005");
    private static final Instant START = Instant.parse("2026-09-09T00:00:00Z");
    private static final Instant END = START.plusSeconds(3600);
    private static final String POLICY_PATH = "/api/v1/products/" + PRODUCT + "/team-price-policy";
    private static final String QUOTE_PATH = "/api/v1/team-price-quotes";
    private static final String TOKEN_HEADER = "blocbuy-token";
    private static final String VALID_POLICY =
            """
            {"amount":"89.00","currency":"sgd","validFrom":"2026-09-09T00:00:00Z",
             "validUntil":"2026-09-09T01:00:00Z","expectedVersion":0}
            """;

    @Autowired private MockMvc mvc;
    @MockitoBean private TeamPriceAppService prices;
    @MockitoBean private RoleRepository roles;
    private final List<String> sessions = new ArrayList<>();

    @BeforeEach
    void setUpPermissions() {
        when(roles.findPermissionCodesByUserId(any())).thenReturn(List.of());
        when(roles.findPermissionCodesByUserId(ADMIN)).thenReturn(List.of("catalog:product:write"));
    }

    @AfterEach
    void deleteOnlyTestSessions() {
        sessions.forEach(StpUtil::logoutByTokenValue);
    }

    @ParameterizedTest
    @MethodSource("protectedRequests")
    void anonymousRequestsCannotReachQuotationOrManagement(MockHttpServletRequestBuilder request)
            throws Exception {
        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401_001"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(prices);
    }

    static Stream<MockHttpServletRequestBuilder> protectedRequests() {
        return Stream.of(
                get(POLICY_PATH),
                put(POLICY_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_POLICY),
                post(QUOTE_PATH).contentType(MediaType.APPLICATION_JSON).content(productBody()),
                get(QUOTE_PATH + "/" + QUOTE));
    }

    @Test
    void ordinaryBuyerCannotReadOrChangePolicyEvenOnAnonymousProductPath() throws Exception {
        String token = login(BUYER);
        mvc.perform(get(POLICY_PATH).header(TOKEN_HEADER, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_403_001"));
        mvc.perform(
                        put(POLICY_PATH)
                                .header(TOKEN_HEADER, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_POLICY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_403_001"));
        verifyNoInteractions(prices);
    }

    @Test
    void adminCanConfigureExactDecimalPolicyAndInspectVersion() throws Exception {
        TeamPriceAppService.ConfigurePolicy command =
                new TeamPriceAppService.ConfigurePolicy(
                        new BigDecimal("89.00"), "sgd", START, END, 0);
        when(prices.configure(PRODUCT, ADMIN, command)).thenReturn(policy());
        when(prices.getPolicy(PRODUCT, ADMIN)).thenReturn(policy());
        String token = login(ADMIN);
        mvc.perform(
                        put(POLICY_PATH)
                                .header(TOKEN_HEADER, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_POLICY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.amount").value("89.00"))
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(get(POLICY_PATH).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value("89.00"));
        verify(prices).configure(PRODUCT, ADMIN, command);
        verify(prices).getPolicy(PRODUCT, ADMIN);
    }

    @ParameterizedTest
    @MethodSource("invalidPolicies")
    void invalidPolicyBodyIsRejectedBeforeBusinessCode(String body) throws Exception {
        mvc.perform(
                        put(POLICY_PATH)
                                .header(TOKEN_HEADER, login(ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(prices);
    }

    static Stream<String> invalidPolicies() {
        return Stream.of(
                VALID_POLICY.replace("89.00", "89.001"),
                VALID_POLICY.replace("89.00", "1e2"),
                VALID_POLICY.replace("89.00", "10000000000000000.00"),
                VALID_POLICY.replace("\"sgd\"", "\"usd\""),
                VALID_POLICY.replace(",\"expectedVersion\":0", ""),
                VALID_POLICY.replace("\"expectedVersion\":0", "\"expectedVersion\":-1"),
                VALID_POLICY.replace("\"2026-09-09T01:00:00Z\"", "null"));
    }

    @Test
    void policyAmountMustBeJsonStringNotFloatingPointNumber() throws Exception {
        mvc.perform(
                        put(POLICY_PATH)
                                .header(TOKEN_HEADER, login(ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_POLICY.replace("\"89.00\"", "89.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
        verifyNoInteractions(prices);
    }

    @Test
    void quoteIssuanceUsesSessionOwnerAndReturnsDecimalString() throws Exception {
        when(prices.issueQuote(PRODUCT, BUYER)).thenReturn(quote());
        mvc.perform(
                        post(QUOTE_PATH)
                                .header(TOKEN_HEADER, login(BUYER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(productBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.id").value(QUOTE.toString()))
                .andExpect(jsonPath("$.data.amount").value("89.00"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-09-09T00:15:00Z"))
                .andExpect(jsonPath("$.data.ownerId").doesNotExist());
        verify(prices).issueQuote(PRODUCT, BUYER);
    }

    @ParameterizedTest
    @MethodSource("tamperedQuoteBodies")
    void clientCannotSupplyQuoteOwnerOrAmount(String body) throws Exception {
        mvc.perform(
                        post(QUOTE_PATH)
                                .header(TOKEN_HEADER, login(BUYER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
        verifyNoInteractions(prices);
    }

    static Stream<String> tamperedQuoteBodies() {
        return Stream.of(
                "{\"productId\":\"" + PRODUCT + "\",\"ownerId\":\"" + OTHER_BUYER + "\"}",
                "{\"productId\":\"" + PRODUCT + "\",\"amount\":\"0.01\"}");
    }

    @ParameterizedTest
    @MethodSource("missingQuoteProductIds")
    void quoteWithoutProductIdIsRejectedBeforeBusinessCode(String body) throws Exception {
        mvc.perform(
                        post(QUOTE_PATH)
                                .header(TOKEN_HEADER, login(BUYER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(prices);
    }

    static Stream<String> missingQuoteProductIds() {
        return Stream.of("{}", "{\"productId\":null}");
    }

    @Test
    void quoteWithMalformedProductIdUsesGlobalBadRequestMapping() throws Exception {
        mvc.perform(
                        post(QUOTE_PATH)
                                .header(TOKEN_HEADER, login(BUYER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
        verifyNoInteractions(prices);
    }

    @Test
    void ownerCanReadHistoricalSnapshotWithoutRepricing() throws Exception {
        when(prices.getQuote(QUOTE, BUYER)).thenReturn(quote());
        mvc.perform(get(QUOTE_PATH + "/" + QUOTE).header(TOKEN_HEADER, login(BUYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value("89.00"))
                .andExpect(jsonPath("$.data.policyVersion").value(1));
        verify(prices).getQuote(QUOTE, BUYER);
    }

    @Test
    void otherOwnerGetsSafeNotFoundEnvelope() throws Exception {
        when(prices.getQuote(QUOTE, OTHER_BUYER))
                .thenThrow(new BizException(CatalogErrorCode.TEAM_PRICE_QUOTE_NOT_FOUND));
        mvc.perform(get(QUOTE_PATH + "/" + QUOTE).header(TOKEN_HEADER, login(OTHER_BUYER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_404_002"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(prices).getQuote(QUOTE, OTHER_BUYER);
    }

    @Test
    void disabledAccountErrorIsNotSuccess() throws Exception {
        when(prices.issueQuote(PRODUCT, BUYER))
                .thenThrow(new BizException(IdentityErrorCode.ACCOUNT_DISABLED));
        mvc.perform(
                        post(QUOTE_PATH)
                                .header(TOKEN_HEADER, login(BUYER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(productBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_403_001"));
    }

    @Test
    void invalidQuoteIdentifierUsesGlobalBadRequestMapping() throws Exception {
        mvc.perform(get(QUOTE_PATH + "/not-a-uuid").header(TOKEN_HEADER, login(BUYER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
        verifyNoInteractions(prices);
    }

    private String login(UUID userId) {
        String token = StpUtil.getStpLogic().createLoginSession(userId.toString());
        sessions.add(token);
        return token;
    }

    private static String productBody() {
        return "{\"productId\":\"" + PRODUCT + "\"}";
    }

    private static TeamPricePolicy policy() {
        return new TeamPricePolicy(PRODUCT, new BigDecimal("89.00"), "sgd", START, END, 1);
    }

    private static TeamPriceQuote quote() {
        return new TeamPriceQuote(
                QUOTE,
                PRODUCT,
                BUYER,
                1,
                new BigDecimal("89.00"),
                "sgd",
                START,
                START.plusSeconds(900));
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
