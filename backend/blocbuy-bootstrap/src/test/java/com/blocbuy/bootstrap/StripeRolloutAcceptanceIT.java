package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocbuy.bootstrap.fixture.StripeTestDatabase;
import com.blocbuy.bootstrap.scheduling.RefundWorkerJob;
import com.blocbuy.bootstrap.scheduling.TeamExpirySweepJob;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.RefundGateway;
import com.blocbuy.payment.gateway.StripeCheckoutGateway;
import com.blocbuy.payment.gateway.StripeClientProvider;
import com.blocbuy.payment.gateway.StripeProperties;
import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.RefundWork;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.jayway.jsonpath.JsonPath;
import com.stripe.Stripe;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * #223 local rehearsal: unchanged buyer HTTP contracts and production business/scheduler wiring.
 * Only OAuth/session storage and Stripe network calls are substitutes; signature checks are real.
 * Production Flyway only: no local business adapter or test-only database migration is needed.
 */
@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1",
            "blocbuy.payment.checkout.success-url=http://localhost:5173/payments/return",
            "blocbuy.payment.checkout.cancel-url=http://localhost:5173/payments/return",
            "blocbuy.payment.stripe.checkout-enabled=true",
            "blocbuy.payment.stripe.api-key=sk_test_local_fixture",
            "blocbuy.payment.stripe.webhook-secret=rollout-test-not-a-provider-credential",
            "blocbuy.scheduling.enabled=true",
            "blocbuy.scheduling.team-expiry.initial-delay=PT1H",
            "blocbuy.scheduling.refund-worker.initial-delay=PT1H"
        })
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Import(AuthControllerTest.SessionStore.class)
@DirtiesContext
class StripeRolloutAcceptanceIT {
    private static final StripeTestDatabase DATABASE =
            new StripeTestDatabase("blocbuy_stripe_local");
    private static final String SIGNING_FIXTURE = "rollout-test-not-a-provider-credential";
    private final Map<String, CheckoutGateway.Session> sessions = new HashMap<>();
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CheckoutPaymentRepository payments;
    @Autowired private TeamExpirySweepJob expiry;
    @Autowired private RefundWorkerJob worker;
    @MockitoBean private OAuthLoginAppService oauth;
    @MockitoBean private CheckoutGateway checkout;
    @MockitoBean private RefundGateway refunds;
    private UUID product;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::url);
        properties.add("spring.datasource.username", DATABASE::username);
        properties.add("spring.datasource.password", DATABASE::password);
    }

    @BeforeEach
    void setup() {
        jdbc.execute(
                "TRUNCATE sys_scheduled_item_retry,sys_scheduler_lock,log_refund_attempt,"
                        + "biz_payment,log_payment_webhook_event,biz_refund,log_payment_review,"
                        + "biz_payment_review_case,biz_purchase_intent,biz_order,biz_group_member,"
                        + "biz_group_deal,biz_group_participation_receipt,biz_share_invite,"
                        + "biz_share_credit,biz_team_price_policy,biz_team_price_quote,biz_product,sys_user");
        product = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO biz_product(id,product_no,title,original_price,currency,stock,status,"
                        + "team_target_size,team_lifetime_seconds)"
                        + " VALUES(?,?,'Rollout rehearsal',20,'sgd',100,'on_sale',3,7200)",
                product,
                product.toString());
        UUID admin = buyer();
        jdbc.update(
                "INSERT INTO biz_team_price_policy(product_id,amount,currency,valid_from,"
                        + "valid_until,create_by,update_by,version) VALUES(?,10,'sgd',"
                        + "clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 day',?,?,1)",
                product,
                admin,
                admin);
        when(checkout.createCheckoutSession(any()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            CheckoutPayment payment = call.getArgument(0);
                            var session = session(payment, false);
                            sessions.put(session.id(), session);
                            return session;
                        });
        when(checkout.retrieveCheckoutSession(any()))
                .thenAnswer(call -> sessions.get(call.getArgument(0)));
        var properties = new StripeProperties("", SIGNING_FIXTURE);
        var signatures =
                new StripeCheckoutGateway(properties, new StripeClientProvider(properties));
        when(checkout.verifyCheckoutWebhook(any(), any()))
                .thenAnswer(
                        call ->
                                signatures.verifyCheckoutWebhook(
                                        call.getArgument(0), call.getArgument(1)));
        when(refunds.createRefund(any())).thenAnswer(call -> succeeded(call.getArgument(0)));
    }

    @Test
    void paidTeamExpiresAndRefundsEveryMemberOnceThroughTheBuyerContracts() throws Exception {
        Cookie leader = login(buyer());
        CheckoutPayment first = prepare(leader, "START", product);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_order", Long.class)).isZero();
        settle(first, "evt_rollout_start");
        UUID team = team(first);
        Cookie member = login(buyer());
        CheckoutPayment second = prepare(member, "JOIN", team);
        settle(second, "evt_rollout_join");
        assertThat(jdbc.queryForObject("SELECT current_size FROM biz_group_deal", Integer.class))
                .isEqualTo(2);
        overdue(team);

        expiry.run();
        receipt(leader, first, "PENDING", true);
        mvc.perform(get("/api/v1/group-deals/me").cookie(leader))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items[0].dealId").value(team.toString()))
                .andExpect(jsonPath("$.data.items[0].status").value("expired"))
                .andExpect(jsonPath("$.data.items[0].refundStatus").doesNotExist());
        mvc.perform(get("/api/v1/orders/me").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("cancelled"));

        worker.run();
        worker.run();
        worker.run();
        expiry.run();
        settle(first, "evt_rollout_start");
        settle(second, "evt_rollout_join_again");

        receipt(leader, first, "SUCCEEDED", false);
        receipt(member, second, "SUCCEEDED", false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_refund", Long.class)).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_refund WHERE status='succeeded'",
                                Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_order", Long.class)).isEqualTo(2);
        verify(refunds, times(2)).createRefund(any());
        mvc.perform(get("/api/v1/payments/" + first.id()).cookie(member))
                .andExpect(status().isNotFound());
    }

    @Test
    void providerOutageStaysVisibleAndRetryUsesTheSameRefundKey() throws Exception {
        Cookie owner = login(buyer());
        CheckoutPayment payment = prepare(owner, "START", product);
        settle(payment, "evt_rollout_retry");
        overdue(team(payment));
        expiry.run();
        Map<UUID, String> keys = new HashMap<>();
        doAnswer(
                        call -> {
                            RefundWork work = call.getArgument(0);
                            keys.put(work.id(), work.providerKey());
                            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
                        })
                .doAnswer(
                        call -> {
                            RefundWork work = call.getArgument(0);
                            assertThat(work.providerKey()).isEqualTo(keys.get(work.id()));
                            return succeeded(work);
                        })
                .when(refunds)
                .createRefund(any());
        worker.run();
        receipt(owner, payment, "PROCESSING", true);
        jdbc.update("UPDATE biz_refund SET next_attempt_at=clock_timestamp()-interval '1 second'");
        worker.run();
        worker.run();
        receipt(owner, payment, "SUCCEEDED", false);
        verify(refunds, times(2)).createRefund(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_refund", Long.class)).isEqualTo(1);
    }

    @Test
    void providerSucceededBeforeWorkerLossRetriesWithOneDurableRefundKey() throws Exception {
        Cookie owner = login(buyer());
        CheckoutPayment payment = prepare(owner, "START", product);
        settle(payment, "evt_rollout_crash");
        overdue(team(payment));
        expiry.run();

        Map<String, RefundGateway.Snapshot> providerResults = new HashMap<>();
        AtomicInteger providerCreations = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        doAnswer(
                        call -> {
                            RefundWork work = call.getArgument(0);
                            RefundGateway.Snapshot snapshot =
                                    providerResults.computeIfAbsent(
                                            work.providerKey(),
                                            key -> {
                                                providerCreations.incrementAndGet();
                                                return succeeded(work);
                                            });
                            if (calls.getAndIncrement() == 0) {
                                throw new IllegalStateException(
                                        "Simulated worker loss after provider success");
                            }
                            return snapshot;
                        })
                .when(refunds)
                .createRefund(any());

        worker.run();
        assertThat(jdbc.queryForObject("SELECT status FROM biz_refund", String.class))
                .isEqualTo("pending");
        assertThat(jdbc.queryForObject("SELECT provider_refund_id FROM biz_refund", String.class))
                .isNull();
        assertThat(providerCreations).hasValue(1);
        jdbc.update("UPDATE biz_refund SET lease_until=clock_timestamp()-interval '1 second'");

        worker.run();
        receipt(owner, payment, "SUCCEEDED", false);
        assertThat(providerResults).hasSize(1);
        assertThat(providerCreations).hasValue(1);
        assertThat(calls).hasValue(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_refund", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM biz_refund", String.class))
                .isEqualTo("succeeded");
        verify(refunds, times(2)).createRefund(any());
    }

    @Test
    void permanentProviderRefusalParksTheObligationForOperatorReview() throws Exception {
        Cookie owner = login(buyer());
        CheckoutPayment payment = prepare(owner, "START", product);
        settle(payment, "evt_rollout_permanent");
        overdue(team(payment));
        expiry.run();
        doAnswer(
                        call -> {
                            throw new BizException(PaymentErrorCode.PAYMENT_STATE_INVALID);
                        })
                .when(refunds)
                .createRefund(any());

        worker.run();
        worker.run();

        assertThat(jdbc.queryForObject("SELECT status FROM biz_refund", String.class))
                .isEqualTo("review");
        assertThat(jdbc.queryForObject("SELECT last_result FROM biz_refund", String.class))
                .isEqualTo("PROVIDER_STATE_MISMATCH");
        verify(refunds, times(1)).createRefund(any());
    }

    @Test
    void replayedSignedRefundNotificationHasNoCheckoutOrRefundSideEffect() throws Exception {
        Cookie owner = login(buyer());
        CheckoutPayment payment = prepare(owner, "START", product);
        settle(payment, "evt_rollout_refund_replay");
        overdue(team(payment));
        expiry.run();
        worker.run();
        long auditsBefore =
                jdbc.queryForObject("SELECT count(*) FROM log_refund_attempt", Long.class);
        long eventsBefore =
                jdbc.queryForObject("SELECT count(*) FROM log_payment_webhook_event", Long.class);
        String body =
                """
                {"id":"evt_refund_notification","object":"event","api_version":"%s",
                "livemode":false,"type":"refund.updated","data":{"object":
                {"id":"re_fixture","object":"refund"}}}
                """
                        .formatted(Stripe.API_VERSION);

        postSignedWebhook(body);
        postSignedWebhook(body);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_refund", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM biz_refund", String.class))
                .isEqualTo("succeeded");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM log_refund_attempt", Long.class))
                .isEqualTo(auditsBefore);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM log_payment_webhook_event", Long.class))
                .isEqualTo(eventsBefore);
        verify(refunds, times(1)).createRefund(any());
    }

    @Test
    void latePaidJoinIsCompensatedWithoutCreatingAnOrder() throws Exception {
        Cookie leader = login(buyer());
        CheckoutPayment first = prepare(leader, "START", product);
        settle(first, "evt_rollout_leader");
        UUID team = team(first);
        Cookie lateBuyer = login(buyer());
        CheckoutPayment late = prepare(lateBuyer, "JOIN", team);
        overdue(team);
        expiry.run();
        settle(late, "evt_rollout_late");
        settle(late, "evt_rollout_late");
        worker.run();
        worker.run();
        worker.run();
        receipt(lateBuyer, late, "SUCCEEDED", false);
        mvc.perform(get("/api/v1/payments/" + late.id()).cookie(lateBuyer))
                .andExpect(jsonPath("$.data.outcome").value("REFUND_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_order", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_refund", Long.class)).isEqualTo(2);
        verify(refunds, times(2)).createRefund(any());
    }

    @Test
    void authCsrfAndWebhookSignatureFailuresNeverCreateBusinessState() throws Exception {
        mvc.perform(get("/api/v1/payments/capabilities")).andExpect(status().isUnauthorized());
        Cookie owner = login(buyer());
        mvc.perform(
                        post("/api/v1/payments/intents")
                                .cookie(owner)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-BlocBuy-Request", "wrong")
                                .content(
                                        "{\"operation\":\"START\",\"businessId\":\""
                                                + product
                                                + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/webhooks/stripe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", "t=0,v1=invalid")
                                .content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_purchase_intent", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_payment", Long.class)).isZero();
    }

    @Test
    void backlogSnapshotSeparatesRunnableLeasedFutureAndReviewWorkWithoutWriting()
            throws Exception {
        var candidates = new java.util.ArrayList<CheckoutPayment>();
        for (int i = 0; i < 4; i++) {
            Cookie owner = login(buyer());
            CheckoutPayment payment = prepare(owner, "START", product);
            settle(payment, "evt_rollout_backlog_" + i);
            overdue(team(payment));
            candidates.add(payment);
        }
        expiry.run();
        jdbc.update(
                "UPDATE biz_refund SET lease_until=clock_timestamp()+interval '5 minutes' WHERE payment_id=?",
                candidates.get(1).id());
        jdbc.update(
                "UPDATE biz_refund SET next_attempt_at=clock_timestamp()+interval '5 minutes' WHERE payment_id=?",
                candidates.get(2).id());
        jdbc.update(
                "UPDATE biz_refund SET status='review' WHERE payment_id=?", candidates.get(3).id());
        String sql = Files.readString(Path.of("../../scripts/payment/rollout-backlog.sql"));
        String snapshot = null;
        try (var connection = jdbc.getDataSource().getConnection();
                var statement = connection.createStatement()) {
            boolean hasRows = statement.execute(sql);
            while (hasRows || statement.getUpdateCount() != -1) {
                if (hasRows) {
                    try (var result = statement.getResultSet()) {
                        assertThat(result.next()).isTrue();
                        snapshot = result.getString(1);
                    }
                }
                hasRows = statement.getMoreResults();
            }
        }
        assertThat(snapshot).isNotNull();
        assertThat((Integer) JsonPath.read(snapshot, "$.unfinished_refunds")).isEqualTo(3);
        assertThat((Integer) JsonPath.read(snapshot, "$.runnable_refunds")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(snapshot, "$.leased_unfinished_refunds")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(snapshot, "$.review_or_failed_refunds")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(snapshot, "$.overdue_open_teams")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_refund", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM log_refund_attempt", Long.class))
                .isZero();
    }

    private UUID buyer() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO sys_user(id,register_source,status) VALUES(?,'local','active')", id);
        return id;
    }

    private Cookie login(UUID buyer) throws Exception {
        when(oauth.loginWithGoogle(any(), any(), any()))
                .thenReturn(new OAuthLoginResult(buyer, "Rehearsal buyer", null));
        Cookie cookie =
                mvc.perform(
                                get("/api/v1/auth/oauth2/google/callback")
                                        .param("code", "test")
                                        .param("state", "test")
                                        .cookie(new Cookie("blocbuy-oauth-state", "test")))
                        .andExpect(status().isFound())
                        .andReturn()
                        .getResponse()
                        .getCookie("blocbuy-token");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private CheckoutPayment prepare(Cookie buyer, String operation, UUID business)
            throws Exception {
        mvc.perform(get("/api/v1/payments/capabilities").cookie(buyer))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.enabled").value(true));
        String intentBody =
                mvc.perform(
                                post("/api/v1/payments/intents")
                                        .cookie(buyer)
                                        .header("X-BlocBuy-Request", "checkout")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"operation\":\"%s\",\"businessId\":\"%s\"}"
                                                        .formatted(operation, business)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String intent = JsonPath.read(intentBody, "$.data.intentId");
        String key = UUID.randomUUID().toString();
        String result =
                mvc.perform(
                                post("/api/v1/payments/checkout-sessions")
                                        .cookie(buyer)
                                        .header("X-BlocBuy-Request", "checkout")
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"intentId\":\"" + intent + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID paymentId = UUID.fromString(JsonPath.read(result, "$.data.paymentId"));
        mvc.perform(
                        post("/api/v1/payments/checkout-sessions")
                                .cookie(buyer)
                                .header("X-BlocBuy-Request", "checkout")
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"intentId\":\"" + intent + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(paymentId.toString()));
        return payments.findById(paymentId).orElseThrow();
    }

    private void receipt(Cookie buyer, CheckoutPayment payment, String refund, boolean pending)
            throws Exception {
        mvc.perform(get("/api/v1/payments/" + payment.id()).cookie(buyer))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.refundStatus").value(refund))
                .andExpect(jsonPath("$.data.refundPending").value(pending));
    }

    @SuppressWarnings("deprecation")
    private void settle(CheckoutPayment payment, String event) throws Exception {
        sessions.put(payment.sessionId(), session(payment, true));
        String body =
                """
                {"id":"%s","object":"event","api_version":"%s","livemode":false,
                "type":"checkout.session.completed","data":{"object":
                {"id":"%s","object":"checkout.session"}}}
                """
                        .formatted(event, Stripe.API_VERSION, payment.sessionId());
        postSignedWebhook(body);
    }

    private void postSignedWebhook(String body) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGNING_FIXTURE.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature =
                "t="
                        + timestamp
                        + ",v1="
                        + HexFormat.of()
                                .formatHex(
                                        mac.doFinal(
                                                (timestamp + "." + body)
                                                        .getBytes(StandardCharsets.UTF_8)));
        mvc.perform(
                        post("/api/v1/webhooks/stripe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", signature)
                                .content(body))
                .andExpect(status().isOk());
    }

    private UUID team(CheckoutPayment payment) {
        return jdbc.queryForObject(
                "SELECT deal_id FROM biz_group_member WHERE payment_id=?",
                UUID.class,
                payment.id());
    }

    private void overdue(UUID team) {
        jdbc.update(
                "UPDATE biz_group_deal SET expire_time=clock_timestamp()-interval '1 second' WHERE id=?",
                team);
    }

    private static CheckoutGateway.Session session(CheckoutPayment payment, boolean paid) {
        return new CheckoutGateway.Session(
                "cs_test_" + payment.id(),
                payment.id().toString(),
                payment.quote().intentId().toString(),
                paid ? "pi_" + payment.id() : null,
                payment.quote().amount().movePointRight(2).longValueExact(),
                payment.quote().currency(),
                paid ? "complete" : "open",
                paid ? "paid" : "unpaid",
                false,
                "https://checkout.stripe.com/c/pay/cs_test_" + payment.id(),
                payment.sessionExpiresAt());
    }

    private static RefundGateway.Snapshot succeeded(RefundWork work) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return new RefundGateway.Snapshot(
                "re_" + work.id().toString().replace("-", ""),
                work.providerIntentId(),
                work.id().toString(),
                work.amount().movePointRight(2).longValueExact(),
                work.currency(),
                "succeeded");
    }
}
