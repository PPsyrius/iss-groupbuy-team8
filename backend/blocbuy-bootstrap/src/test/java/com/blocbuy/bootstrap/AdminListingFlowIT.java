package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.catalog.model.ListingDetails;
import com.blocbuy.catalog.service.AdminListingAppService;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.identity.repository.RoleRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/** UC9 acceptance through MockMvc, real RBAC, transactions, converters and isolated PostgreSQL. */
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
class AdminListingFlowIT {
    private static final String BASE = "/api/v1/admin/products";
    private static final String HEADER = "blocbuy-token";
    private static final String DETAILS =
            """
            {"title":"Tea","description":"Leaf tea","images":["https://example.com/tea.png"],
             "originalPrice":"12.00","currency":"sgd","stock":5}
            """;

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
    @Autowired private AdminListingAppService listings;
    @Autowired private RoleRepository roles;
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

    /** All management operations reject anonymous and non-admin clients. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "create",
                "page",
                "counts",
                "get",
                "revise",
                "status",
                "teamTerms",
                "delete"
            })
    void everyRouteRequiresAdministrator(String operation) throws Exception {
        mvc.perform(request(operation)).andExpect(status().isUnauthorized());
        mvc.perform(request(operation).header(HEADER, buyerToken))
                .andExpect(status().isForbidden());
    }

    /** The current-session response reports the user's actual SQL-backed roles. */
    @Test
    void meReturnsPersistedRolesWithoutGrantingBuyerAdminAccess() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(admin.toString()))
                .andExpect(jsonPath("$.data.roles", hasItem("admin")));

        mvc.perform(get("/api/v1/auth/me").header(HEADER, buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(buyer.toString()))
                .andExpect(jsonPath("$.data.roles").isEmpty())
                .andExpect(jsonPath("$.data.roles", not(hasItem("admin"))));

        jdbc.update(
                """
                INSERT INTO rel_user_role (user_id, role_id)
                SELECT ?, id FROM sys_role WHERE role_code = 'buyer' AND deleted = false
                """,
                buyer);
        mvc.perform(get("/api/v1/auth/me").header(HEADER, buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItem("buyer")))
                .andExpect(jsonPath("$.data.roles", not(hasItem("admin"))));

        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    /** An administrator role alone must not bypass the independently checked capability. */
    @Test
    @Transactional
    void administratorStillNeedsCatalogPermission() throws Exception {
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
        assertThat(roles.findRoleCodesByUserId(admin)).contains("admin");
        assertThat(roles.findPermissionCodesByUserId(admin))
                .doesNotContain("catalog:product:write");
        mvc.perform(get(BASE).header(HEADER, adminToken)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/counts").header(HEADER, adminToken))
                .andExpect(status().isForbidden());
    }

    /** Delegating the capability alone must not grant access to administrator-only listing APIs. */
    @Test
    @Transactional
    void catalogPermissionWithoutAdministratorRoleIsInsufficient() throws Exception {
        UUID editorRole = UuidV7.next();
        jdbc.update(
                "INSERT INTO sys_role (id, role_code, role_name) VALUES (?, ?, 'Catalog editor')",
                editorRole,
                "editor-" + editorRole);
        jdbc.update(
                "INSERT INTO rel_user_role (user_id, role_id) VALUES (?, ?)", buyer, editorRole);
        assertThat(
                        jdbc.update(
                                """
                INSERT INTO rel_role_permission (role_id, permission_id)
                SELECT ?, id FROM sys_permission
                WHERE perm_code = 'catalog:product:write' AND deleted = false
                """,
                                editorRole))
                .isEqualTo(1);
        assertThat(roles.findPermissionCodesByUserId(buyer)).contains("catalog:product:write");
        assertThat(roles.findRoleCodesByUserId(buyer)).doesNotContain("admin");
        mvc.perform(get(BASE).header(HEADER, buyerToken)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/counts").header(HEADER, buyerToken))
                .andExpect(status().isForbidden());
    }

    /** Full CRUD round trip retains audit data and never exposes drafts to buyers. */
    @Test
    void draftCrudAndSoftDeletionPreserveAudit() throws Exception {
        UUID id = create("CRUD-" + UuidV7.next());
        mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/" + id).header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(0));
        mvc.perform(
                        put(BASE + "/" + id)
                                .header(HEADER, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"expectedVersion\":0,\"listing\":"
                                                + DETAILS.replace("Tea", "Green tea")
                                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listing.title").value("Green tea"))
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(
                        get(BASE)
                                .param("status", "draft")
                                .param("size", "1")
                                .header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("draft"));
        mvc.perform(
                        delete(BASE + "/" + id)
                                .param("expectedVersion", "1")
                                .header(HEADER, adminToken))
                .andExpect(status().isOk());
        mvc.perform(get(BASE + "/" + id).header(HEADER, adminToken))
                .andExpect(status().isNotFound());
        mvc.perform(
                        delete(BASE + "/" + id)
                                .param("expectedVersion", "1")
                                .header(HEADER, adminToken))
                .andExpect(status().isNotFound());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted FROM biz_product WHERE id = ?", Boolean.class, id))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM biz_product WHERE id = ?", Integer.class, id))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT update_by FROM biz_product WHERE id = ?", UUID.class, id))
                .isEqualTo(admin);
    }

    /** Publication affects browse but never permits a return to deletable draft state. */
    @Test
    void publishedListingCanBeDelistedButNotDeleted() throws Exception {
        UUID id = create("STATE-" + UuidV7.next());
        changeStatus(id, "on_sale", 0, 200);
        mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isOk());
        changeStatus(id, "off_sale", 1, 200);
        mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isNotFound());
        changeStatus(id, "draft", 2, 400);
        mvc.perform(
                        delete(BASE + "/" + id)
                                .param("expectedVersion", "2")
                                .header(HEADER, adminToken))
                .andExpect(status().isConflict());
        changeStatus(id, "on_sale", 2, 200);
    }

    /**
     * Unique business numbers and version checks reject duplicate/stale writes without mutation.
     */
    @Test
    void duplicateNumbersAndStaleWritesAreSafeConflicts() throws Exception {
        String number = "DUP-" + UuidV7.next();
        UUID id = create(number);
        mvc.perform(
                        post(BASE)
                                .header(HEADER, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody(number)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_409_003"));
        changeStatus(id, "on_sale", 0, 200);
        changeStatus(id, "off_sale", 0, 412);
        mvc.perform(
                        put(BASE + "/" + id)
                                .header(HEADER, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"expectedVersion\":1,\"listing\":"
                                                + DETAILS.replace("12.00", "15.00")
                                                + "}"))
                .andExpect(status().isConflict());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM biz_product WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    /** Omitted and explicit-null product numbers both generate persisted immutable references. */
    @ParameterizedTest
    @ValueSource(strings = {"", "\"productNo\":null,"})
    void missingProductNumberGeneratesPersistedDraft(String optionalNumber) throws Exception {
        var data =
                json.readTree(
                                mvc.perform(
                                                post(BASE)
                                                        .header(HEADER, adminToken)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(
                                                                "{"
                                                                        + optionalNumber
                                                                        + "\"listing\":"
                                                                        + DETAILS
                                                                        + "}"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.status").value("draft"))
                                        .andExpect(jsonPath("$.data.version").value(0))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data");
        String number = data.path("productNo").asText();
        UUID id = UUID.fromString(data.path("id").asText());
        assertThat(number).matches("BB-[1-9][0-9]{5,18}");
        assertThat(id.version()).isEqualTo(7);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT product_no FROM biz_product WHERE id = ?",
                                String.class,
                                id))
                .isEqualTo(number);
        mvc.perform(
                        put(BASE + "/" + id)
                                .header(HEADER, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"expectedVersion\":0,\"listing\":" + DETAILS + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productNo").value(number));
    }

    /**
     * Optional does not mean that empty, whitespace, malformed or oversized values are accepted.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " ",
                "-TEA",
                "TEA/001",
                "TEA\n",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            })
    void invalidSuppliedProductNumberIsStillRejected(String number) throws Exception {
        mvc.perform(
                        post(BASE)
                                .header(HEADER, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"productNo\":"
                                                + json.writeValueAsString(number)
                                                + ",\"listing\":"
                                                + DETAILS
                                                + "}"))
                .andExpect(status().isBadRequest());
    }

    /** Supplied BB numbers, including deleted history, cannot collide with generated references. */
    @Test
    void generatedNumberSkipsSuppliedNumbersAndDeletedHistory() throws Exception {
        long reserved =
                jdbc.queryForObject(
                                "SELECT nextval('domain.seq_biz_product_product_no')", Long.class)
                        + 1;
        UUID supplied = create("BB-" + reserved);
        create("BB-" + (reserved + 1));
        create("BB-999999999999999999999999999999");
        mvc.perform(
                        delete(BASE + "/" + supplied)
                                .header(HEADER, adminToken)
                                .param("expectedVersion", "0"))
                .andExpect(status().isOk());
        String number = createGeneratedAfter(new CountDownLatch(0));
        assertThat(Long.parseLong(number.substring(3))).isEqualTo(reserved + 2);
    }

    /** Large supplied suffixes must not advance or exhaust the generated-number sequence. */
    @ParameterizedTest
    @ValueSource(strings = {"9223372036854775807", "9223372036854775806"})
    @Transactional
    void suppliedBoundaryNumbersDoNotExhaustGeneration(String suffix) throws Exception {
        UUID supplied = create("BB-" + suffix);
        String first = createGeneratedAfter(new CountDownLatch(0));
        String nullResponse =
                mvc.perform(
                                post(BASE)
                                        .header(HEADER, adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"productNo\":null,\"listing\":" + DETAILS + "}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String second = json.readTree(nullResponse).path("data").path("productNo").asText();
        assertThat(Long.parseLong(second.substring(3)))
                .isGreaterThan(Long.parseLong(first.substring(3)))
                .isLessThan(Long.parseLong(suffix));
        mvc.perform(
                        delete(BASE + "/" + supplied)
                                .header(HEADER, adminToken)
                                .param("expectedVersion", "0"))
                .andExpect(status().isOk());
        String afterDeletion = createGeneratedAfter(new CountDownLatch(0));
        assertThat(Long.parseLong(afterDeletion.substring(3)))
                .isGreaterThan(Long.parseLong(second.substring(3)))
                .isLessThan(Long.parseLong(suffix));
    }

    /** Both HTTP creates finish successfully with different, persisted sequence-backed numbers. */
    @Test
    void concurrentCreatesWithoutNumbersAreUnique() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> createGeneratedAfter(start));
            var second = pool.submit(() -> createGeneratedAfter(start));
            start.countDown();
            String firstNumber = first.get(20, TimeUnit.SECONDS);
            String secondNumber = second.get(20, TimeUnit.SECONDS);
            assertThat(firstNumber).isNotEqualTo(secondNumber);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM biz_product WHERE product_no IN (?, ?) AND deleted = false",
                                    Integer.class,
                                    firstNumber,
                                    secondNumber))
                    .isEqualTo(2);
        }
    }

    /**
     * Exercise the actual servlet chain and independent application transactions on each thread.
     */
    private String createGeneratedAfter(CountDownLatch start) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("test barrier timed out");
        }
        String response =
                mvc.perform(
                                post(BASE)
                                        .header(HEADER, adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"listing\":" + DETAILS + "}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(response).path("data").path("productNo").asText();
    }

    /** Stale commands report the locked stored revision and leave all listing state intact. */
    @ParameterizedTest
    @ValueSource(strings = {"revise", "status", "delete", "teamTerms"})
    void staleListingWritesReturnStoredVersionWithoutMutation(String operation) throws Exception {
        UUID id = create("STALE-" + UuidV7.next());
        mvc.perform(
                        put(BASE + "/" + id)
                                .header(HEADER, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"expectedVersion\":0,\"listing\":" + DETAILS + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
        String item = BASE + "/" + id;
        MockHttpServletRequestBuilder stale =
                switch (operation) {
                    case "revise" ->
                            put(item)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"expectedVersion\":0,\"listing\":" + DETAILS + "}");
                    case "status" ->
                            put(item + "/status")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"expectedVersion\":0,\"status\":\"on_sale\"}");
                    case "delete" -> delete(item).param("expectedVersion", "0");
                    case "teamTerms" ->
                            put(item + "/team-terms")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"expectedVersion\":0,\"targetSize\":3,\"lifetimeSeconds\":7200}");
                    default -> throw new IllegalArgumentException(operation);
                };
        Integer storedVersion =
                jdbc.queryForObject(
                        "SELECT version FROM biz_product WHERE id = ?", Integer.class, id);
        mvc.perform(stale.header(HEADER, adminToken))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("CATALOG_412_002"))
                .andExpect(jsonPath("$.errors.currentVersion").value(storedVersion));
        mvc.perform(get(item).header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(storedVersion))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.teamTerms.configured").value(false));
    }

    /** Defensive legacy-data guards include expired/deleted quotes, policies and closed deals. */
    @ParameterizedTest
    @ValueSource(strings = {"policy", "quote", "deal"})
    void anyHistoricalReferenceVetoesDraftDeletion(String reference) throws Exception {
        UUID id = create("REF-" + UuidV7.next());
        switch (reference) {
            case "policy" ->
                    jdbc.update(
                            """
                            INSERT INTO biz_team_price_policy (product_id, amount, currency, valid_from,
                                valid_until, create_by, update_by, version, deleted)
                            VALUES (?, 9, 'sgd', now()-interval '1 hour', now(), ?, ?, 1, true)
                            """,
                            id,
                            admin,
                            admin);
            case "quote" ->
                    jdbc.update(
                            """
                            INSERT INTO biz_team_price_quote (product_id, owner_id, policy_version, amount,
                                currency, issued_at, expires_at, create_by, update_by, deleted)
                            VALUES (?, ?, 1, 9, 'sgd', now()-interval '1 hour', now(), ?, ?, true)
                            """,
                            id,
                            buyer,
                            admin,
                            admin);
            case "deal" ->
                    jdbc.update(
                            """
                            INSERT INTO biz_group_deal (deal_no, product_id, leader_user_id, target_size,
                                current_size, deal_price, currency, status, start_time, expire_time, deleted)
                            VALUES (?, ?, ?, 2, 0, 9, 'sgd', 'expired', now()-interval '1 hour', now(), true)
                            """,
                            "REF-" + id,
                            id,
                            admin);
            default -> throw new IllegalArgumentException(reference);
        }
        mvc.perform(
                        delete(BASE + "/" + id)
                                .param("expectedVersion", "0")
                                .header(HEADER, adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_409_004"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted FROM biz_product WHERE id = ?", Boolean.class, id))
                .isFalse();
    }

    /** Header identity alone cannot bypass an account disabled after login. */
    @Test
    void disabledAdministratorIsRejected() throws Exception {
        jdbc.update("UPDATE sys_user SET status = 'disabled' WHERE id = ?", admin);
        mvc.perform(get(BASE).header(HEADER, adminToken)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/counts").header(HEADER, adminToken))
                .andExpect(status().isForbidden());
    }

    /** Empty populations produce explicit numeric zeros, not missing aggregate fields. */
    @Test
    @Transactional
    void emptyDashboardCountsAreZero() throws Exception {
        jdbc.update("UPDATE biz_product SET deleted = true");
        jdbc.update("UPDATE biz_group_deal SET deleted = true");
        mvc.perform(get(BASE + "/counts").header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draft").value(0))
                .andExpect(jsonPath("$.data.onSale").value(0))
                .andExpect(jsonPath("$.data.offSale").value(0))
                .andExpect(jsonPath("$.data.openDeals").value(0));
    }

    /** Counts match each paged total and exclude deleted rows in every publication state. */
    @Test
    @Transactional
    void dashboardStatusCountsMatchPagedTotals() throws Exception {
        jdbc.update("UPDATE biz_product SET deleted = true");
        jdbc.update(
                """
                INSERT INTO biz_product (product_no, title, original_price, currency, stock, status, deleted)
                SELECT ? || '-' || fixture.n, 'Counted tea', 12, 'sgd', 5, fixture.status, fixture.deleted
                FROM (VALUES (1, 'draft', false), (2, 'draft', false),
                    (3, 'on_sale', false), (4, 'off_sale', false),
                    (5, 'draft', true), (6, 'on_sale', true), (7, 'off_sale', true))
                    AS fixture(n, status, deleted)
                """,
                "COUNTS-" + UuidV7.next());
        var counts =
                json.readTree(
                                mvc.perform(get(BASE + "/counts").header(HEADER, adminToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.draft").value(2))
                                        .andExpect(jsonPath("$.data.onSale").value(1))
                                        .andExpect(jsonPath("$.data.offSale").value(1))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data");
        for (var entry :
                Map.of("draft", "draft", "on_sale", "onSale", "off_sale", "offSale").entrySet()) {
            var page =
                    json.readTree(
                                    mvc.perform(
                                                    get(BASE)
                                                            .param("status", entry.getKey())
                                                            .param("size", "1")
                                                            .header(HEADER, adminToken))
                                            .andExpect(status().isOk())
                                            .andReturn()
                                            .getResponse()
                                            .getContentAsString())
                            .path("data");
            assertThat(counts.path(entry.getValue()).asLong())
                    .isEqualTo(page.path("total").asLong());
        }
    }

    /**
     * Count every eligible deal, not one per listing; reject stale open flags and closed history.
     */
    @Test
    @Transactional
    void dashboardOpenDealsExcludeFullExpiredClosedAndDeletedDeals() throws Exception {
        jdbc.update("UPDATE biz_group_deal SET deleted = true");
        UUID first = create("OPEN-" + UuidV7.next());
        UUID second = create("OPEN-" + UuidV7.next());
        jdbc.update(
                """
                INSERT INTO biz_group_deal (deal_no, product_id, leader_user_id, target_size,
                    current_size, deal_price, currency, status, start_time, expire_time, deleted)
                SELECT ? || '-' || fixture.n, CASE WHEN fixture.n = 3 THEN ? ELSE ? END,
                    ?, 3, fixture.members, 9, 'sgd', fixture.status,
                    CURRENT_TIMESTAMP - interval '1 hour',
                    CURRENT_TIMESTAMP + fixture.seconds * interval '1 second', fixture.deleted
                FROM (VALUES (1, 'open', 0, 3600, false), (2, 'open', 2, 3600, false),
                    (3, 'open', 1, 3600, false), (4, 'open', 3, 3600, false),
                    (5, 'open', 4, 3600, false), (6, 'open', 1, -1, false),
                    (7, 'open', 1, 0, false), (8, 'expired', 1, 3600, false),
                    (9, 'success', 3, 3600, false), (10, 'cancelled', 1, 3600, false),
                    (11, 'open', 1, 3600, true)) AS fixture(n, status, members, seconds, deleted)
                """,
                "DEAL-COUNTS-" + UuidV7.next(),
                second,
                first,
                admin);
        mvc.perform(get(BASE + "/counts").header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openDeals").value(3));
    }

    /** Bounded query parameters and server-owned fields are enforced before mutation. */
    @Test
    void invalidPayloadAndPaginationAreRejected() throws Exception {
        mvc.perform(get(BASE).param("size", "101").header(HEADER, adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("page", "0").header(HEADER, adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("status", "bogus").header(HEADER, adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(BASE)
                                .header(HEADER, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody("BAD").replace("\"12.00\"", "12.00")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(BASE)
                                .header(HEADER, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        createBody("BAD")
                                                .replace(
                                                        "\"stock\":5",
                                                        "\"stock\":5,\"soldCount\":99")))
                .andExpect(status().isBadRequest());
    }

    /** A missing concurrency precondition is client input failure, never a server error. */
    @Test
    void missingDeletionVersionIsBadRequestWithoutMutation() throws Exception {
        UUID id = create("MISSING-" + UuidV7.next());
        mvc.perform(delete(BASE + "/" + id).header(HEADER, adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted FROM biz_product WHERE id = ?", Boolean.class, id))
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM biz_product WHERE id = ?", Integer.class, id))
                .isZero();
    }

    /** PUT replacement clears omitted optional values in storage, not just the response DTO. */
    @Test
    void replacementClearsOmittedOptionalContent() throws Exception {
        UUID id = create("CLEAR-" + UuidV7.next());
        mvc.perform(
                        put(BASE + "/" + id)
                                .header(HEADER, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"expectedVersion":0,"listing":{
                                "title":"Plain tea","originalPrice":"12.00","currency":"sgd","stock":5}}
                                """))
                .andExpect(status().isOk());
        mvc.perform(get(BASE + "/" + id).header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listing.description").isEmpty())
                .andExpect(jsonPath("$.data.listing.images").isEmpty());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT description FROM biz_product WHERE id = ?",
                                String.class,
                                id))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT images::text FROM biz_product WHERE id = ?",
                                String.class,
                                id))
                .isEqualTo("[]");
    }

    /** Administrative pagination includes every retained state but excludes soft-deleted rows. */
    @Test
    void unfilteredPageIncludesAllNonDeletedStates() throws Exception {
        UUID draft = create("PAGE-DRAFT-" + UuidV7.next());
        UUID published = create("PAGE-LIVE-" + UuidV7.next());
        UUID delisted = create("PAGE-OFF-" + UuidV7.next());
        UUID removed = create("PAGE-DELETED-" + UuidV7.next());
        changeStatus(published, "on_sale", 0, 200);
        changeStatus(delisted, "on_sale", 0, 200);
        changeStatus(delisted, "off_sale", 1, 200);
        mvc.perform(
                        delete(BASE + "/" + removed)
                                .param("expectedVersion", "0")
                                .header(HEADER, adminToken))
                .andExpect(status().isOk());
        mvc.perform(get(BASE).param("size", "100").header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.items[*].id")
                                .value(
                                        hasItems(
                                                draft.toString(),
                                                published.toString(),
                                                delisted.toString())))
                .andExpect(jsonPath("$.data.items[*].id").value(not(hasItem(removed.toString()))));
    }

    /** A retired draft number may be reused without reviving or overwriting its old audit row. */
    @Test
    void deletedDraftNumberCanBeReusedWithNewIdentity() throws Exception {
        String number = "REUSE-" + UuidV7.next();
        UUID original = create(number);
        mvc.perform(
                        delete(BASE + "/" + original)
                                .param("expectedVersion", "0")
                                .header(HEADER, adminToken))
                .andExpect(status().isOk());
        UUID replacement = create(number);
        assertThat(replacement).isNotEqualTo(original);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_product WHERE product_no = ?",
                                Long.class,
                                number))
                .isEqualTo(2L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT deleted FROM biz_product WHERE id = ?",
                                Boolean.class,
                                original))
                .isTrue();
    }

    /**
     * Swagger and generated clients can discover cookie auth on admin and session endpoints without
     * protecting public operations.
     */
    @Test
    void openApiDocumentsAdministratorTokenWithoutProtectingPublicBrowse() throws Exception {
        var spec =
                json.readTree(
                        mvc.perform(get("/api/v3/api-docs"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        var scheme = spec.path("components").path("securitySchemes").path("blocbuyToken");
        assertThat(scheme.path("type").asText()).isEqualTo("apiKey");
        // the browser flow carries the token in the Sa-Token cookie, never in a header
        assertThat(scheme.path("in").asText()).isEqualTo("cookie");
        assertThat(scheme.path("name").asText()).isEqualTo(HEADER);
        for (var route :
                Map.of(
                                BASE,
                                List.of("get", "post"),
                                BASE + "/counts",
                                List.of("get"),
                                BASE + "/{productId}",
                                List.of("get", "put", "delete"),
                                BASE + "/{productId}/status",
                                List.of("put"),
                                BASE + "/{productId}/team-terms",
                                List.of("put"),
                                "/api/v1/auth/me",
                                List.of("get"),
                                "/api/v1/auth/logout",
                                List.of("post"))
                        .entrySet()) {
            for (String method : route.getValue()) {
                var security =
                        spec.path("paths").path(route.getKey()).path(method).path("security");
                assertThat(security.isArray()).isTrue();
                assertThat(security.path(0).path("blocbuyToken").isArray()).isTrue();
            }
        }
        assertThat(spec.has("security")).isFalse();
        assertThat(spec.path("paths").path("/api/v1/products").path("get").has("security"))
                .isFalse();
        // the OAuth handshake endpoints on AuthController are public paths — a class-level
        // @SecurityRequirement would wrongly mark them as authenticated
        assertThat(
                        spec.path("paths")
                                .path("/api/v1/auth/oauth2/google")
                                .path("get")
                                .has("security"))
                .isFalse();
        assertThat(
                        spec.path("paths")
                                .path("/api/v1/auth/oauth2/google/callback")
                                .path("get")
                                .has("security"))
                .isFalse();
    }

    /** OpenAPI patterns must validate complete input, like Bean Validation does at runtime. */
    @Test
    void openApiPatternsRejectSubstringMatches() throws Exception {
        var spec =
                json.readTree(
                        mvc.perform(get("/api/v3/api-docs"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        var schemas = spec.path("components").path("schemas");
        var content = schemas.path("ListingContent").path("properties");
        assertFullPattern(
                content.path("currency").path("pattern").asText(), "sgd", "xsgd", "sgdx", "sgd\n");
        assertFullPattern(
                content.path("originalPrice").path("pattern").asText(),
                "12.00",
                "x12.00",
                "12.00x",
                "12.00\n",
                "0",
                "0.00",
                "00.00");
        for (String amount : List.of("0.01", "00.01", "12", "9999999999999999.99")) {
            assertFullPattern(content.path("originalPrice").path("pattern").asText(), amount);
        }
        assertFullPattern(
                schemas.path("CreateListingRequest")
                        .path("properties")
                        .path("productNo")
                        .path("pattern")
                        .asText(),
                "SKU-1",
                "#SKU-1",
                "SKU-1#",
                "A".repeat(65));
        assertFullPattern(
                schemas.path("ListingStatusRequest")
                        .path("properties")
                        .path("status")
                        .path("pattern")
                        .asText(),
                "on_sale",
                "xon_sale",
                "off_salex",
                "on_sale\n");
    }

    /** Use substring search to model JSON Schema pattern semantics, not Java matches(). */
    private static void assertFullPattern(String expression, String accepted, String... rejected) {
        assertThat(expression).isNotBlank();
        Pattern pattern = Pattern.compile(expression);
        assertThat(pattern.matcher(accepted).find()).isTrue();
        for (String value : rejected) {
            assertThat(pattern.matcher(value).find())
                    .as("pattern must reject <%s>", value)
                    .isFalse();
        }
    }

    /** Concurrent revisions from the same snapshot commit exactly once. */
    @Test
    void concurrentEditsCommitOneRevision() throws Exception {
        UUID id = create("RACE-" + UuidV7.next());
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> reviseAfter(start, id, "A"));
            var second = pool.submit(() -> reviseAfter(start, id, "B"));
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("ok", "CATALOG_412_002");
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM biz_product WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    /**
     * Competing commands use the real transactional proxy; only business conflicts are converted.
     */
    private String reviseAfter(CountDownLatch start, UUID id, String title)
            throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("test barrier timed out");
        }
        try {
            listings.revise(
                    id,
                    new ListingDetails(
                            title,
                            null,
                            null,
                            null,
                            List.of(),
                            null,
                            new BigDecimal("12.00"),
                            "sgd",
                            5),
                    0,
                    admin);
            return "ok";
        } catch (BizException ex) {
            assertThat(ex.details()).containsEntry("currentVersion", 1);
            return ex.errorCode().code();
        }
    }

    /** Distinct valid requests for every protected route. */
    private MockHttpServletRequestBuilder request(String operation) {
        String item = BASE + "/" + UuidV7.next();
        return switch (operation) {
            case "create" ->
                    post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("SECURITY"));
            case "page" -> get(BASE);
            case "counts" -> get(BASE + "/counts");
            case "get" -> get(item);
            case "revise" ->
                    put(item)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":0,\"listing\":" + DETAILS + "}");
            case "status" ->
                    put(item + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":0,\"status\":\"on_sale\"}");
            case "teamTerms" ->
                    put(item + "/team-terms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    "{\"expectedVersion\":0,\"targetSize\":3,\"lifetimeSeconds\":7200}");
            case "delete" -> delete(item).param("expectedVersion", "0");
            default -> throw new IllegalArgumentException(operation);
        };
    }

    /** Return the created server-generated identifier after asserting the draft contract. */
    private UUID create(String number) throws Exception {
        String response =
                mvc.perform(
                                post(BASE)
                                        .header(HEADER, adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createBody(number)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("draft"))
                        .andExpect(jsonPath("$.data.version").value(0))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(json.readTree(response).path("data").path("id").asText());
    }

    /** Build a decimal-string request without client-controlled identity or status. */
    private static String createBody(String number) {
        return "{\"productNo\":\"" + number + "\",\"listing\":" + DETAILS + "}";
    }

    /** Exercise conditional lifecycle changes through the public protocol. */
    private void changeStatus(UUID id, String next, int version, int expectedStatus)
            throws Exception {
        mvc.perform(
                        put(BASE + "/" + id + "/status")
                                .header(HEADER, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "status",
                                                        next,
                                                        "expectedVersion",
                                                        version))))
                .andExpect(status().is(expectedStatus));
    }
}
