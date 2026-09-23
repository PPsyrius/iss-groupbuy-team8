package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocbuy.bootstrap.fixture.StripeTestDatabase;
import com.blocbuy.common.persistence.UuidTypeHandler;
import com.blocbuy.common.web.GlobalExceptionHandler;
import com.blocbuy.payment.event.PaymentSucceeded;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.gateway.StripeCheckoutGateway;
import com.blocbuy.payment.gateway.StripeClientProvider;
import com.blocbuy.payment.gateway.StripeProperties;
import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.CheckoutQuote;
import com.blocbuy.payment.persistence.CheckoutPaymentRepositoryImpl;
import com.blocbuy.payment.persistence.PaymentMapper;
import com.blocbuy.payment.persistence.RefundMapper;
import com.blocbuy.payment.service.StripeCheckoutService;
import com.blocbuy.payment.service.StripePaymentAppService;
import com.blocbuy.payment.service.TeamExpiryRefundService;
import com.blocbuy.payment.web.StripeWebhookController;
import com.stripe.Stripe;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real PostgreSQL/MyBatis transactions; provider and group/order collaborators are test doubles.
 */
class StripeCheckoutFlowTest {
    private static StripeTestDatabase testDatabase;
    private static CheckoutPaymentRepositoryImpl repository;
    private static RefundMapper refunds;
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager transactions;
    private static final UUID TEAM = UUID.randomUUID();
    private static final String HTTP_PATH = "/api/v1/webhooks/stripe";
    private static final String SIGNING_FIXTURE = "local-http-test-not-a-stripe-credential";
    private final Map<UUID, CheckoutQuote> quotes = new ConcurrentHashMap<>();
    private final Map<String, CheckoutGateway.Session> sessions = new ConcurrentHashMap<>();
    private final AtomicBoolean failFulfilment = new AtomicBoolean();
    private CheckoutGateway gateway;
    private StripeCheckoutService service;
    private TeamExpiryRefundService expiryRefunds;

    @BeforeAll
    static void database() throws Exception {
        testDatabase = new StripeTestDatabase("blocbuy_stripe_test");
        var ds =
                new DriverManagerDataSource(
                        testDatabase.url(), testDatabase.username(), testDatabase.password());
        Flyway.configure()
                .dataSource(ds)
                .defaultSchema("domain")
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(ds);
        transactions = new DataSourceTransactionManager(ds);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        var config = new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.getTypeHandlerRegistry().register(UUID.class, new UuidTypeHandler());
        factory.setConfiguration(config);
        factory.setMapperLocations(
                new ClassPathResource("mapper/payment/PaymentMapper.xml"),
                new ClassPathResource("mapper/payment/RefundMapper.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        repository = new CheckoutPaymentRepositoryImpl(session.getMapper(PaymentMapper.class));
        refunds = session.getMapper(RefundMapper.class);
        jdbc.execute(
                "CREATE TABLE stripe_test_team(id uuid PRIMARY KEY, seats int NOT NULL, expired boolean NOT NULL)");
        jdbc.execute(
                "CREATE TABLE stripe_test_orders(payment_id uuid PRIMARY KEY, order_id uuid NOT NULL)");
    }

    @AfterAll
    static void stopDatabase() {
        if (testDatabase != null) testDatabase.close();
    }

    @BeforeEach
    void setup() {
        jdbc.execute(
                "TRUNCATE biz_payment,log_payment_webhook_event,biz_refund,log_payment_review,biz_payment_review_case,"
                        + "stripe_test_orders,stripe_test_team");
        jdbc.update("INSERT INTO stripe_test_team VALUES (?,1,false)", TEAM);
        gateway = mock(CheckoutGateway.class);
        when(gateway.createCheckoutSession(any()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            CheckoutPayment payment = call.getArgument(0);
                            return sessions.computeIfAbsent(
                                    "cs_test_" + payment.id(), key -> session(payment, false));
                        });
        when(gateway.retrieveCheckoutSession(any()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return sessions.get(call.getArgument(0));
                        });
        var business = new TestBusiness();
        service =
                new StripeCheckoutService(
                        repository,
                        gateway,
                        business,
                        transactions,
                        "http://localhost:5173/payment/success",
                        "http://localhost:5173/payment/cancel");
        expiryRefunds = new TeamExpiryRefundService(repository, business, transactions);
    }

    @Test
    void checkoutPaidReplayAndRefundAreDurableAndIdempotent() {
        CheckoutPayment payment = prepare();
        assertThat(repository.findById(payment.id())).isPresent();
        assertThat(
                        service.prepare(
                                        payment.quote().userId(),
                                        payment.quote().intentId(),
                                        "test-key")
                                .paymentId())
                .isEqualTo(payment.id());
        paid(payment);
        webhook(payment, "evt_first");
        webhook(payment, "evt_first");
        webhook(payment, "evt_other");
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.SUCCEEDED);
        assertThat(count("stripe_test_orders")).isEqualTo(1);
        assertThat(count("log_payment_webhook_event")).isEqualTo(2);
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            jdbc.update(
                                    "UPDATE stripe_test_team SET expired=true WHERE id=?", TEAM);
                            expiryRefunds.recordExpiredTeam(TEAM);
                        });
        expiryRefunds.recordExpiredTeam(TEAM);
        assertThat(count("biz_refund")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM biz_refund", String.class))
                .isEqualTo("pending");
    }

    @Test
    void softDeletedPaymentIsExcludedFromOperationalReadsAndWrites() {
        CheckoutPayment payment = prepare();
        UUID paymentId = payment.id();
        jdbc.update(
                "UPDATE biz_payment SET status='succeeded',fulfilment_result='FULFILLED',"
                        + "fulfilled_deal_id=?,deleted=true WHERE id=?",
                TEAM,
                paymentId);
        jdbc.update(
                "INSERT INTO biz_payment_review_case(payment_id,reason) VALUES (?,?)",
                paymentId,
                "DELETED_FIXTURE");

        assertThat(repository.findById(paymentId)).isEmpty();
        assertThat(repository.findRequest(payment.quote().userId(), "test-key")).isEmpty();
        assertThat(repository.findBlockingAttempt(payment.quote().intentId())).isEmpty();
        repository.requireRefundsForExpiredTeam(TEAM, "TEAM_EXPIRED", "evt_deleted_team");
        assertThat(repository.pendingBefore(repository.now(), 100)).isEmpty();
        assertThat(repository.reviewCases(null, 100)).isEmpty();
        assertThat(repository.outcome(paymentId)).isNull();
        assertThatThrownBy(() -> repository.lock(paymentId))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.save(payment))
                .isInstanceOf(IllegalStateException.class);
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> repository.recordOutcome(paymentId, TEAM, orderId, "FULFILLED"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.requireRefund(payment, "TEAM_EXPIRED", "evt_deleted"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("biz_refund")).isZero();

        jdbc.update(
                "INSERT INTO biz_refund(payment_id,intent_id,deal_id,amount,currency,reason,"
                        + "source_event,status) VALUES (?,?,?,?,?,?,?,'pending')",
                paymentId,
                payment.quote().intentId(),
                TEAM,
                payment.quote().amount(),
                payment.quote().currency(),
                "TEAM_EXPIRED",
                "evt_existing");
        assertThat(repository.refundRecorded(paymentId)).isFalse();
        assertThat(refunds.status(paymentId)).isNull();
        assertThat(refunds.claimNext()).isNull();
        assertThat(refunds.claimReview(paymentId)).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT lease_until IS NULL AND version=0 FROM biz_refund",
                                Boolean.class))
                .isTrue();
    }

    @Test
    void softDeletedRefundCannotSatisfyANewObligation() {
        CheckoutPayment payment = prepare();
        jdbc.update(
                "UPDATE biz_payment SET status='succeeded',fulfilment_result='FULFILLED',"
                        + "fulfilled_deal_id=? WHERE id=?",
                TEAM,
                payment.id());
        jdbc.update(
                "INSERT INTO biz_refund(payment_id,intent_id,deal_id,amount,currency,reason,"
                        + "source_event,status,deleted) VALUES (?,?,?,?,?,?,?,'pending',true)",
                payment.id(),
                payment.quote().intentId(),
                TEAM,
                payment.quote().amount(),
                payment.quote().currency(),
                "TEAM_EXPIRED",
                "evt_deleted_refund");

        assertThatThrownBy(
                        () -> repository.requireRefund(payment, "TEAM_EXPIRED", "evt_replacement"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                repository.requireRefundsForExpiredTeam(
                                        TEAM, "TEAM_EXPIRED", "evt_replacement_team"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM biz_refund WHERE NOT deleted", Integer.class))
                .isZero();
    }

    @Test
    void concurrentExpiryRecordingCreatesOneObligationPerPaidMember() throws Exception {
        CheckoutPayment first = prepare();
        CheckoutPayment second = prepare();
        jdbc.update(
                "UPDATE biz_payment SET status='succeeded',fulfilment_result='FULFILLED',"
                        + "fulfilled_deal_id=? WHERE id IN (?,?)",
                TEAM,
                first.id(),
                second.id());
        jdbc.update("UPDATE stripe_test_team SET expired=true WHERE id=?", TEAM);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> expiryRefunds.recordExpiredTeam(TEAM));
            var two = executor.submit(() -> expiryRefunds.recordExpiredTeam(TEAM));
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        }

        assertThat(count("biz_refund")).isEqualTo(2);
    }

    @Test
    void signedHttpCallbackPreservesUtf8AndWhitespaceAndCommitsOnlyOnce() throws Exception {
        CheckoutPayment payment = prepare();
        paid(payment);
        MockMvc mvc = signedHttp();
        String body =
                signedEvent("evt_http_paid", "checkout.session.completed", payment.sessionId());
        String signature = sign(body);
        for (int retry = 0; retry < 2; retry++) {
            mvc.perform(
                            post(HTTP_PATH)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .header("Stripe-Signature", signature)
                                    .content(body.getBytes(StandardCharsets.UTF_8)))
                    .andExpect(status().isOk());
        }
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.SUCCEEDED);
        assertThat(count("log_payment_webhook_event")).isEqualTo(1);
        assertThat(count("stripe_test_orders")).isEqualTo(1);
    }

    @Test
    void signedHttpFailureRollsBackAndProviderRetryCanRecover() throws Exception {
        CheckoutPayment payment = prepare();
        paid(payment);
        MockMvc mvc = signedHttp();
        String body =
                signedEvent("evt_http_retry", "checkout.session.completed", payment.sessionId());
        failFulfilment.set(true);
        mvc.perform(
                        post(HTTP_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", sign(body))
                                .content(body))
                .andExpect(status().isInternalServerError());
        assertThat(count("log_payment_webhook_event")).isZero();
        assertThat(count("stripe_test_orders")).isZero();
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.CREATED);
        failFulfilment.set(false);
        mvc.perform(
                        post(HTTP_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", sign(body))
                                .content(body))
                .andExpect(status().isOk());
        assertThat(count("stripe_test_orders")).isEqualTo(1);
    }

    @Test
    void httpRejectsTamperedOrMissingSignatureButAcknowledgesSignedUnknownType() throws Exception {
        MockMvc mvc = signedHttp();
        String body = signedEvent("evt_http_unknown", "customer.created", "unused");
        mvc.perform(
                        post(HTTP_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", sign(body))
                                .content(body + " "))
                .andExpect(status().isBadRequest());
        mvc.perform(post(HTTP_PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(HTTP_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", sign(body))
                                .content(body))
                .andExpect(status().isOk());
        assertThat(count("log_payment_webhook_event")).isZero();
        assertThat(count("biz_payment")).isZero();
        verify(gateway, never()).retrieveCheckoutSession(any());
    }

    // Real MVC/controller/facade/core/SQL and SDK verification; remote retrieval and business
    // collaborators remain test doubles. This standalone harness does not exercise deployment
    // filters.
    private MockMvc signedHttp() {
        var verifier =
                new StripeCheckoutGateway(
                        new StripeProperties("", SIGNING_FIXTURE),
                        new StripeClientProvider(new StripeProperties("", SIGNING_FIXTURE)));
        when(gateway.verifyCheckoutWebhook(any(), any()))
                .thenAnswer(
                        call ->
                                verifier.verifyCheckoutWebhook(
                                        call.getArgument(0), call.getArgument(1)));
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("checkout", service);
        var facade =
                new StripePaymentAppService(beans.getBeanProvider(StripeCheckoutService.class));
        return MockMvcBuilders.standaloneSetup(new StripeWebhookController(facade))
                .setControllerAdvice(
                        new com.blocbuy.payment.web.StripeRequestExceptionHandler(),
                        new GlobalExceptionHandler())
                .build();
    }

    private static String signedEvent(String id, String type, String sessionId) {
        return "{\n  \"id\":\""
                + id
                + "\",\"object\":\"event\",\"api_version\":\""
                + Stripe.API_VERSION
                + "\",\"livemode\":false,\"type\":\""
                + type
                + "\",\"description\":\"本地付款验证\",\"data\":{\"object\":{\"id\":\""
                + sessionId
                + "\",\"object\":\"checkout.session\"}}\n}\n";
    }

    private static String sign(String body) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGNING_FIXTURE.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t="
                + timestamp
                + ",v1="
                + HexFormat.of()
                        .formatHex(
                                mac.doFinal(
                                        (timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void downstreamFailureRollsBackReceiptAndPaymentThenRetryWorks() {
        CheckoutPayment payment = prepare();
        paid(payment);
        failFulfilment.set(true);
        assertThatThrownBy(() -> webhook(payment, "evt_rollback"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("log_payment_webhook_event")).isZero();
        assertThat(count("stripe_test_orders")).isZero();
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.CREATED);
        failFulfilment.set(false);
        webhook(payment, "evt_rollback");
        assertThat(count("stripe_test_orders")).isEqualTo(1);
    }

    @Test
    void mismatchGoesToReviewWithoutFulfilment() {
        CheckoutPayment payment = prepare();
        var valid = session(payment, true);
        sessions.put(
                valid.id(),
                new CheckoutGateway.Session(
                        valid.id(),
                        valid.paymentId(),
                        valid.intentId(),
                        valid.paymentIntentId(),
                        1,
                        "sgd",
                        "complete",
                        "paid",
                        false,
                        null,
                        valid.expiresAt()));
        webhook(payment, "evt_mismatch");
        assertThat(count("stripe_test_orders")).isZero();
        assertThat(count("log_payment_review")).isEqualTo(1);
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.CREATED);
    }

    @Test
    void completedButUnpaidDoesNotFulfil() {
        CheckoutPayment payment = prepare();
        var valid = session(payment, false);
        sessions.put(
                valid.id(),
                new CheckoutGateway.Session(
                        valid.id(),
                        valid.paymentId(),
                        valid.intentId(),
                        "pi_pending",
                        1000,
                        "sgd",
                        "complete",
                        "unpaid",
                        false,
                        null,
                        valid.expiresAt()));
        webhook(payment, "evt_unpaid");
        assertThat(count("stripe_test_orders")).isZero();
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.CREATED);
    }

    @Test
    void expiredBusinessStillRecordsPaidFactAndCompensation() {
        CheckoutPayment payment = prepare();
        jdbc.update("UPDATE stripe_test_team SET expired=true WHERE id=?", TEAM);
        paid(payment);
        webhook(payment, "evt_late");
        assertThat(count("stripe_test_orders")).isZero();
        assertThat(count("biz_refund")).isEqualTo(1);
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.SUCCEEDED);
    }

    @Test
    void concurrentPaidBuyersCannotBothTakeLastSeat() throws Exception {
        CheckoutPayment first = prepare();
        CheckoutPayment second = prepare();
        paid(first);
        paid(second);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> service.recover(first.id(), "test", true));
            var b = executor.submit(() -> service.recover(second.id(), "test", true));
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        }
        assertThat(count("stripe_test_orders")).isEqualTo(1);
        assertThat(count("biz_refund")).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_payment WHERE status='succeeded'",
                                Integer.class))
                .isEqualTo(2);
    }

    @Test
    void recoveryWorksWithoutAnyWebhookAndCanBeRepeated() {
        CheckoutPayment payment = prepare();
        paid(payment);
        assertThat(service.preview(payment.id()).proposedAction())
                .isEqualTo("VERIFY_FULFILMENT_OR_RECORD_COMPENSATION");
        assertThat(count("log_payment_review")).isZero();
        assertThat(count("stripe_test_orders")).isZero();
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.CREATED);
        var exceptionInput1 = payment.id();
        assertThatThrownBy(() -> service.recover(exceptionInput1, "test", false))
                .isInstanceOf(IllegalArgumentException.class);
        service.recover(payment.id(), "test", true);
        service.recover(payment.id(), "test", true);
        assertThat(count("stripe_test_orders")).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM log_payment_review WHERE reason='RECOVERY_FULFILLED'",
                                Integer.class))
                .isEqualTo(2);
        assertThat(service.reviewCases(null)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM biz_payment_review_case", String.class))
                .isEqualTo("resolved");
    }

    @Test
    void openPaymentRemainsOwnedObservationUntilVerifiedTerminalUnpaid() {
        CheckoutPayment payment = prepare();
        service.assignReview(payment.id(), "yorik", Instant.now().plusSeconds(1800));
        service.recover(payment.id(), "yorik", true);
        var openCase = service.reviewCases(null).getFirst();
        assertThat(openCase.paymentId()).isEqualTo(payment.id());
        assertThat(openCase.owner()).isEqualTo("yorik");
        assertThat(openCase.status()).isEqualTo("open");
        assertThat(openCase.lastCheckedAt()).isNotNull();
        assertThat(openCase.nextCheckAt()).isAfter(openCase.lastCheckedAt());
        assertThat(openCase.providerSummary()).contains("providerPaid=false");
        assertThat(service.reviewCases(openCase.caseId())).isEmpty();
        assertThat(repository.findBlockingAttempt(payment.quote().intentId())).isPresent();

        sessions.put(payment.sessionId(), expired(session(payment, false)));
        service.recover(payment.id(), "yorik", true);
        assertThat(service.reviewCases(null)).isEmpty();
        assertThat(repository.findBlockingAttempt(payment.quote().intentId())).isEmpty();
        assertThat(count("stripe_test_orders")).isZero();
        assertThat(count("log_payment_review")).isGreaterThan(0);
    }

    @Test
    void failedRecoveryKeepsAnOwnedCaseAndCanBeRetriedAfterAutomaticDeliveryStops() {
        CheckoutPayment payment = prepare();
        paid(payment);
        failFulfilment.set(true);
        var exceptionInput2 = payment.id();
        assertThatThrownBy(() -> service.recover(exceptionInput2, "yorik", true))
                .isInstanceOf(IllegalStateException.class);
        var review = service.reviewCases(null).getFirst();
        assertThat(review.reason()).isEqualTo("RECOVERY_FAILED");
        assertThat(review.owner()).isEqualTo("yorik");
        assertThat(count("log_payment_webhook_event")).isZero();
        assertThat(count("stripe_test_orders")).isZero();
        failFulfilment.set(false);
        service.recover(payment.id(), "yorik", true);
        assertThat(service.reviewCases(null)).isEmpty();
        assertThat(count("stripe_test_orders")).isEqualTo(1);
    }

    @Test
    void paidFlagWithoutActualDownstreamEvidenceCannotCloseReview() {
        CheckoutPayment payment = prepare();
        paid(payment);
        webhook(payment, "evt_before_corruption");
        // Corrupt only the disposable fixture to model a missing downstream row during inspection.
        jdbc.update("DELETE FROM stripe_test_orders WHERE payment_id=?", payment.id());
        service.recover(payment.id(), "yorik", true);
        var review = service.reviewCases(null).getFirst();
        assertThat(review.status()).isEqualTo("open");
        assertThat(review.providerSummary()).contains("DOWNSTREAM_MISSING");
        assertThat(repository.outcome(payment.id())).isEqualTo("FULFILLED");
        assertThat(count("stripe_test_orders")).isZero();
    }

    @Test
    void concurrentManualRecoveriesShareOneCaseAndOneBusinessResult() throws Exception {
        CheckoutPayment payment = prepare();
        paid(payment);
        var fetched = new java.util.concurrent.CountDownLatch(2);
        when(gateway.retrieveCheckoutSession(payment.sessionId()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            fetched.countDown();
                            assertThat(fetched.await(5, TimeUnit.SECONDS)).isTrue();
                            return sessions.get(payment.sessionId());
                        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.recover(payment.id(), "yorik", true));
            var second = executor.submit(() -> service.recover(payment.id(), "backup", true));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(count("stripe_test_orders")).isEqualTo(1);
        assertThat(count("biz_payment_review_case")).isEqualTo(1);
        assertThat(service.reviewCases(null)).isEmpty();
    }

    @Test
    void missingRefundObligationCannotBeHiddenByThePaymentOutcomeFlag() {
        CheckoutPayment payment = prepare();
        jdbc.update("UPDATE stripe_test_team SET expired=true WHERE id=?", TEAM);
        paid(payment);
        webhook(payment, "evt_refund_fixture");
        assertThat(repository.outcome(payment.id())).isEqualTo("REFUND_REQUIRED");
        // Disposable test data only: model an obligation missing despite its result flag.
        jdbc.update("DELETE FROM biz_refund WHERE payment_id=?", payment.id());
        service.recover(payment.id(), "yorik", true);
        assertThat(service.reviewCases(null).getFirst().providerSummary())
                .contains("DOWNSTREAM_MISSING");
        assertThat(count("biz_refund")).isZero();
    }

    @Test
    void readOnlyScanIncludesCanceledPaymentsWithAnOpenReview() {
        CheckoutPayment payment = prepare();
        sessions.put(payment.sessionId(), expired(session(payment, false)));
        webhook(payment, "evt_cancel_for_scan");
        var exceptionInput3 = payment.id();
        var exceptionInput4 = Instant.now().minusSeconds(1);
        assertThatThrownBy(() -> service.assignReview(exceptionInput3, "yorik", exceptionInput4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count("biz_payment_review_case")).isZero();
        service.assignReview(payment.id(), "yorik", Instant.now().plusSeconds(900));
        int auditRows = count("log_payment_review");
        assertThat(service.scan()).extracting(CheckoutPayment::id).containsExactly(payment.id());
        assertThat(service.reviewCases(null)).hasSize(1);
        assertThat(count("log_payment_review")).isEqualTo(auditRows);
    }

    @Test
    void canceledThenPaidRequiresAuditedRecoveryAndPreservesCompensation() {
        CheckoutPayment payment = prepare();
        var open = session(payment, false);
        sessions.put(open.id(), expired(open));
        webhook(payment, "evt_expired");
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.CANCELED);
        paid(payment);
        webhook(payment, "evt_late_paid");
        assertThat(count("log_payment_review")).isEqualTo(1);
        assertThat(count("stripe_test_orders")).isZero();
        var exceptionInput5 = payment.quote().userId();
        var exceptionInput6 = payment.quote().intentId();
        assertThatThrownBy(() -> service.prepare(exceptionInput5, exceptionInput6, "new-key"))
                .isInstanceOf(RuntimeException.class);
        jdbc.update("UPDATE stripe_test_team SET expired=true WHERE id=?", TEAM);
        service.recover(payment.id(), "test", true);
        service.recover(payment.id(), "test", true);
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.SUCCEEDED);
        assertThat(count("biz_refund")).isEqualTo(1);
    }

    @Test
    void cleanupExpiresOpenSessionOutsideTransaction() {
        CheckoutPayment payment = prepare();
        // Simulate the frozen business deadline passing without a wall-clock sleep.
        jdbc.update(
                "UPDATE biz_payment SET business_deadline=clock_timestamp()-interval '1 second' WHERE id=?",
                payment.id());
        when(gateway.expireCheckoutSession(payment.sessionId()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            var closed = expired(sessions.get(payment.sessionId()));
                            sessions.put(payment.sessionId(), closed);
                            return closed;
                        });
        assertThat(service.expireDueSessions()).isEqualTo(1);
        assertThat(service.expireDueSessions()).isZero();
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.CANCELED);
        assertThat(count("stripe_test_orders")).isZero();
    }

    @Test
    void simultaneousDeliveryOfSameEventCommitsOneReceipt() throws Exception {
        CheckoutPayment payment = prepare();
        paid(payment);
        when(gateway.verifyCheckoutWebhook("body", "signature"))
                .thenReturn(
                        Optional.of(
                                new CheckoutGateway.Event(
                                        "evt_simultaneous",
                                        "checkout.session.completed",
                                        payment.sessionId(),
                                        false,
                                        null)));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.handleWebhook("body", "signature"));
            var second = executor.submit(() -> service.handleWebhook("body", "signature"));
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        assertThat(count("log_payment_webhook_event")).isEqualTo(1);
        assertThat(count("stripe_test_orders")).isEqualTo(1);
    }

    private CheckoutGateway.Session expired(CheckoutGateway.Session open) {
        return new CheckoutGateway.Session(
                open.id(),
                open.paymentId(),
                open.intentId(),
                null,
                open.amountMinor(),
                open.currency(),
                "expired",
                "unpaid",
                false,
                null,
                open.expiresAt());
    }

    @Test
    void foreignOwnerAndChangedRequestIntentAreRejected() {
        CheckoutPayment payment = prepare();
        var exceptionInput7 = UUID.randomUUID();
        var exceptionInput8 = payment.quote().intentId();
        assertThatThrownBy(() -> service.prepare(exceptionInput7, exceptionInput8, "test-key"))
                .isInstanceOf(RuntimeException.class);
        var quote = quote(payment.quote().userId());
        quotes.put(quote.intentId(), quote);
        var exceptionInput9 = quote.userId();
        var exceptionInput10 = quote.intentId();
        assertThatThrownBy(() -> service.prepare(exceptionInput9, exceptionInput10, "test-key"))
                .isInstanceOf(RuntimeException.class);
        assertThat(count("biz_payment")).isEqualTo(1);
    }

    @Test
    void creationTimeoutRetainsOneAttemptAndStableProviderKey() {
        var quote = quote(UUID.randomUUID());
        quotes.put(quote.intentId(), quote);
        doThrow(new IllegalStateException("simulated timeout"))
                .when(gateway)
                .createCheckoutSession(any());
        var exceptionInput11 = quote.userId();
        var exceptionInput12 = quote.intentId();
        assertThatThrownBy(() -> service.prepare(exceptionInput11, exceptionInput12, "test-key"))
                .isInstanceOf(IllegalStateException.class);
        CheckoutPayment saved = repository.findRequest(quote.userId(), "test-key").orElseThrow();
        doAnswer(
                        call -> {
                            CheckoutPayment retried = call.getArgument(0);
                            assertThat(retried.providerKey()).isEqualTo(saved.providerKey());
                            assertThat(retried.sessionExpiresAt())
                                    .isEqualTo(saved.sessionExpiresAt());
                            return session(retried, false);
                        })
                .when(gateway)
                .createCheckoutSession(any());
        service.prepare(quote.userId(), quote.intentId(), "test-key");
        assertThat(count("biz_payment")).isEqualTo(1);
    }

    @Test
    void lostCreationResponseCanRecoverProviderVerifiedPaidSessionWithoutCreatingAnother() {
        CheckoutPayment payment = unknownCreation(true);
        String candidate = "cs_test_" + payment.id();
        assertThat(service.preview(payment.id(), candidate).proposedAction())
                .isEqualTo("VERIFY_FULFILMENT_OR_RECORD_COMPENSATION");
        assertThat(repository.findById(payment.id()).orElseThrow().sessionId()).isNull();
        assertThat(count("log_payment_review")).isZero();
        service.recover(payment.id(), "yorik", true, candidate);
        service.recover(payment.id(), "yorik", true, candidate);
        CheckoutPayment recovered = repository.findById(payment.id()).orElseThrow();
        assertThat(recovered.sessionId()).isEqualTo(candidate);
        assertThat(recovered.status()).isEqualTo(CheckoutPayment.Status.SUCCEEDED);
        assertThat(count("stripe_test_orders")).isEqualTo(1);
        assertThat(service.reviewCases(null)).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT provider_summary FROM biz_payment_review_case",
                                String.class))
                .contains(candidate);
        verify(gateway).createCheckoutSession(any());
    }

    @Test
    void lostOpenSessionCanBeReassociatedButStaysUnderObservation() {
        CheckoutPayment payment = unknownCreation(false);
        String candidate = "cs_test_" + payment.id();
        service.recover(payment.id(), "yorik", true, candidate);
        assertThat(repository.findById(payment.id()).orElseThrow().sessionId())
                .isEqualTo(candidate);
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.CREATED);
        assertThat(service.reviewCases(null)).hasSize(1);
        assertThat(
                        service.prepare(
                                        payment.quote().userId(),
                                        payment.quote().intentId(),
                                        "test-key")
                                .checkoutUrl())
                .isEqualTo(sessions.get(candidate).url());
        assertThat(count("stripe_test_orders")).isZero();
        verify(gateway).createCheckoutSession(any());
    }

    @Test
    void operatorHintCannotAttachAProviderSessionBelongingToAnotherPayment() {
        CheckoutPayment payment = unknownCreation(true);
        var foreign =
                session(
                        new CheckoutPayment(
                                UUID.randomUUID(),
                                payment.quote(),
                                "other-key",
                                payment.createdAt(),
                                payment.sessionExpiresAt(),
                                payment.successUrl(),
                                payment.cancelUrl(),
                                null,
                                null,
                                null,
                                CheckoutPayment.Status.CREATED,
                                0),
                        true);
        sessions.put(foreign.id(), foreign);
        assertThat(service.preview(payment.id(), foreign.id()).proposedAction())
                .isEqualTo("REVIEW_PROVIDER_MISMATCH");
        var exceptionInput13 = payment.id();
        var foreignSessionId = foreign.id();
        assertThatThrownBy(() -> service.recover(exceptionInput13, "yorik", true, foreignSessionId))
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.findById(payment.id()).orElseThrow().sessionId()).isNull();
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.CREATED);
        assertThat(service.reviewCases(null).getFirst().providerSummary())
                .contains("lookupHint=" + foreign.id());
        assertThat(count("stripe_test_orders")).isZero();
    }

    @Test
    void operatorHintCannotOverwriteAnExistingSessionOrUseAnArbitraryUrl() {
        CheckoutPayment payment = prepare();
        var exceptionInput14 = payment.id();
        assertThatThrownBy(
                        () -> service.recover(exceptionInput14, "yorik", true, "cs_test_different"))
                .isInstanceOf(RuntimeException.class);
        var exceptionInput15 = payment.id();
        assertThatThrownBy(() -> service.preview(exceptionInput15, "http://127.0.0.1/private"))
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.findById(payment.id()).orElseThrow().sessionId())
                .isEqualTo(payment.sessionId());
        verify(gateway, never()).retrieveCheckoutSession("cs_test_different");
        verify(gateway, never()).retrieveCheckoutSession("http://127.0.0.1/private");
    }

    @Test
    void candidateSessionWithWrongAmountIsNotBoundEvenWhenPaymentIdMatches() {
        CheckoutPayment payment = unknownCreation(true);
        var original = sessions.get("cs_test_" + payment.id());
        var mismatch =
                new CheckoutGateway.Session(
                        original.id(),
                        original.paymentId(),
                        original.intentId(),
                        original.paymentIntentId(),
                        1,
                        original.currency(),
                        original.status(),
                        original.paymentStatus(),
                        original.liveMode(),
                        original.url(),
                        original.expiresAt());
        sessions.put(mismatch.id(), mismatch);
        service.recover(payment.id(), "yorik", true, mismatch.id());
        assertThat(repository.findById(payment.id()).orElseThrow().sessionId()).isNull();
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.CREATED);
        assertThat(service.reviewCases(null).getFirst().providerSummary())
                .contains(mismatch.id(), "savedSnapshotMatches=false");
        assertThat(count("stripe_test_orders")).isZero();
    }

    private CheckoutPayment unknownCreation(boolean paid) {
        var quote = quote(UUID.randomUUID());
        quotes.put(quote.intentId(), quote);
        doAnswer(
                        call -> {
                            CheckoutPayment payment = call.getArgument(0);
                            var remote = session(payment, paid);
                            sessions.put(remote.id(), remote);
                            throw new IllegalStateException(
                                    "Provider created session but response was lost");
                        })
                .when(gateway)
                .createCheckoutSession(any());
        var exceptionInput16 = quote.userId();
        var exceptionInput17 = quote.intentId();
        assertThatThrownBy(() -> service.prepare(exceptionInput16, exceptionInput17, "test-key"))
                .isInstanceOf(IllegalStateException.class);
        CheckoutPayment saved = repository.findRequest(quote.userId(), "test-key").orElseThrow();
        assertThat(saved.sessionId()).isNull();
        return saved;
    }

    @Test
    void providerOperationsRejectAnEnclosingDatabaseTransaction() {
        var exceptionInput18 = UUID.randomUUID();
        var exceptionInput19 = UUID.randomUUID();
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(
                status ->
                        assertThatThrownBy(
                                        () ->
                                                service.prepare(
                                                        exceptionInput18,
                                                        exceptionInput19,
                                                        "test-key"))
                                .isInstanceOf(IllegalStateException.class));
    }

    private CheckoutPayment prepare() {
        var quote = quote(UUID.randomUUID());
        quotes.put(quote.intentId(), quote);
        var result = service.prepare(quote.userId(), quote.intentId(), "test-key");
        return repository.findById(result.paymentId()).orElseThrow();
    }

    /**
     * Both production paths mint quotes with {@code quoteValidUntil <= businessDeadline}: a JOIN
     * takes the earlier of now+2400s and the team's deadline, a START gives the team at least 2400
     * seconds of life. This fixture had the two the wrong way round, which let a checkout session
     * be issued that outlived the team it was for (#138). Keep the real ordering here.
     */
    private CheckoutQuote quote(UUID user) {
        Instant now = repository.now().truncatedTo(ChronoUnit.MICROS);
        return new CheckoutQuote(
                UUID.randomUUID(),
                user,
                "JOIN",
                TEAM,
                new BigDecimal("10.00"),
                "sgd",
                now.plusSeconds(2400),
                now.plusSeconds(3600));
    }

    private CheckoutGateway.Session session(CheckoutPayment payment, boolean paid) {
        return new CheckoutGateway.Session(
                "cs_test_" + payment.id(),
                payment.id().toString(),
                payment.quote().intentId().toString(),
                paid ? "pi_" + payment.id() : null,
                1000,
                "sgd",
                paid ? "complete" : "open",
                paid ? "paid" : "unpaid",
                false,
                paid ? null : "https://checkout.stripe.com/test/" + payment.id(),
                payment.sessionExpiresAt());
    }

    private void paid(CheckoutPayment payment) {
        sessions.put(payment.sessionId(), session(payment, true));
    }

    private void webhook(CheckoutPayment payment, String id) {
        when(gateway.verifyCheckoutWebhook("body", "signature"))
                .thenReturn(
                        Optional.of(
                                new CheckoutGateway.Event(
                                        id,
                                        "checkout.session.completed",
                                        payment.sessionId(),
                                        false,
                                        null)));
        service.handleWebhook("body", "signature");
    }

    private int count(String table) {
        // Test-owned, constant table names only; never used for application SQL.
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private class TestBusiness implements PaymentBusinessPort {
        @Override
        public CheckoutQuote lockQuote(UUID user, UUID intentId) {
            lock();
            var quote = quotes.get(intentId);
            if (quote == null || !quote.userId().equals(user)) {
                throw new IllegalArgumentException("Owner");
            }
            return quote;
        }

        @Override
        public void lockForSettlement(CheckoutQuote quote) {
            lock();
        }

        private void lock() {
            jdbc.queryForObject(
                    "SELECT seats FROM stripe_test_team WHERE id=? FOR UPDATE",
                    Integer.class,
                    TEAM);
        }

        @Override
        public Outcome fulfil(PaymentSucceeded event) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            if (Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT expired FROM stripe_test_team WHERE id=?",
                            Boolean.class,
                            TEAM))) {
                return new Outcome(null, null, "TEAM_EXPIRED");
            }
            int accepted =
                    jdbc.update(
                            "UPDATE stripe_test_team SET seats=seats-1 WHERE id=? AND seats>0",
                            TEAM);
            if (accepted == 0) {
                return new Outcome(null, null, "TEAM_FULL");
            }
            UUID order = UUID.randomUUID();
            jdbc.update("INSERT INTO stripe_test_orders VALUES (?,?)", event.paymentId(), order);
            if (failFulfilment.get()) {
                throw new IllegalStateException("Simulated downstream outage");
            }
            return new Outcome(TEAM, order, null);
        }

        @Override
        public void lockExpiredTeam(UUID id) {
            lock();
            if (!Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT expired FROM stripe_test_team WHERE id=?",
                            Boolean.class,
                            id))) {
                throw new IllegalStateException("Not expired");
            }
        }

        @Override
        public FulfilmentEvidence fulfilmentEvidence(PaymentSucceeded event) {
            return Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT EXISTS(SELECT 1 FROM stripe_test_orders WHERE payment_id=?)",
                                    Boolean.class,
                                    event.paymentId()))
                    ? FulfilmentEvidence.COMPLETE
                    : FulfilmentEvidence.NONE;
        }
    }
}
