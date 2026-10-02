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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
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
@Import({TeamPriceControllerTest.SessionStore.class, SqlStatementCapture.Configuration.class})
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
    @Autowired private SqlStatementCapture sql;
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

    /** A stale HTTP write reports the policy revision, rather than the product revision. */
    @Test
    void staleConfigurationReturnsStoredPolicyVersionWithoutAnotherRevision() throws Exception {
        configure("89.00", 0);
        configure("79.00", 1);
        Integer storedVersion =
                jdbc.queryForObject(
                        "SELECT max(version) FROM biz_team_price_policy WHERE product_id = ?",
                        Integer.class,
                        product);
        mvc.perform(
                        put(policyPath())
                                .header("blocbuy-token", adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(policyBody("69.00", 1)))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("CATALOG_412_001"))
                .andExpect(jsonPath("$.errors.currentVersion").value(storedVersion));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_team_price_policy WHERE product_id = ?",
                                Long.class,
                                product))
                .isEqualTo(2);
        assertThat(prices.getPolicy(product, admin).version()).isEqualTo(storedVersion);
    }

    /** Proves two Spring-proxied concurrent configures commit once; loser sees CATALOG_412_001. */
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
                .andExpect(jsonPath("$.code").value("CATALOG_400_001"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(
                        jsonPath("$.errors[0].message")
                                .value("Must be strictly below the solo price"));
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

    @ParameterizedTest
    @ValueSource(strings = {"129.00", "130.00", "0.00"})
    void invalidAmountNamesTheRequestFieldWithoutWritingPolicy(String amount) throws Exception {
        mvc.perform(
                        put(policyPath())
                                .header("blocbuy-token", adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(policyBody(amount, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_400_001"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_team_price_policy WHERE product_id = ?",
                                Long.class,
                                product))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void invalidWindowNamesEndTimeWithoutWritingPolicy(int secondsAfterEnd) throws Exception {
        validFrom = validUntil.plusSeconds(secondsAfterEnd);
        mvc.perform(
                        put(policyPath())
                                .header("blocbuy-token", adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(policyBody("89.00", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_400_001"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("validUntil"))
                .andExpect(jsonPath("$.errors[0].message").value("Must be after the start"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_team_price_policy WHERE product_id = ?",
                                Long.class,
                                product))
                .isZero();
    }

    /**
     * #254: append through the real PUT three times and return the exact stored protocol values.
     */
    @Test
    void policyHistoryReturnsThreeConfiguredRevisionsNewestFirst() throws Exception {
        final JsonNode first = configure("89.00", 0);
        validFrom = validFrom.plusSeconds(1);
        validUntil = validUntil.plusSeconds(1);
        final JsonNode second = configure("79.00", 1);
        validFrom = validFrom.plusSeconds(1);
        validUntil = validUntil.plusSeconds(1);
        JsonNode third = configure("69.00", 2);
        var result =
                json.readTree(
                                mvc.perform(get(historyPath()).header("blocbuy-token", adminToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.total").value(3))
                                        .andExpect(jsonPath("$.data.page").value(1))
                                        .andExpect(jsonPath("$.data.size").value(20))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data")
                        .path("items");
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get(0)).isEqualTo(third);
        assertThat(result.get(1)).isEqualTo(second);
        assertThat(result.get(2)).isEqualTo(first);
        mvc.perform(
                        get(historyPath())
                                .param("size", "2")
                                .param("page", "2")
                                .header("blocbuy-token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].version").value(1));
        mvc.perform(
                        get(historyPath())
                                .param("size", "2")
                                .param("page", "3")
                                .header("blocbuy-token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items").isEmpty());
        assertThat(prices.getPolicy(product, admin).version()).isEqualTo(3);
    }

    /** Every listing lifecycle is readable without inventing a policy or returning unavailable. */
    @ParameterizedTest
    @ValueSource(strings = {"draft", "on_sale", "off_sale"})
    void policyHistoryWithoutPolicyIsEmptyForEveryListingState(String state) throws Exception {
        jdbc.update("UPDATE biz_product SET status=? WHERE id=?", state, product);
        mvc.perform(get(historyPath()).header("blocbuy-token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void policyHistoryRequiresLoginAdminRoleAndActiveAccount() throws Exception {
        mvc.perform(get(historyPath())).andExpect(status().isUnauthorized());
        mvc.perform(get(historyPath()).header("blocbuy-token", buyerToken))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE sys_user SET status='disabled' WHERE id=?", admin);
        mvc.perform(get(historyPath()).header("blocbuy-token", adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_403_001"));
    }

    /**
     * Capability alone is not admin access; admin role alone cannot bypass a revoked capability.
     */
    @Test
    @Transactional
    void policyHistoryChecksRoleAndCatalogCapabilityIndependently() throws Exception {
        jdbc.update(
                "INSERT INTO rel_user_role(user_id,role_id) "
                        + "SELECT ?,id FROM sys_role WHERE role_code='buyer' AND NOT deleted",
                buyer);
        jdbc.update(
                "INSERT INTO rel_role_permission(role_id,permission_id) "
                        + "SELECT role.id,permission.id FROM sys_role role CROSS JOIN sys_permission permission "
                        + "WHERE role.role_code='buyer' AND NOT role.deleted "
                        + "AND permission.perm_code='catalog:product:write' AND NOT permission.deleted");
        mvc.perform(get(historyPath()).header("blocbuy-token", buyerToken))
                .andExpect(status().isForbidden());
        jdbc.update(
                "UPDATE rel_role_permission SET deleted=true WHERE role_id IN "
                        + "(SELECT id FROM sys_role WHERE role_code='admin') AND permission_id IN "
                        + "(SELECT id FROM sys_permission WHERE perm_code='catalog:product:write')");
        mvc.perform(get(historyPath()).header("blocbuy-token", adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void policyHistoryRejectsMissingAndDeletedListings() throws Exception {
        mvc.perform(
                        get("/api/v1/admin/products/"
                                        + UuidV7.next()
                                        + "/team-price-policy/revisions")
                                .header("blocbuy-token", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_404_001"));
        configure("89.00", 0);
        jdbc.update("UPDATE biz_product SET deleted=true WHERE id=?", product);
        mvc.perform(get(historyPath()).header("blocbuy-token", adminToken))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"page,0", "page,-1", "size,0", "size,101", "page,wrong", "size,wrong"})
    void policyHistoryValidatesPagination(String parameter, String value) throws Exception {
        mvc.perform(get(historyPath()).param(parameter, value).header("blocbuy-token", adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void policyHistoryRetainsExpiredFutureRevisionsAndExcludesDeletedAndOtherProducts()
            throws Exception {
        configure("89.00", 0);
        configure("79.00", 1);
        configure("69.00", 2);
        jdbc.update(
                "UPDATE biz_team_price_policy SET valid_from=now()-interval '2 hours', "
                        + "valid_until=now()-interval '1 hour' WHERE product_id=? AND version=1",
                product);
        jdbc.update(
                "UPDATE biz_team_price_policy SET valid_from=now()+interval '1 hour', "
                        + "valid_until=now()+interval '2 hours' WHERE product_id=? AND version=2",
                product);
        jdbc.update(
                "UPDATE biz_team_price_policy SET deleted=true WHERE product_id=? AND version=3",
                product);
        jdbc.update(
                "INSERT INTO biz_team_price_policy(product_id,amount,currency,valid_from,valid_until,"
                        + "version,create_by,update_by) VALUES (?,59,'sgd',now(),now()+interval '1 hour',99,?,?)",
                UuidV7.next(),
                admin,
                admin);
        mvc.perform(get(historyPath()).header("blocbuy-token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].version").value(2))
                .andExpect(jsonPath("$.data.items[0].amount").value("79.00"))
                .andExpect(jsonPath("$.data.items[1].version").value(1));
        assertThat(prices.getPolicy(product, admin).version()).isEqualTo(2);
        mvc.perform(
                        get(historyPath())
                                .param("page", "2147483647")
                                .param("size", "100")
                                .header("blocbuy-token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    /** A history read uses two policy statements regardless of page size and makes no writes. */
    @Test
    void policyHistoryHasFixedQueryCountWithoutWrites() throws Exception {
        configure("89.00", 0);
        configure("79.00", 1);
        configure("69.00", 2);
        var small = captureHistory(1);
        var large = captureHistory(3);
        assertThat(large).hasSameSizeAs(small);
        assertThat(large.stream().filter(statement -> statement.contains("biz_team_price_policy")))
                .hasSize(2);
        assertThat(large)
                .noneMatch(statement -> statement.matches("(?is).*\\b(insert|update|delete)\\b.*"));
    }

    private List<String> captureHistory(int size) throws Exception {
        sql.start();
        List<String> captured;
        try {
            mvc.perform(
                            get(historyPath())
                                    .param("size", Integer.toString(size))
                                    .header("blocbuy-token", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(size));
        } finally {
            captured = sql.finish();
        }
        return captured;
    }

    private String historyPath() {
        return "/api/v1/admin/products/" + product + "/team-price-policy/revisions";
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
            assertThat(ex.details()).containsEntry("currentVersion", 1);
            return ex.errorCode().code();
        }
    }

    private JsonNode configure(String amount, int version) throws Exception {
        String response =
                mvc.perform(
                                put(policyPath())
                                        .header("blocbuy-token", adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(policyBody(amount, version)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.version").value(version + 1))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(response).path("data");
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
