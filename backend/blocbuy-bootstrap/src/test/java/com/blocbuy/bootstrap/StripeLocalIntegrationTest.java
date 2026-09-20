package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.bootstrap.fixture.PaymentFixtureConfiguration;
import com.blocbuy.bootstrap.fixture.PaymentLifecycleFixture;
import com.blocbuy.bootstrap.fixture.StripeTestDatabase;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.service.PaymentBrowserService;
import com.blocbuy.payment.service.StripeCheckoutService;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Actual group service and PostgreSQL, test-only order/intent fixture, mocked Stripe network. */
@SpringBootTest(
        properties = {
            "server.address=127.0.0.1",
            "spring.flyway.locations=classpath:db/migration,classpath:db/stripe,classpath:db/stripe-fixture",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1",
            "blocbuy.payment.checkout.success-url=https://shop.example/payments/return",
            "blocbuy.payment.checkout.cancel-url=https://shop.example/payments/return"
        })
@ActiveProfiles({"local", "stripe-sprint2"})
@Import(PaymentFixtureConfiguration.class)
@AutoConfigureMockMvc
@DirtiesContext
class StripeLocalIntegrationTest {
    // Testcontainers reaps this container after the Spring test JVM exits.
    private static final StripeTestDatabase DATABASE =
            new StripeTestDatabase("blocbuy_stripe_local");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::url);
        properties.add("spring.datasource.username", DATABASE::username);
        properties.add("spring.datasource.password", DATABASE::password);
    }

    @Autowired private PaymentLifecycleFixture coordinator;
    @Autowired private StripeCheckoutService payments;
    @Autowired private PaymentBrowserService browser;
    @Autowired private CheckoutPaymentRepository repository;
    @Autowired private com.blocbuy.payment.repository.PaymentRepository devPayments;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private java.util.List<RequestMappingHandlerMapping> mappings;
    @Autowired private com.blocbuy.payment.repository.RefundRepository refunds;
    @Autowired private com.blocbuy.order.service.OrderHistoryService orders;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean private CheckoutGateway gateway;
    private final Map<String, CheckoutGateway.Session> sessions = new ConcurrentHashMap<>();
    private UUID product;

    @BeforeEach
    void setup() {
        jdbc.execute(
                "TRUNCATE log_refund_attempt,biz_payment,log_payment_webhook_event,biz_refund,log_payment_review,biz_payment_review_case,stripe_local_intent,stripe_local_expiry_request,biz_order,biz_group_member,biz_group_deal,biz_group_participation_receipt,stripe_local_product_terms,biz_team_price_policy,biz_product,sys_user");
        product = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO biz_product(id,product_no,title,original_price,currency,stock,status) VALUES(?,?,'Local test product',20,'sgd',100,'on_sale')",
                product,
                product.toString());
        UUID admin = buyer();
        jdbc.update(
                "INSERT INTO biz_team_price_policy(product_id,amount,currency,valid_from,valid_until,create_by,update_by,version) VALUES(?,10,'sgd',clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 day',?,?,1)",
                product,
                admin,
                admin);
        jdbc.update("INSERT INTO stripe_local_product_terms VALUES(?,2,7200)", product);
        when(gateway.createCheckoutSession(any()))
                .thenAnswer(
                        call -> {
                            CheckoutPayment p = call.getArgument(0);
                            var session = session(p, "open", "unpaid");
                            sessions.put(session.id(), session);
                            return session;
                        });
        when(gateway.retrieveCheckoutSession(any()))
                .thenAnswer(call -> sessions.get(call.getArgument(0)));
        when(gateway.expireCheckoutSession(any()))
                .thenAnswer(
                        call -> {
                            String id = call.getArgument(0);
                            var previous = sessions.get(id);
                            CheckoutPayment p =
                                    repository
                                            .findById(UUID.fromString(previous.paymentId()))
                                            .orElseThrow();
                            var expired = session(p, "expired", "unpaid");
                            sessions.put(id, expired);
                            return expired;
                        });
    }

    @Test
    void createsRealTeamMemberAndOrderOnlyAfterPaymentAndReplaysOnce() {
        CheckoutPayment p = prepare(buyer(), "START", product);
        assertThat(p.successUrl()).endsWith("?paymentId=" + p.id());
        assertThat(p.cancelUrl()).endsWith("?paymentId=" + p.id());
        assertThat(count("biz_group_deal")).isZero();
        paid(p);
        payments.recover(p.id(), "test-operator", true);
        payments.recover(p.id(), "test-operator", true);
        assertThat(count("biz_group_deal")).isEqualTo(1);
        assertThat(count("biz_group_member")).isEqualTo(1);
        assertThat(count("biz_order")).isEqualTo(1);
        var receipt = browser.receipt(p.quote().userId(), p.id());
        assertThat(devPayments.findById(p.id().toString()).orElseThrow().id())
                .isEqualTo(p.id().toString());
        assertThat(receipt.status()).isEqualTo("SUCCEEDED");
        assertThat(receipt.orderId())
                .isEqualTo(jdbc.queryForObject("SELECT id FROM biz_order", UUID.class));
        assertThat(receipt.dealId()).isEqualTo(deal());
        assertThat(receipt.dealId()).isNotEqualTo(product);
        assertThat(receipt.refundPending()).isFalse();
        var exceptionInput1 = UUID.randomUUID();
        var exceptionInput2 = p.id();
        assertThatThrownBy(() -> browser.receipt(exceptionInput1, exceptionInput2))
                .isInstanceOf(com.blocbuy.common.exception.BizException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT m.order_id=o.id FROM biz_group_member m JOIN biz_order o ON m.payment_id=o.payment_id",
                                Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM biz_payment_review_case", String.class))
                .isEqualTo("RESOLVED");
    }

    @Test
    void twentyBuyersRaceForLastSeatAndNineteenRefundObligationsPersist() throws Exception {
        CheckoutPayment leader = prepare(buyer(), "START", product);
        paid(leader);
        payments.recover(leader.id(), "test-operator", true);
        UUID deal = deal();
        var attempts = new ArrayList<CheckoutPayment>();
        for (int i = 0; i < 20; i++) {
            CheckoutPayment p = prepare(buyer(), "JOIN", deal);
            paid(p);
            attempts.add(p);
        }
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(20)) {
            var results =
                    attempts.stream()
                            .map(
                                    p ->
                                            executor.submit(
                                                    () -> {
                                                        start.await();
                                                        payments.recover(
                                                                p.id(), "test-operator", true);
                                                        return true;
                                                    }))
                            .toList();
            start.countDown();
            for (var result : results) {
                assertThat(result.get(40, TimeUnit.SECONDS)).isTrue();
            }
        }
        assertThat(count("biz_group_member")).isEqualTo(2);
        assertThat(count("biz_order")).isEqualTo(2);
        assertThat(count("biz_refund")).isEqualTo(19);
        assertThat(jdbc.queryForObject("SELECT current_size FROM biz_group_deal", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT stock FROM biz_product", Integer.class))
                .isEqualTo(98);
    }

    @Test
    void teamExpiryAndLatePaymentEachProduceExactlyOnePendingRefund() {
        CheckoutPayment leader = prepare(buyer(), "START", product);
        paid(leader);
        payments.recover(leader.id(), "test-operator", true);
        UUID deal = deal();
        CheckoutPayment late = prepare(buyer(), "JOIN", deal);
        jdbc.update(
                "UPDATE biz_group_deal SET expire_time=clock_timestamp()-interval '1 second' WHERE id=?",
                deal);
        coordinator.expireTeam(deal);
        coordinator.expireTeam(deal);
        paid(late);
        payments.recover(late.id(), "test-operator", true);
        payments.recover(late.id(), "test-operator", true);
        assertThat(count("biz_refund")).isEqualTo(2);
        assertThat(count("biz_order")).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_refund WHERE status='PENDING'",
                                Long.class))
                .isEqualTo(2);
    }

    @Test
    void technicalOrderFailureRollsBackPaymentGroupInventoryAndCanRecover() {
        CheckoutPayment p = prepare(buyer(), "START", product);
        paid(p);
        jdbc.execute("ALTER TABLE biz_order ADD CONSTRAINT local_fail_order CHECK (quantity=999)");
        try {
            var exceptionInput3 = p.id();
            assertThatThrownBy(() -> payments.recover(exceptionInput3, "test-operator", true))
                    .isInstanceOf(RuntimeException.class);
            assertThat(repository.findById(p.id()).orElseThrow().status())
                    .isEqualTo(CheckoutPayment.Status.CREATED);
            assertThat(count("biz_group_member")).isZero();
            assertThat(count("biz_group_deal")).isZero();
        } finally {
            jdbc.execute("ALTER TABLE biz_order DROP CONSTRAINT local_fail_order");
        }
        payments.recover(p.id(), "test-operator", true);
        assertThat(count("biz_order")).isEqualTo(1);
    }

    @Test
    void testBusinessFixturesNeverExposeLocalHttpEndpoints() {
        assertThat(
                        mappings.stream()
                                .flatMap(handler -> handler.getHandlerMethods().keySet().stream()))
                .isNotEmpty()
                .allSatisfy(
                        mapping ->
                                assertThat(mapping.getPatternValues())
                                        .noneMatch(path -> path.startsWith("/local/stripe")));
    }

    @Test
    void durableCleanupClosesUnpaidSessionAfterGroupExpires() {
        CheckoutPayment leader = prepare(buyer(), "START", product);
        paid(leader);
        payments.recover(leader.id(), "test-operator", true);
        CheckoutPayment pending = prepare(buyer(), "JOIN", deal());
        jdbc.update("UPDATE biz_group_deal SET expire_time=clock_timestamp()-interval '1 second'");
        coordinator.sweep();
        assertThat(repository.findById(pending.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.CANCELED);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT completed_at IS NOT NULL FROM stripe_local_expiry_request",
                                Boolean.class))
                .isTrue();
        assertThat(count("biz_refund")).isEqualTo(1);
    }

    private UUID buyer() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO sys_user(id,register_source,status) VALUES(?,'local','active')", id);
        return id;
    }

    @Test
    void expiredSessionWithCanceledProviderIntentFailsAndAllowsAnotherAttempt() {
        CheckoutPayment p = prepare(buyer(), "START", product);
        sessions.put(
                p.sessionId(),
                new CheckoutGateway.Session(
                        p.sessionId(),
                        p.id().toString(),
                        p.quote().intentId().toString(),
                        "pi_" + p.id(),
                        1000,
                        "sgd",
                        "expired",
                        "unpaid",
                        false,
                        null,
                        p.sessionExpiresAt(),
                        "canceled"));
        payments.recover(p.id(), "test-operator", true);
        assertThat(repository.findById(p.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.FAILED);
        assertThat(count("biz_order")).isZero();
        assertThat(count("biz_refund")).isZero();
        var retry = payments.prepare(p.quote().userId(), p.quote().intentId(), "new-attempt");
        assertThat(retry.paymentId()).isNotEqualTo(p.id());
    }

    @Test
    void declinedCardOnOpenSessionIsNotTerminalFailure() {
        CheckoutPayment p = prepare(buyer(), "START", product);
        sessions.put(
                p.sessionId(),
                new CheckoutGateway.Session(
                        p.sessionId(),
                        p.id().toString(),
                        p.quote().intentId().toString(),
                        "pi_" + p.id(),
                        1000,
                        "sgd",
                        "open",
                        "unpaid",
                        false,
                        null,
                        p.sessionExpiresAt(),
                        "requires_payment_method"));
        payments.recover(p.id(), "test-operator", true);
        assertThat(repository.findById(p.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.CREATED);
        var exceptionInput4 = p.quote().userId();
        var exceptionInput5 = p.quote().intentId();
        assertThatThrownBy(() -> payments.prepare(exceptionInput4, exceptionInput5, "new-attempt"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void refundExecutesOutsideTransactionAndOrderHistoryShowsRefunded() {
        CheckoutPayment p = expiredPurchase();
        var provider = mock(com.blocbuy.payment.gateway.RefundGateway.class);
        when(provider.createRefund(any()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            org.springframework.transaction.support
                                                    .TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            com.blocbuy.payment.model.RefundWork w = call.getArgument(0);
                            return new com.blocbuy.payment.gateway.RefundGateway.Snapshot(
                                    "re_test",
                                    w.providerIntentId(),
                                    w.id().toString(),
                                    1000,
                                    "sgd",
                                    "succeeded");
                        });
        var worker = new com.blocbuy.payment.service.RefundService(refunds, provider, transactions);
        assertThat(worker.processNext()).isTrue();
        assertThat(worker.processNext()).isFalse();
        verify(provider, times(1)).createRefund(any());
        var receipt = browser.receipt(p.quote().userId(), p.id());
        assertThat(receipt.refundStatus()).isEqualTo("SUCCEEDED");
        assertThat(repository.refundRecorded(p.id())).isTrue();
        assertThat(receipt.refundPending()).isFalse();
        assertThat(receipt.status()).isEqualTo("SUCCEEDED");
        var page = orders.page(p.quote().userId(), 1, 10);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst().status()).isEqualTo("refunded");
        assertThat(page.items().getFirst().amount()).isEqualTo("10.00");
        assertThat(orders.page(buyer(), 1, 10).items()).isEmpty();
        assertThat(orders.page(p.quote().userId(), 2, 10).items()).isEmpty();
        var exceptionInput6 = p.quote().userId();
        assertThatThrownBy(() -> orders.page(exceptionInput6, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refundLeaseFencesStaleWorkerAndCanBeReclaimed() {
        CheckoutPayment p = expiredPurchase();
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        var first = tx.execute(s -> refunds.claimNext());
        assertThat(first).isNotNull();
        var leased = tx.execute(s -> refunds.claimNext());
        assertThat(leased).isNull();
        jdbc.update(
                "UPDATE biz_refund SET lease_until=clock_timestamp()-interval '1 second' WHERE payment_id=?",
                p.id());
        var second = tx.execute(s -> refunds.claimNext());
        assertThat(second.version()).isEqualTo(first.version() + 1);
        assertThat(second.firstAttemptAt()).isEqualTo(first.firstAttemptAt());
        tx.executeWithoutResult(s -> refunds.finish(first, "re_stale", "SUCCEEDED", "STALE"));
        assertThat(refunds.status(p.id())).isEqualTo("PENDING");
        tx.executeWithoutResult(
                s -> refunds.finish(second, "re_test", "PROCESSING", "PENDING_PROVIDER"));
        assertThat(refunds.status(p.id())).isEqualTo("PROCESSING");
        assertThat(browser.receipt(p.quote().userId(), p.id()).refundPending()).isTrue();
    }

    @Test
    void olderUnknownRefundRequiresReviewWithoutRemoteCreation() {
        CheckoutPayment p = expiredPurchase();
        jdbc.update(
                "UPDATE biz_refund SET first_attempt_at=clock_timestamp()-interval '25 hours' WHERE payment_id=?",
                p.id());
        var provider = mock(com.blocbuy.payment.gateway.RefundGateway.class);
        var worker = new com.blocbuy.payment.service.RefundService(refunds, provider, transactions);
        worker.processNext();
        assertThat(refunds.status(p.id())).isEqualTo("REVIEW");
        assertThat(worker.processNext()).isFalse();
        verify(provider, never()).createRefund(any());
        when(provider.retrieveRefund("re_recovered"))
                .thenAnswer(
                        call -> {
                            String obligation =
                                    jdbc.queryForObject(
                                            "SELECT id::text FROM biz_refund WHERE payment_id=?",
                                            String.class,
                                            p.id());
                            return new com.blocbuy.payment.gateway.RefundGateway.Snapshot(
                                    "re_recovered",
                                    "pi_" + p.id(),
                                    obligation,
                                    1000,
                                    "sgd",
                                    "succeeded");
                        });
        assertThat(worker.reconcile(p.id(), "re_recovered")).isTrue();
        assertThat(refunds.status(p.id())).isEqualTo("SUCCEEDED");
    }

    private CheckoutPayment expiredPurchase() {
        CheckoutPayment p = prepare(buyer(), "START", product);
        paid(p);
        payments.recover(p.id(), "test-operator", true);
        UUID group = deal();
        jdbc.update(
                "UPDATE biz_group_deal SET expire_time=clock_timestamp()-interval '1 second' WHERE id=?",
                group);
        coordinator.expireTeam(group);
        return p;
    }

    @Test
    void browserServiceCreatesOwnedIntentAndCheckoutAndRejectsInvalidCommands() {
        UUID user = buyer();
        UUID intent = browser.createIntent(user, "START", product);
        var result = browser.prepare(user, intent, "browser-native-test");
        assertThat(browser.receipt(user, result.paymentId()).status()).isEqualTo("CREATED");
        assertThatThrownBy(() -> browser.createIntent(user, "INVALID", product))
                .isInstanceOf(com.blocbuy.common.exception.BizException.class);
        assertThatThrownBy(() -> browser.prepare(user, intent, ""))
                .isInstanceOf(com.blocbuy.common.exception.BizException.class);
    }

    private CheckoutPayment prepare(UUID buyer, String operation, UUID business) {
        UUID intent = coordinator.intent(buyer, operation, business);
        var result = payments.prepare(buyer, intent, UUID.randomUUID().toString());
        return repository.findById(result.paymentId()).orElseThrow();
    }

    private void paid(CheckoutPayment payment) {
        sessions.put(payment.sessionId(), session(payment, "complete", "paid"));
    }

    private CheckoutGateway.Session session(CheckoutPayment p, String status, String paid) {
        return new CheckoutGateway.Session(
                "cs_test_" + p.id(),
                p.id().toString(),
                p.quote().intentId().toString(),
                "paid".equals(paid) ? "pi_" + p.id() : null,
                p.quote().amount().movePointRight(2).longValueExact(),
                "sgd",
                status,
                paid,
                false,
                "https://checkout.stripe.com/c/pay/cs_test_" + p.id(),
                p.sessionExpiresAt());
    }

    private UUID deal() {
        return jdbc.queryForObject("SELECT id FROM biz_group_deal", UUID.class);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
