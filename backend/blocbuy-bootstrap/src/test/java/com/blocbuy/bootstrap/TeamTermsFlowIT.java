package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.catalog.service.TeamTermsAppService;
import com.blocbuy.catalog.service.TeamTermsAppService.ConfigureTerms;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.util.UuidV7;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * US25 acceptance through MockMvc, real Sa-Token RBAC, Flyway, the transactional application
 * services and an isolated PostgreSQL 18 container. Sessions use the test-only memory store.
 */
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
class TeamTermsFlowIT {
    private static final String BASE = "/api/v1/admin/products";
    private static final String HEADER = "blocbuy-token";
    private static final String DRAFT = "draft";
    private static final String ON_SALE = "on_sale";
    private static final String ORIGINAL_PRICE = "100.00";
    private static final String TEAM_PRICE = "89.00";
    private static final String TARGET_SIZE = "targetSize";
    private static final String LIFETIME_SECONDS = "lifetimeSeconds";
    private static final String EXPECTED_VERSION = "expectedVersion";
    private static final String TEAM_PRICE_KEY = "teamPrice";
    private static final String TEAM_PRICE_AMOUNT = "teamPrice.amount";
    private static final String CODE = "$.code";
    private static final String TEAM_PRICE_INVALID = "CATALOG_400_001";
    private static final String READ_TERMS =
            "SELECT team_target_size, team_lifetime_seconds, version FROM biz_product WHERE id = ?";

    @Container
    @SuppressWarnings("resource") // Testcontainers owns this disposable database.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    /** Redirect all database access to the owned test container, never a developer database. */
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
    @Autowired private TeamTermsAppService terms;
    private UUID admin;
    private UUID buyer;
    private String adminToken;
    private String buyerToken;

    /** Unique fixtures per test with real SQL-backed administrator grants. */
    @BeforeEach
    void seedIdentities() {
        admin = UuidV7.next();
        buyer = UuidV7.next();
        jdbc.update(
                """
                INSERT INTO sys_user (id, register_source, status)
                VALUES (?, 'google', 'active'), (?, 'google', 'active')
                """,
                admin,
                buyer);
        jdbc.update(
                """
                INSERT INTO rel_user_role (user_id, role_id)
                SELECT ?, id FROM sys_role WHERE role_code = 'admin' AND deleted = false
                """,
                admin);
        adminToken = StpUtil.getStpLogic().createLoginSession(admin.toString());
        buyerToken = StpUtil.getStpLogic().createLoginSession(buyer.toString());
    }

    /** Dispose only sessions created by this test. */
    @AfterEach
    void clearSessions() {
        StpUtil.logoutByTokenValue(adminToken);
        StpUtil.logoutByTokenValue(buyerToken);
    }

    /** A draft can be configured; the write is versioned, stamped and audited, and stays hidden. */
    @Test
    void draftTermsWriteIsVersionedStampedAndAudited() throws Exception {
        UUID id = product(DRAFT);

        write(id, adminToken, body(0, 3, 7200, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(id.toString()))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.teamTerms.targetSize").value(3))
                .andExpect(jsonPath("$.data.teamTerms.lifetimeSeconds").value(7200))
                .andExpect(jsonPath("$.data.teamTerms.configured").value(true))
                .andExpect(jsonPath("$.data", not(hasKey(TEAM_PRICE_KEY))));

        assertThat(jdbc.queryForMap(READ_TERMS, id))
                .containsEntry("team_target_size", 3)
                .containsEntry("team_lifetime_seconds", 7200)
                .containsEntry("version", 1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT update_by FROM biz_product WHERE id = ?", UUID.class, id))
                .isEqualTo(admin);
        assertThat(
                        jdbc.queryForList(
                                "SELECT * FROM log_product_team_terms WHERE product_id = ?", id))
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row).containsEntry("product_version", 1);
                            assertThat(row).containsEntry("old_target_size", null);
                            assertThat(row).containsEntry("old_lifetime_seconds", null);
                            assertThat(row).containsEntry("new_target_size", 3);
                            assertThat(row).containsEntry("new_lifetime_seconds", 7200);
                            assertThat(row).containsEntry("team_price_version", null);
                            assertThat(row).containsEntry("create_by", admin);
                        });
        mvc.perform(get(BASE + "/" + id).header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamTerms.targetSize").value(3))
                .andExpect(jsonPath("$.data.teamTerms.configured").value(true));
        mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isNotFound());
    }

    /**
     * A never-configured listing reads as not configured, and the page carries terms without N+1.
     */
    @Test
    void unconfiguredListingReadsAsNotConfiguredAndPagesCarryTerms() throws Exception {
        UUID bare = product(DRAFT);
        UUID configured = product(DRAFT);
        write(configured, adminToken, body(0, 2, 2400, null)).andExpect(status().isOk());

        mvc.perform(get(BASE + "/" + bare).header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamTerms.targetSize").doesNotExist())
                .andExpect(jsonPath("$.data.teamTerms.configured").value(false));
        mvc.perform(get(BASE).header(HEADER, adminToken).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.items[?(@.id=='" + configured + "')].teamTerms.configured")
                                .value(hasItem(true)))
                .andExpect(
                        jsonPath("$.data.items[?(@.id=='" + bare + "')].teamTerms.configured")
                                .value(hasItem(false)));
    }

    /** Terms are editable in every lifecycle state; every write appends one audit row. */
    @ParameterizedTest
    @ValueSource(strings = {"draft", "on_sale", "off_sale"})
    void termsAreEditableInEveryLifecycleState(String state) throws Exception {
        UUID id = product(state);

        write(id, adminToken, body(0, 3, 7200, null)).andExpect(status().isOk());
        write(id, adminToken, body(1, 5, 3600, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        assertThat(jdbc.queryForMap(READ_TERMS, id))
                .containsEntry("team_target_size", 5)
                .containsEntry("team_lifetime_seconds", 3600)
                .containsEntry("version", 2);
        assertThat(
                        jdbc.queryForList(
                                "SELECT old_target_size, new_target_size FROM log_product_team_terms"
                                        + " WHERE product_id = ? ORDER BY product_version",
                                id))
                .extracting(row -> row.get("old_target_size"), row -> row.get("new_target_size"))
                .containsExactly(tuple(null, 3), tuple(3, 5));
    }

    /** A stale version is refused with 412: nothing is written and nothing is audited. */
    @Test
    void staleVersionIsRefusedWithoutMutationOrAudit() throws Exception {
        UUID id = product(DRAFT);
        write(id, adminToken, body(0, 3, 7200, null)).andExpect(status().isOk());

        write(id, adminToken, body(0, 9, 9000, null))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath(CODE).value("CATALOG_412_002"));

        assertThat(jdbc.queryForMap(READ_TERMS, id))
                .containsEntry("team_target_size", 3)
                .containsEntry("version", 1);
        assertThat(logRows(id)).isEqualTo(1);
    }

    /** A listing edit made in between also invalidates the terms write: one shared token. */
    @Test
    void listingEditInvalidatesTheTermsToken() throws Exception {
        UUID id = product(DRAFT);
        jdbc.update("UPDATE biz_product SET version = version + 1 WHERE id = ?", id);

        write(id, adminToken, body(0, 3, 7200, null))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath(CODE).value("CATALOG_412_002"));
        assertThat(logRows(id)).isZero();
    }

    /** Two admins with the same version: exactly one write wins, one is told to re-read. */
    @Test
    void concurrentWritesCommitOneRevision() throws Exception {
        UUID id = product(DRAFT);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> writeAfter(start, id, 3));
            var second = pool.submit(() -> writeAfter(start, id, 4));
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("ok", "CATALOG_412_002");
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM biz_product WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(logRows(id)).isEqualTo(1);
    }

    /** Bean Validation failures are 400 with per-field errors and change nothing. */
    @Test
    void malformedInputIsRejectedWithFieldErrors() throws Exception {
        UUID id = product(DRAFT);
        Map<String, Object> badPrice = price("abc", 0);

        write(id, adminToken, body(0, 1, 2399, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODE).value("COMMON_400_002"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem(TARGET_SIZE)))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem(LIFETIME_SECONDS)));
        write(id, adminToken, body(0, 3, 86_401, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem(LIFETIME_SECONDS)));
        write(id, adminToken, body(0, 3, 7200, badPrice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem(TEAM_PRICE_AMOUNT)));
        write(id, adminToken, "{\"targetSize\":3,\"lifetimeSeconds\":7200}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem(EXPECTED_VERSION)));
        write(
                        id,
                        adminToken,
                        "{\"expectedVersion\":0,\"targetSize\":3,\"lifetimeSeconds\":7200,\"x\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODE).value("COMMON_400_001"));

        assertThat(jdbc.queryForMap(READ_TERMS, id)).containsEntry("version", 0);
        assertThat(logRows(id)).isZero();
    }

    /**
     * Price rules live only in the team price policy, so a bad price gets the standalone endpoint's
     * CATALOG_400_001 with the nested request field, and the terms roll back with it.
     */
    @Test
    void teamPriceNotBelowTheOriginalPriceIsRejectedByThePolicy() throws Exception {
        UUID id = product(ON_SALE);

        write(id, adminToken, body(0, 3, 7200, price(ORIGINAL_PRICE, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODE).value(TEAM_PRICE_INVALID))
                .andExpect(jsonPath("$.errors[0].field").value("teamPrice.amount"))
                .andExpect(
                        jsonPath("$.errors[0].message")
                                .value("Must be strictly below the solo price"));
        assertNothingWritten(id);
    }

    /** A price window the policy cannot hold is CATALOG_400_001 and writes nothing. */
    @Test
    void badTeamPriceWindowIsRejectedWithNestedFieldErrors() throws Exception {
        UUID id = product(ON_SALE);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        write(
                        id,
                        adminToken,
                        body(
                                0,
                                3,
                                7200,
                                price(
                                        TEAM_PRICE,
                                        now.minus(2, ChronoUnit.DAYS),
                                        now.minus(1, ChronoUnit.DAYS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODE).value(TEAM_PRICE_INVALID))
                .andExpect(jsonPath("$.errors[0].field").value("teamPrice.validUntil"))
                .andExpect(jsonPath("$.errors[0].message").value("Must be in the future"));
        write(
                        id,
                        adminToken,
                        body(
                                0,
                                3,
                                7200,
                                price(TEAM_PRICE, now.plusSeconds(3600), now.plusSeconds(3600))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODE).value(TEAM_PRICE_INVALID))
                .andExpect(jsonPath("$.errors[0].field").value("teamPrice.validUntil"))
                .andExpect(jsonPath("$.errors[0].message").value("Must be after the start"));
        assertNothingWritten(id);
    }

    /** #264: a teamPrice no START could use rolls the terms back with it. */
    @Test
    void teamPriceWindowStartCannotUseIsRejectedAndRollsBackTerms() throws Exception {
        UUID id = product(ON_SALE);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        write(
                        id,
                        adminToken,
                        body(
                                0,
                                3,
                                7200,
                                price(TEAM_PRICE, now.minusSeconds(60), now.plusSeconds(1800))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODE).value(TEAM_PRICE_INVALID))
                .andExpect(jsonPath("$.errors[0].field").value("teamPrice.validUntil"))
                .andExpect(
                        jsonPath("$.errors[0].message")
                                .value(
                                        "Must end more than 40 minutes after the rule takes effect"));
        assertNothingWritten(id);
    }

    private void assertNothingWritten(UUID id) {
        assertThat(jdbc.queryForMap(READ_TERMS, id))
                .containsEntry("team_target_size", null)
                .containsEntry("version", 0);
        assertThat(logRows(id)).isZero();
        assertThat(policyCount(id)).isZero();
    }

    /** Terms and price are one transaction: both land, and both are audited together. */
    @Test
    void termsAndTeamPriceCommitTogetherOnAnOnSaleListing() throws Exception {
        UUID id = product(ON_SALE);

        write(id, adminToken, body(0, 3, 7200, price(TEAM_PRICE, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.teamPrice.amount").value(TEAM_PRICE))
                .andExpect(jsonPath("$.data.teamPrice.currency").value("sgd"))
                .andExpect(jsonPath("$.data.teamPrice.version").value(1));

        assertThat(policyCount(id)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT team_price_version FROM log_product_team_terms WHERE product_id = ?",
                                Integer.class,
                                id))
                .isEqualTo(1);
    }

    /**
     * A price the policy rejects rolls the terms back too: nothing half-applied, nothing audited.
     */
    @Test
    void staleTeamPriceVersionRollsTheTermsBack() throws Exception {
        UUID id = product(ON_SALE);
        write(id, adminToken, body(0, 3, 7200, price(TEAM_PRICE, 0))).andExpect(status().isOk());

        write(id, adminToken, body(1, 5, 3600, price("79.00", 0)))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath(CODE).value("CATALOG_412_001"));

        assertThat(jdbc.queryForMap(READ_TERMS, id))
                .containsEntry("team_target_size", 3)
                .containsEntry("team_lifetime_seconds", 7200)
                .containsEntry("version", 1);
        assertThat(policyCount(id)).isEqualTo(1);
        assertThat(logRows(id)).isEqualTo(1);
    }

    /** The price policy still needs a live listing, so a draft with a price writes nothing. */
    @ParameterizedTest
    @ValueSource(strings = {"draft", "off_sale"})
    void teamPriceNeedsAnOnSaleListing(String state) throws Exception {
        UUID id = product(state);

        write(id, adminToken, body(0, 3, 7200, price(TEAM_PRICE, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODE).value("CATALOG_409_001"));

        assertThat(jdbc.queryForMap(READ_TERMS, id)).containsEntry("version", 0);
        assertThat(logRows(id)).isZero();
        assertThat(policyCount(id)).isZero();
    }

    /** Absent and soft-deleted listings are indistinguishable. */
    @Test
    void absentAndDeletedListingsAreNotFound() throws Exception {
        write(UuidV7.next(), adminToken, body(0, 3, 7200, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(CODE).value("CATALOG_404_001"));
        UUID deleted = product(DRAFT);
        jdbc.update("UPDATE biz_product SET deleted = true WHERE id = ?", deleted);
        write(deleted, adminToken, body(0, 3, 7200, null)).andExpect(status().isNotFound());
    }

    /** The write refuses anonymous and non-admin clients before touching the listing. */
    @Test
    void anonymousAndOrdinaryBuyersAreRefused() throws Exception {
        UUID id = product(DRAFT);
        String path = BASE + "/" + id + "/team-terms";
        String payload = body(0, 3, 7200, null);

        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        write(id, buyerToken, payload).andExpect(status().isForbidden());

        assertThat(jdbc.queryForMap(READ_TERMS, id)).containsEntry("version", 0);
    }

    /** The administrator role alone must not bypass the independently checked capability. */
    @Test
    @Transactional
    void administratorStillNeedsCatalogPermission() throws Exception {
        UUID id = product(DRAFT);
        // The test transaction rolls this grant revocation back; other fixtures remain intact.
        assertThat(
                        jdbc.update(
                                """
                UPDATE rel_role_permission SET deleted = true
                WHERE role_id = (SELECT id FROM sys_role WHERE role_code = 'admin' AND deleted = false)
                  AND permission_id = (SELECT id FROM sys_permission
                      WHERE perm_code = 'catalog:product:write' AND deleted = false)
                  AND deleted = false
                """))
                .isEqualTo(1);

        write(id, adminToken, body(0, 3, 7200, null)).andExpect(status().isForbidden());
    }

    /** Delegating the capability alone must not grant the administrator-only write. */
    @Test
    @Transactional
    void catalogPermissionWithoutAdministratorRoleIsInsufficient() throws Exception {
        UUID id = product(DRAFT);
        UUID editorRole = UuidV7.next();
        jdbc.update(
                "INSERT INTO sys_role (id, role_code, role_name) VALUES (?, ?, 'Catalog editor')",
                editorRole,
                "editor-" + editorRole);
        jdbc.update(
                "INSERT INTO rel_user_role (user_id, role_id) VALUES (?, ?)", buyer, editorRole);
        jdbc.update(
                """
                INSERT INTO rel_role_permission (role_id, permission_id)
                SELECT ?, id FROM sys_permission
                WHERE perm_code = 'catalog:product:write' AND deleted = false
                """,
                editorRole);

        write(id, buyerToken, body(0, 3, 7200, null)).andExpect(status().isForbidden());
    }

    /** A disabled account is refused even with a still-valid session. */
    @Test
    void disabledAdministratorIsRejected() throws Exception {
        UUID id = product(DRAFT);
        jdbc.update("UPDATE sys_user SET status = 'disabled' WHERE id = ?", admin);

        write(id, adminToken, body(0, 3, 7200, null)).andExpect(status().isForbidden());

        assertThat(jdbc.queryForMap(READ_TERMS, id)).containsEntry("version", 0);
    }

    /** The generated contract documents the operation, its token and its constraints. */
    @Test
    void openApiDocumentsTheOperationAndItsConstraints() throws Exception {
        var spec =
                json.readTree(
                        mvc.perform(get("/api/v3/api-docs"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        var operation = spec.path("paths").path(BASE + "/{productId}/team-terms").path("put");
        assertThat(operation.path("security").path(0).path("blocbuyToken").isArray()).isTrue();
        var request = spec.path("components").path("schemas").path("TeamTermsRequest");
        assertThat(request.path("required"))
                .extracting(JsonNode::asText)
                .contains(EXPECTED_VERSION, TARGET_SIZE, LIFETIME_SECONDS)
                .doesNotContain(TEAM_PRICE_KEY);
        assertThat(request.path("properties").path(TARGET_SIZE).path("minimum").asInt())
                .isEqualTo(2);
        assertThat(request.path("properties").path(LIFETIME_SECONDS).path("minimum").asInt())
                .isEqualTo(2400);
        assertThat(request.path("properties").path(LIFETIME_SECONDS).path("maximum").asInt())
                .isEqualTo(86_400);
        assertThat(operation.path("description").asText())
                .contains("COMMON_400_002", TEAM_PRICE_INVALID, "CATALOG_409_001");
        var listing = spec.path("components").path("schemas").path("AdminListingResponse");
        assertThat(listing.path("properties").has("teamTerms")).isTrue();
        var view = spec.path("components").path("schemas").path("TeamTermsView").path("properties");
        assertThat(view.has("configured")).isTrue();
        assertThat(view.has("startable")).isFalse();
        assertThat(view.path("configured").path("description").asText())
                .contains("START can still fail");
        assertThat(view.path(TARGET_SIZE).path("type"))
                .extracting(JsonNode::asText)
                .contains("null");
        assertThat(view.path(LIFETIME_SECONDS).path("type"))
                .extracting(JsonNode::asText)
                .contains("null");
    }

    /** Competing commands use the real transactional proxy; only business conflicts convert. */
    private String writeAfter(CountDownLatch start, UUID id, int size) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("test barrier timed out");
        }
        try {
            terms.configure(id, new ConfigureTerms(0, size, 7200, null), admin);
            return "ok";
        } catch (BizException ex) {
            return ex.errorCode().code();
        }
    }

    private ResultActions write(UUID id, String token, String payload) throws Exception {
        return mvc.perform(
                put(BASE + "/" + id + "/team-terms")
                        .header(HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload));
    }

    private String body(int version, int size, int lifetime, Map<String, Object> teamPrice)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put(EXPECTED_VERSION, version);
        request.put(TARGET_SIZE, size);
        request.put(LIFETIME_SECONDS, lifetime);
        if (teamPrice != null) {
            request.put(TEAM_PRICE_KEY, teamPrice);
        }
        return json.writeValueAsString(request);
    }

    private static Map<String, Object> price(String amount, int policyVersion) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> price =
                price(amount, now.minusSeconds(60), now.plus(30, ChronoUnit.DAYS));
        price.put(EXPECTED_VERSION, policyVersion);
        return price;
    }

    /** A first policy (expected version 0) over the given window. */
    private static Map<String, Object> price(String amount, Instant validFrom, Instant validUntil) {
        Map<String, Object> price = new LinkedHashMap<>();
        price.put("amount", amount);
        price.put("currency", "sgd");
        price.put("validFrom", validFrom.toString());
        price.put("validUntil", validUntil.toString());
        price.put(EXPECTED_VERSION, 0);
        return price;
    }

    /** A product row in the given lifecycle state at version 0, never configured. */
    private UUID product(String status) {
        UUID id = UuidV7.next();
        jdbc.update(
                """
                INSERT INTO biz_product
                    (id, product_no, title, original_price, currency, stock, status, pricing_mode)
                VALUES (?, ?, 'Team terms fixture', ?::numeric, 'sgd', 5, ?, 'dual')
                """,
                id,
                "TT-" + id,
                ORIGINAL_PRICE,
                status);
        return id;
    }

    private int logRows(UUID id) {
        return count("SELECT COUNT(*) FROM log_product_team_terms WHERE product_id = ?", id);
    }

    private int policyCount(UUID id) {
        return count("SELECT COUNT(*) FROM biz_team_price_policy WHERE product_id = ?", id);
    }

    private int count(String sql, UUID id) {
        Integer count = jdbc.queryForObject(sql, Integer.class, id);
        return count == null ? 0 : count;
    }
}
