package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.util.UuidV7;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #252 acceptance with real migrations, SQL-backed RBAC and the read-only HTTP boundary. */
@SpringBootTest(properties = {"spring.data.redis.host=localhost", "spring.data.redis.port=1"})
@AutoConfigureMockMvc
@Testcontainers
@Import({TeamPriceControllerTest.SessionStore.class, SqlStatementCapture.Configuration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class AdminPaymentFlowIT {
    private static final String PATH = "/api/v1/admin/payments/attention";
    private static final String HEADER = "blocbuy-token";
    private static final Instant WHEN = Instant.parse("2026-10-02T12:00:00Z");

    @Container
    @SuppressWarnings("resource") // Testcontainers owns this disposable database.
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
    @Autowired private SqlStatementCapture sql;
    @Autowired private SqlSessionTemplate session;
    private UUID admin;
    private UUID buyer;
    private UUID deal;
    private String adminToken;
    private String buyerToken;

    @BeforeEach
    void seed() {
        admin = UuidV7.next();
        buyer = UuidV7.next();
        deal = UuidV7.next();
        jdbc.update(
                "INSERT INTO sys_user(id,register_source,status,nickname,email) "
                        + "VALUES (?,'google','active','Administrator',NULL), "
                        + "(?,'google','active','Tea buyer','private@example.com')",
                admin,
                buyer);
        jdbc.update(
                "INSERT INTO rel_user_role(user_id,role_id) "
                        + "SELECT ?,id FROM sys_role WHERE role_code='admin' AND NOT deleted",
                admin);
        jdbc.update(
                "INSERT INTO biz_group_deal(id,deal_no,product_id,leader_user_id,target_size,"
                        + "current_size,deal_price,currency,status,start_time,expire_time) "
                        + "VALUES (?,'BLOC-252',?,?,2,2,12,'sgd','success',now(),now())",
                deal,
                UuidV7.next(),
                buyer);
        adminToken = StpUtil.getStpLogic().createLoginSession(admin.toString());
        buyerToken = StpUtil.getStpLogic().createLoginSession(buyer.toString());
    }

    @AfterEach
    void clearSessions() {
        StpUtil.logoutByTokenValue(adminToken);
        StpUtil.logoutByTokenValue(buyerToken);
    }

    @Test
    void pendingRefundHasEveryColumnAndSucceededRefundDisappears() throws Exception {
        UUID payment = payment("compensation_required", "pending", WHEN);
        payment("compensation_required", "succeeded", WHEN.plusSeconds(10));
        String body =
                mvc.perform(get(PATH).header(HEADER, adminToken))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.data.total").value(1))
                        .andExpect(jsonPath("$.data.items[0].paymentId").value(payment.toString()))
                        .andExpect(jsonPath("$.data.items[0].buyerNickname").value("Tea buyer"))
                        .andExpect(jsonPath("$.data.items[0].dealId").value(deal.toString()))
                        .andExpect(jsonPath("$.data.items[0].dealNo").value("BLOC-252"))
                        .andExpect(jsonPath("$.data.items[0].reasonCode").value("GROUPBUY_409_002"))
                        .andExpect(jsonPath("$.data.items[0].amount").value("12.00"))
                        .andExpect(jsonPath("$.data.items[0].currency").value("sgd"))
                        .andExpect(jsonPath("$.data.items[0].refundStatus").value("pending"))
                        .andExpect(jsonPath("$.data.items[0].occurredAt").value(WHEN.toString()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContain("private@example.com", buyer.toString(), "userId", "email");
    }

    @Test
    void anonymousBuyerDisabledAdminAndRevokedCapabilityAreDenied() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header(HEADER, buyerToken)).andExpect(status().isForbidden());
        jdbc.update("UPDATE sys_user SET status='disabled' WHERE id=?", admin);
        session.clearCache();
        mvc.perform(get(PATH).header(HEADER, adminToken)).andExpect(status().isForbidden());
        jdbc.update("UPDATE sys_user SET status='active' WHERE id=?", admin);
        jdbc.update(
                "UPDATE rel_role_permission SET deleted=true WHERE permission_id IN "
                        + "(SELECT id FROM sys_permission WHERE perm_code='payment:read')");
        session.clearCache();
        mvc.perform(get(PATH).header(HEADER, adminToken)).andExpect(status().isForbidden());
    }

    @Test
    void capabilityWithoutAdminRoleIsInsufficient() throws Exception {
        jdbc.update(
                "INSERT INTO rel_role_permission(role_id,permission_id) "
                        + "SELECT role.id,permission.id FROM sys_role role CROSS JOIN sys_permission permission "
                        + "WHERE role.role_code='buyer' AND permission.perm_code='payment:read'");
        jdbc.update(
                "INSERT INTO rel_user_role(user_id,role_id) "
                        + "SELECT ?,id FROM sys_role WHERE role_code='buyer'",
                buyer);
        mvc.perform(get(PATH).header(HEADER, buyerToken)).andExpect(status().isForbidden());
    }

    @Test
    void stablePaginationIncludesOutstandingStatesButNotAppliedDeletedOrReviewOnlyRows()
            throws Exception {
        final UUID oldest = payment("compensation_required", "failed", WHEN);
        // Equal receipt times fall back to payment id DESC. UuidV7 is random within one
        // millisecond, so rank the two ids the way PostgreSQL does (unsigned bytes, the same as
        // lowercase hex order) instead of trusting generation order.
        UUID first = UuidV7.next();
        UUID second = UuidV7.next();
        boolean firstIsHigher = first.toString().compareTo(second.toString()) > 0;
        final UUID newest = firstIsHigher ? first : second;
        final UUID middle = firstIsHigher ? second : first;
        payment(middle, "compensation_required", "processing", WHEN.plusSeconds(1));
        payment(newest, "compensation_required", "review", WHEN.plusSeconds(1));
        payment("applied", "pending", WHEN.plusSeconds(2));
        UUID reviewOnly = payment("applied", null, WHEN.plusSeconds(3));
        jdbc.update(
                "INSERT INTO biz_payment_review_case(payment_id,reason) VALUES (?,'EXPIRY_FAILED')",
                reviewOnly);
        UUID deletedPayment = payment("compensation_required", "pending", WHEN.plusSeconds(4));
        jdbc.update("UPDATE biz_payment SET deleted=true WHERE id=?", deletedPayment);
        UUID deletedReceipt = payment("compensation_required", "pending", WHEN.plusSeconds(5));
        jdbc.update(
                "UPDATE biz_group_participation_receipt SET deleted=true WHERE payment_id=?",
                deletedReceipt);
        UUID unpaid = payment("compensation_required", "pending", WHEN.plusSeconds(6));
        jdbc.update("UPDATE biz_payment SET status='created' WHERE id=?", unpaid);
        mvc.perform(get(PATH).param("size", "1").header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].refundStatus").value("review"))
                .andExpect(jsonPath("$.data.items[0].paymentId").value(newest.toString()));
        mvc.perform(get(PATH).param("page", "2").param("size", "1").header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].refundStatus").value("processing"))
                .andExpect(jsonPath("$.data.items[0].paymentId").value(middle.toString()));
        mvc.perform(get(PATH).param("page", "3").param("size", "1").header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].refundStatus").value("failed"))
                .andExpect(jsonPath("$.data.items[0].paymentId").value(oldest.toString()));
        mvc.perform(get(PATH).param("page", "4").param("size", "1").header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void missingOrDeletedRefundRemainsPendingAndMissingBlocDoesNotHidePayment() throws Exception {
        UUID missingRefund = payment("compensation_required", null, WHEN);
        UUID deletedRefund = payment("compensation_required", "succeeded", WHEN.plusSeconds(1));
        jdbc.update("UPDATE biz_refund SET deleted=true WHERE payment_id=?", deletedRefund);
        jdbc.update(
                "UPDATE biz_group_participation_receipt SET deal_id=NULL WHERE payment_id=?",
                missingRefund);
        jdbc.update("UPDATE biz_group_deal SET deleted=true WHERE id=?", deal);
        mvc.perform(get(PATH).header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].refundStatus").value("pending"))
                .andExpect(jsonPath("$.data.items[1].dealNo").value("BLOC-252"));
        jdbc.update("DELETE FROM biz_group_deal WHERE id=?", deal);
        session.clearCache();
        mvc.perform(get(PATH).header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].dealNo").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "private", "PRIVATE", "private@example.com"})
    void blankAndEmailDerivedNicknamesNeverLeakContactInformation(String nickname)
            throws Exception {
        payment("compensation_required", "pending", WHEN);
        jdbc.update("UPDATE sys_user SET nickname=? WHERE id=?", nickname, buyer);
        mvc.perform(get(PATH).header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].buyerNickname").value("Unknown buyer"));
    }

    @Test
    void deletedBuyerUsesFallbackWithoutDroppingFinancialRow() throws Exception {
        payment("compensation_required", "pending", WHEN);
        jdbc.update("UPDATE sys_user SET deleted=true WHERE id=?", buyer);
        mvc.perform(get(PATH).header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].buyerNickname").value("Unknown buyer"));
    }

    @Test
    void pageHasFixedQueryCountAndDoesNotWriteFinancialState() throws Exception {
        for (int i = 0; i < 20; i++) {
            payment("compensation_required", "pending", WHEN.plusSeconds(i));
        }
        List<String> small = capture(1);
        List<String> large = capture(20);
        assertThat(large).hasSameSizeAs(small);
        assertThat(
                        large.stream()
                                .filter(
                                        statement ->
                                                statement.contains(
                                                        "biz_group_participation_receipt")))
                .hasSize(2);
        assertThat(large)
                .noneMatch(statement -> statement.matches("(?is).*\\b(insert|update|delete)\\b.*"));
    }

    private List<String> capture(int size) throws Exception {
        // Each MockMvc request joins this test transaction; model a fresh request-level SQL cache.
        session.clearCache();
        sql.start();
        List<String> captured;
        try {
            mvc.perform(get(PATH).param("size", Integer.toString(size)).header(HEADER, adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(size));
        } finally {
            captured = sql.finish();
        }
        return captured;
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "page=-1", "size=0", "size=101", "size=wrong"})
    void invalidPaginationIsRejected(String parameter) throws Exception {
        String[] parts = parameter.split("=");
        mvc.perform(get(PATH).param(parts[0], parts[1]).header(HEADER, adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyPopulationAndLargePageReturnBoundedEmptyResults() throws Exception {
        mvc.perform(get(PATH).header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items").isEmpty());
        payment("compensation_required", "pending", WHEN);
        session.clearCache();
        mvc.perform(
                        get(PATH)
                                .param("page", "2147483647")
                                .param("size", "100")
                                .header(HEADER, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    /** Fixture-only writes, rolled back after each test. No provider or settlement changes. */
    private UUID payment(String outcome, String refundStatus, Instant at) {
        return payment(UuidV7.next(), outcome, refundStatus, at);
    }

    private UUID payment(UUID id, String outcome, String refundStatus, Instant at) {
        UUID intent = UuidV7.next();
        jdbc.update(
                "INSERT INTO biz_payment(id,payment_no,user_id,provider,amount,currency,status,"
                        + "intent_id,operation,business_id) VALUES (?,?,?,'stripe',12,'sgd','succeeded',?,'JOIN',?)",
                id,
                id.toString(),
                buyer,
                intent,
                deal);
        jdbc.update(
                "INSERT INTO biz_group_participation_receipt(payment_id,request_fingerprint,deal_id,"
                        + "outcome,reason_code,create_time,create_by,update_by) VALUES (?,'fixture',?,?,"
                        + "'GROUPBUY_409_002',?,?,?)",
                id,
                deal,
                outcome,
                java.sql.Timestamp.from(at),
                buyer,
                buyer);
        if (refundStatus != null) {
            jdbc.update(
                    "INSERT INTO biz_refund(payment_id,intent_id,deal_id,amount,currency,reason,"
                            + "source_event,status) VALUES (?,?,?,12,'sgd','GROUPBUY_409_002','fixture',?)",
                    id,
                    intent,
                    deal,
                    refundStatus);
        }
        return id;
    }
}
