package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.catalog.service.TeamPriceAppService;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.util.UuidV7;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/** Real Flyway, HTTP, authorization, application transactions and PostgreSQL; no business mocks. */
@SpringBootTest(
        properties = {
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            "spring.flyway.locations=classpath:db/migration"
        })
@AutoConfigureMockMvc
@Testcontainers
@Import(TeamPriceControllerTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TeamPriceFlowIT {
    @Container
    @SuppressWarnings("resource") // Testcontainers owns the isolated database lifecycle
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add(
                "spring.datasource.url",
                () -> {
                    String url = POSTGRES.getJdbcUrl();
                    return url + (url.contains("?") ? "&" : "?") + "currentSchema=domain";
                });
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private TeamPriceAppService prices;
    private UUID product;
    private UUID buyer;
    private UUID admin;
    private String buyerToken;
    private String adminToken;
    private Instant validFrom;
    private Instant validUntil;

    @BeforeEach
    void seedOnlyIsolatedContainer() {
        product = UuidV7.next();
        buyer = UuidV7.next();
        admin = UuidV7.next();
        validFrom = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS);
        validUntil = validFrom.plusSeconds(3600);
        jdbc.update(
                """
                INSERT INTO sys_user (id, register_source, status, nickname)
                VALUES (?, 'google', 'active', 'Test buyer'), (?, 'google', 'active', 'Test admin')
                """,
                buyer,
                admin);
        jdbc.update(
                """
                INSERT INTO rel_user_role (user_id, role_id)
                SELECT ?, id FROM sys_role WHERE role_code = 'admin' AND deleted = false
                """,
                admin);
        jdbc.update(
                """
                INSERT INTO biz_product
                    (id, product_no, title, original_price, currency, stock, status, pricing_mode)
                VALUES (?, ?, 'Quotation integration fixture', 129.00, 'sgd', 10, 'on_sale', 'dual')
                """,
                product,
                "IT-" + product);
        buyerToken = StpUtil.getStpLogic().createLoginSession(buyer.toString());
        adminToken = StpUtil.getStpLogic().createLoginSession(admin.toString());
    }

    @AfterEach
    void clearTestSessions() {
        if (buyerToken != null) {
            StpUtil.logoutByTokenValue(buyerToken);
        }
        if (adminToken != null) {
            StpUtil.logoutByTokenValue(adminToken);
        }
    }

    @Test
    void firstTeamQuoteSurvivesLaterPriceChangeWithoutStockOrPaymentMutation() throws Exception {
        configure("89.00", 0);
        String response =
                mvc.perform(
                                post("/api/v1/team-price-quotes")
                                        .header("blocbuy-token", buyerToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of("productId", product))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.amount").value("89.00"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID quoteId = UUID.fromString(json.readTree(response).path("data").path("id").asText());
        configure("79.00", 1);
        mvc.perform(get("/api/v1/team-price-quotes/" + quoteId).header("blocbuy-token", buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value("89.00"))
                .andExpect(jsonPath("$.data.policyVersion").value(1));
        mvc.perform(get("/api/v1/team-price-quotes/" + quoteId).header("blocbuy-token", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_404_002"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT stock FROM biz_product WHERE id = ?",
                                Integer.class,
                                product))
                .isEqualTo(10);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_group_deal WHERE product_id = ?",
                                Long.class,
                                product))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_payment WHERE user_id = ?",
                                Long.class,
                                buyer))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT policy_version FROM biz_team_price_quote WHERE id = ?",
                                Integer.class,
                                quoteId))
                .isEqualTo(1);
    }

    @Test
    void concurrentConfigurationThroughSpringProxyCommitsExactlyOneRevision() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> configureConcurrently("69.00", ready, go));
            var second = executor.submit(() -> configureConcurrently("79.00", ready, go));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                go.countDown();
            }
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("committed", "CATALOG_412_001");
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_team_price_policy WHERE product_id = ?",
                                Long.class,
                                product))
                .isEqualTo(1);
        assertThat(prices.issueQuote(product, buyer).policyVersion()).isEqualTo(1);
    }

    @Test
    void invalidPolicyAndDisabledBuyerLeaveNoOrphanRows() throws Exception {
        mvc.perform(
                        put(policyPath())
                                .header("blocbuy-token", adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(policyBody("129.00", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_400_001"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_team_price_policy WHERE product_id = ?",
                                Long.class,
                                product))
                .isZero();
        configure("89.00", 0);
        jdbc.update(
                "UPDATE sys_user SET status = 'disabled', update_time = clock_timestamp() WHERE id = ?",
                buyer);
        mvc.perform(
                        post("/api/v1/team-price-quotes")
                                .header("blocbuy-token", buyerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("productId", product))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_403_001"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_team_price_quote WHERE product_id = ?",
                                Long.class,
                                product))
                .isZero();
    }

    private String configureConcurrently(String amount, CountDownLatch ready, CountDownLatch go)
            throws InterruptedException {
        ready.countDown();
        if (!go.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("test start barrier timed out");
        }
        try {
            prices.configure(
                    product,
                    admin,
                    new TeamPriceAppService.ConfigurePolicy(
                            new BigDecimal(amount), "sgd", validFrom, validUntil, 0));
            return "committed";
        } catch (BizException ex) {
            return ex.errorCode().code();
        }
    }

    private void configure(String amount, int version) throws Exception {
        mvc.perform(
                        put(policyPath())
                                .header("blocbuy-token", adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(policyBody(amount, version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(version + 1));
    }

    private String policyPath() {
        return "/api/v1/products/" + product + "/team-price-policy";
    }

    private String policyBody(String amount, int version) throws Exception {
        return json.writeValueAsString(
                Map.of(
                        "amount",
                        amount,
                        "currency",
                        "sgd",
                        "validFrom",
                        validFrom,
                        "validUntil",
                        validUntil,
                        "expectedVersion",
                        version));
    }
}
