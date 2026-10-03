package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.gateway.RateLimitGateway;
import com.blocbuy.common.gateway.RateLimitGateway.RateLimit;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.ShareInvite;
import com.blocbuy.groupbuy.repository.GroupDealRepository;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import com.blocbuy.groupbuy.repository.ShareInviteRepository;
import com.blocbuy.payment.gateway.PaymentIntentPort;
import com.blocbuy.payment.service.StripeCheckoutService;
import com.blocbuy.payment.service.StripeFeatureSwitch;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** HTTP/auth/application/Redis chain; business ports stop before durable purchase/provider work. */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
@AutoConfigureMockMvc
@Testcontainers
@Import(TeamPriceControllerTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WriteRateLimitIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.10").withExposedPorts(6379);

    @DynamicPropertySource
    static void stores(DynamicPropertyRegistry properties) {
        properties.add(
                "spring.datasource.url",
                () -> {
                    String url = POSTGRES.getJdbcUrl();
                    return url + (url.contains("?") ? "&" : "?") + "currentSchema=domain";
                });
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RateLimitGateway limits;
    @Autowired private MeterRegistry meters;
    @MockitoBean private PaymentIntentPort intents;
    @MockitoBean private StripeCheckoutService checkout;
    @MockitoBean private StripeFeatureSwitch feature;
    @MockitoBean private GroupDealRepository deals;
    @MockitoBean private PaidGroupDealRepository participation;
    @MockitoBean private ShareInviteRepository invites;

    private UUID user;
    private UUID deal;
    private String token;

    enum Action {
        JOIN("join-intent"),
        PAYMENT("payment-initiation"),
        INVITE("invite-create");

        private final String scope;

        Action(String scope) {
            this.scope = scope;
        }
    }

    @BeforeEach
    void fixtures() {
        redis.execute(
                (RedisCallback<Void>)
                        connection -> {
                            connection.serverCommands().flushDb();
                            return null;
                        });
        user = UuidV7.next();
        deal = UuidV7.next();
        jdbc.update(
                "INSERT INTO sys_user (id, register_source, status) VALUES (?, 'google', 'active')",
                user);
        token = StpUtil.getStpLogic().createLoginSession(user.toString());
        when(intents.createIntent(any(), any(), any(), any(), any())).thenReturn(UuidV7.next());
        when(checkout.prepare(any(), any(), anyString()))
                .thenReturn(
                        new StripeCheckoutService.CheckoutResult(UuidV7.next(), null, "CREATED"));
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        GroupDeal open =
                GroupDeal.reconstitute(
                        deal,
                        "LIMIT-1",
                        UuidV7.next(),
                        user,
                        new BigDecimal("10.00"),
                        "sgd",
                        3,
                        1,
                        DealStatus.OPEN,
                        now.minusSeconds(60),
                        now.plusSeconds(3600),
                        null);
        when(deals.findById(any())).thenReturn(Optional.of(open));
        when(participation.hasActiveMember(any(), any())).thenReturn(true);
        when(participation.currentTime()).thenReturn(now);
        when(invites.findByDealAndSharer(any(), any()))
                .thenAnswer(
                        call ->
                                Optional.of(
                                        new ShareInvite(
                                                UuidV7.next(),
                                                "AAAAAAAAAAAAAAAAAAAAAA",
                                                call.getArgument(0),
                                                call.getArgument(1))));
    }

    @AfterEach
    void logout() {
        StpUtil.logoutByTokenValue(token);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void overLimitRequestsUseUnified429(Action action) throws Exception {
        RateLimit limit = new RateLimit(action.scope, 60, Duration.ofMinutes(1));
        String subject = action == Action.JOIN ? user + ":" + deal : user.toString();
        for (int i = 0; i < 60; i++) {
            assertThat(limits.tryAcquire(limit, subject)).isTrue();
        }
        mvc.perform(request(action, deal))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMON_429_001"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(intents, checkout, deals, participation, invites);
        var keys = redis.keys("blocbuy:local:common:rate-limit:*" + action.scope + ":*");
        assertThat(keys).hasSize(1);
        String key = keys.iterator().next();
        assertThat(key).doesNotContain(user.toString()).doesNotContain(deal.toString());
        assertThat(redis.getExpire(key)).isPositive().isLessThanOrEqualTo(60);
        // JOIN isolates each deal; the two user-wide limits deliberately do not.
        mvc.perform(request(action, UuidV7.next()))
                .andExpect(action == Action.JOIN ? status().isOk() : status().isTooManyRequests());
        for (Action other : Action.values()) {
            if (other != action) {
                mvc.perform(request(other, deal)).andExpect(status().isOk());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void unreachableRedisAllowsWritesAndRecordsDegradation(Action action) throws Exception {
        RedisConnectionFactory original = redis.getConnectionFactory();
        LettuceConnectionFactory unavailable =
                new LettuceConnectionFactory(
                        new RedisStandaloneConfiguration("127.0.0.1", 1),
                        LettuceClientConfiguration.builder()
                                .commandTimeout(Duration.ofMillis(200))
                                .shutdownTimeout(Duration.ZERO)
                                .build());
        unavailable.afterPropertiesSet();
        unavailable.start();
        double before =
                meters.counter("blocbuy.rate_limit.fail_open", "scope", action.scope).count();
        try {
            redis.setConnectionFactory(unavailable);
            var response =
                    mvc.perform(request(action, deal))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.code").value("COMMON_200_000"));
            switch (action) {
                case JOIN -> {
                    response.andExpect(jsonPath("$.data.intentId").isString());
                    verify(intents).createIntent(user, "JOIN", deal, null, null);
                }
                case PAYMENT -> {
                    response.andExpect(jsonPath("$.data.paymentId").isString());
                    verify(checkout).prepare(user, deal, "limit-test");
                }
                case INVITE -> {
                    response.andExpect(jsonPath("$.data.ref").value("AAAAAAAAAAAAAAAAAAAAAA"));
                    verify(invites).findByDealAndSharer(deal, user);
                }
            }
            assertThat(
                            meters.counter("blocbuy.rate_limit.fail_open", "scope", action.scope)
                                    .count())
                    .isEqualTo(before + 1);
        } finally {
            redis.setConnectionFactory(original);
            unavailable.destroy();
        }
    }

    private MockHttpServletRequestBuilder request(Action action, UUID businessId) {
        return switch (action) {
            case JOIN ->
                    post("/api/v1/payments/intents")
                            .header("blocbuy-token", token)
                            .header("X-BlocBuy-Request", "checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    "{\"operation\":\"JOIN\",\"businessId\":\""
                                            + businessId
                                            + "\"}");
            case PAYMENT ->
                    post("/api/v1/payments/checkout-sessions")
                            .header("blocbuy-token", token)
                            .header("X-BlocBuy-Request", "checkout")
                            .header("Idempotency-Key", "limit-test")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"intentId\":\"" + businessId + "\"}");
            case INVITE ->
                    post("/api/v1/group-deals/" + businessId + "/invites")
                            .header("blocbuy-token", token)
                            .header("X-BlocBuy-Request", "invite");
        };
    }
}
