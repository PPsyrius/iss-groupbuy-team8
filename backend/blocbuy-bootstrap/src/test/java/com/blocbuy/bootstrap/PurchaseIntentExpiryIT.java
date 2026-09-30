package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.blocbuy.bootstrap.fixture.PaymentFixtureConfiguration;
import com.blocbuy.bootstrap.fixture.PaymentLifecycleFixture;
import com.blocbuy.bootstrap.fixture.StripeTestDatabase;
import com.blocbuy.bootstrap.scheduling.PurchaseIntentExpiryJob;
import com.blocbuy.order.repository.PurchaseIntentRepository;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.RefundGateway;
import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.service.StripeCheckoutService;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real PostgreSQL transactions and payment settlement; only the provider network is mocked. */
@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/stripe-fixture",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1",
            "blocbuy.payment.checkout.success-url=https://shop.example/payments/return",
            "blocbuy.payment.checkout.cancel-url=https://shop.example/payments/return",
            "blocbuy.scheduling.enabled=true",
            "blocbuy.scheduling.purchase-intent-expiry.initial-delay=PT1H",
            "blocbuy.scheduling.purchase-intent-expiry.interval=PT1H",
            "blocbuy.scheduling.purchase-intent-expiry.batch-size=2",
            "blocbuy.scheduling.team-expiry.initial-delay=PT1H",
            "blocbuy.scheduling.refund-worker.initial-delay=PT1H"
        })
@ActiveProfiles({"local", "stripe-sprint2"})
@Import(PaymentFixtureConfiguration.class)
@DirtiesContext
class PurchaseIntentExpiryIT {
    private static final StripeTestDatabase DATABASE =
            new StripeTestDatabase("blocbuy_stripe_local");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", DATABASE::url);
        p.add("spring.datasource.username", DATABASE::username);
        p.add("spring.datasource.password", DATABASE::password);
    }

    @Autowired private PurchaseIntentExpiryJob job;
    @Autowired private PaymentLifecycleFixture intents;
    @Autowired private PurchaseIntentRepository intentRepository;
    @Autowired private StripeCheckoutService payments;
    @Autowired private CheckoutPaymentRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private MeterRegistry meters;
    @MockitoBean private CheckoutGateway gateway;
    @MockitoBean private RefundGateway refundGateway;
    private final Map<String, CheckoutGateway.Session> sessions = new ConcurrentHashMap<>();
    private UUID product;

    @BeforeEach
    void setup() {
        jdbc.execute(
                "TRUNCATE sys_scheduled_item_retry,sys_scheduler_lock,log_refund_attempt,biz_payment,"
                        + "log_payment_webhook_event,biz_refund,log_payment_review,biz_payment_review_case,"
                        + "biz_purchase_intent,stripe_local_expiry_request,biz_order,biz_group_member,"
                        + "biz_group_deal,biz_group_participation_receipt,biz_team_price_policy,biz_product,sys_user");
        sessions.clear();
        product = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO biz_product(id,product_no,title,original_price,currency,stock,status,"
                        + "team_target_size,team_lifetime_seconds) VALUES(?,?,'Expiry product',20,'sgd',100,'on_sale',2,7200)",
                product,
                product.toString());
        UUID admin = buyer();
        jdbc.update(
                "INSERT INTO biz_team_price_policy(product_id,amount,currency,valid_from,valid_until,"
                        + "create_by,update_by,version) VALUES(?,10,'sgd',clock_timestamp()-interval '1 hour',"
                        + "clock_timestamp()+interval '1 day',?,?,1)",
                product,
                admin,
                admin);
        when(gateway.createCheckoutSession(any()))
                .thenAnswer(
                        call -> {
                            CheckoutPayment p = call.getArgument(0);
                            var session = session(p, false);
                            sessions.put(session.id(), session);
                            return session;
                        });
        when(gateway.retrieveCheckoutSession(any()))
                .thenAnswer(call -> sessions.get(call.getArgument(0)));
    }

    @Test
    void boundedRunsSoftExpireOnlyUnpaidIntentsAndReplayDoesNotWrite() {
        UUID first = intent();
        UUID second = intent();
        UUID third = intent();
        UUID live = intent();
        CheckoutPayment paid = prepared("START");
        sessions.put(paid.sessionId(), session(paid, true));
        payments.recover(paid.id(), "test", true);
        overdue(first, 3);
        overdue(second, 2);
        overdue(third, 1);
        overdue(paid.quote().intentId(), 4);
        job.run();
        assertThat(expired(first)).isTrue();
        assertThat(expired(second)).isTrue();
        assertThat(expired(third)).isFalse();
        job.run();
        assertThat(expired(third)).isTrue();
        assertThat(expired(live)).isFalse();
        assertThat(expired(paid.quote().intentId())).isFalse();
        job.run();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM biz_purchase_intent WHERE id=?",
                                Integer.class,
                                first))
                .isEqualTo(1);
        assertThat(count("biz_purchase_intent")).isEqualTo(5);
        assertThat(count("biz_order")).isEqualTo(1);
        assertThat(count("biz_refund")).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT locked_until <= clock_timestamp() FROM sys_scheduler_lock"
                                        + " WHERE lock_key='purchase-intent-expiry'",
                                Boolean.class))
                .isTrue();
    }

    @Test
    void oneFailedIntentRollsBackAloneAndBackoffLetsYoungerIntentsProgress() {
        UUID poison = intent();
        UUID good = intent();
        UUID younger = intent();
        overdue(poison, 3);
        overdue(good, 2);
        overdue(younger, 1);
        double failedBefore = failures();
        jdbc.execute(
                "CREATE FUNCTION test_fail_intent_expiry() RETURNS trigger LANGUAGE plpgsql AS"
                        + " $$ BEGIN RAISE EXCEPTION 'test fault'; END $$");
        jdbc.execute(
                "CREATE TRIGGER test_fail_intent_expiry BEFORE UPDATE ON biz_purchase_intent"
                        + " FOR EACH ROW WHEN (NEW.id='"
                        + poison
                        + "'::uuid)"
                        + " EXECUTE FUNCTION test_fail_intent_expiry()");
        try {
            job.run();
        } finally {
            jdbc.execute("DROP TRIGGER test_fail_intent_expiry ON biz_purchase_intent");
            jdbc.execute("DROP FUNCTION test_fail_intent_expiry()");
        }
        assertThat(expired(poison)).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM biz_purchase_intent WHERE id=?",
                                Integer.class,
                                poison))
                .isZero();
        assertThat(expired(good)).isTrue();
        assertThat(expired(younger)).isFalse();
        assertThat(failures() - failedBefore).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT failure_count FROM sys_scheduled_item_retry"
                                        + " WHERE job_key='purchase-intent-expiry' AND item_id=?",
                                Integer.class,
                                poison))
                .isEqualTo(1);
        job.run();
        assertThat(expired(younger)).isTrue();
        assertThat(expired(poison)).isFalse();
        releaseBackoff(poison);
        job.run();
        assertThat(expired(poison)).isTrue();
    }

    @Test
    void aSettlingStartIntentIsSkippedWithoutWaitingAndOtherIntentsStillExpire() throws Exception {
        UUID busy = intent();
        UUID good = intent();
        overdue(busy, 2);
        overdue(good, 1);
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(1)) {
            var holder =
                    workers.submit(
                            () ->
                                    new TransactionTemplate(transactions)
                                            .executeWithoutResult(
                                                    s -> {
                                                        jdbc.queryForObject(
                                                                "SELECT id FROM biz_purchase_intent WHERE id=? FOR UPDATE",
                                                                UUID.class,
                                                                busy);
                                                        held.countDown();
                                                        await(release);
                                                    }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                job.run();
                assertThat(expired(busy)).isFalse();
                assertThat(expired(good)).isTrue();
            } finally {
                release.countDown();
            }
            holder.get(5, TimeUnit.SECONDS);
        }
        releaseBackoff(busy);
        job.run();
        assertThat(expired(busy)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"START", "JOIN"})
    void aLateWebhookRacingCleanupHasOneRefundAndNoOrderEvenOnReplay(String operation)
            throws Exception {
        CheckoutPayment late = latePayment(operation);
        sessions.put(late.sessionId(), session(late, true));
        var event =
                new CheckoutGateway.Event(
                        "evt_" + late.id(),
                        "checkout.session.completed",
                        late.sessionId(),
                        false,
                        null);
        when(gateway.verifyCheckoutWebhook("body", "signature")).thenReturn(Optional.of(event));
        var start = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var cleanup =
                    workers.submit(
                            () -> {
                                await(start);
                                job.run();
                            });
            var webhook =
                    workers.submit(
                            () -> {
                                await(start);
                                payments.handleWebhook("body", "signature");
                            });
            cleanup.get(10, TimeUnit.SECONDS);
            webhook.get(10, TimeUnit.SECONDS);
        }
        payments.handleWebhook("body", "signature");
        job.run();
        assertThat(repository.findById(late.id()).orElseThrow().status())
                .isEqualTo(CheckoutPayment.Status.SUCCEEDED);
        assertThat(repository.outcome(late.id())).isEqualTo("REFUND_REQUIRED");
        assertThat(count("biz_refund")).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_order WHERE payment_id=?",
                                Long.class,
                                late.id()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_group_member WHERE payment_id=?",
                                Long.class,
                                late.id()))
                .isZero();
    }

    @Test
    void aForeignLeaseSkipsTheRun() {
        UUID id = intent();
        overdue(id, 1);
        jdbc.update(
                "INSERT INTO sys_scheduler_lock(lock_key,owner,locked_until)"
                        + " VALUES('purchase-intent-expiry','other',clock_timestamp()+interval '5 minutes')");
        job.run();
        assertThat(expired(id)).isFalse();
    }

    @Test
    void aPaidRefusedIntentRemainsUnexpiredAndYieldsItsPlaceToAnUnpaidCandidate() {
        CheckoutPayment late = latePayment("START");
        sessions.put(late.sessionId(), session(late, true));
        payments.recover(late.id(), "test", true);
        assertThat(repository.outcome(late.id())).isEqualTo("REFUND_REQUIRED");
        UUID good = intent();
        overdue(good, 1);
        job.run();
        assertThat(expired(late.quote().intentId())).isFalse();
        assertThat(expired(good)).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT fulfilled_payment_id FROM biz_purchase_intent WHERE id=?",
                                UUID.class,
                                late.quote().intentId()))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT retry_after = 'infinity'::timestamptz FROM sys_scheduled_item_retry"
                                        + " WHERE job_key='purchase-intent-expiry' AND item_id=?",
                                Boolean.class,
                                late.quote().intentId()))
                .isTrue();
        assertThat(count("biz_refund")).isEqualTo(1);
        assertThat(count("biz_order")).isZero();
        assertThat(intentRepository.dueForExpiry(intentRepository.currentTime(), 2))
                .doesNotContain(late.quote().intentId());
        job.run();
        assertThat(expired(late.quote().intentId())).isFalse();
    }

    @Test
    void aHeldRecoveryRowTimesOutAndReleasesTheLease() throws Exception {
        CheckoutPayment late = latePayment("START");
        sessions.put(late.sessionId(), session(late, true));
        payments.recover(late.id(), "test", true);
        UUID id = late.quote().intentId();
        jdbc.update(
                "INSERT INTO sys_scheduled_item_retry(job_key,item_id,failure_count,retry_after)"
                        + " VALUES('purchase-intent-expiry',?,1,clock_timestamp()-interval '1 second')",
                id);
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        double failedBefore =
                meters.counter(
                                "blocbuy.scheduler.runs",
                                "job",
                                "purchase-intent-expiry",
                                "outcome",
                                "failed")
                        .count();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var holder =
                    workers.submit(
                            () ->
                                    new TransactionTemplate(transactions)
                                            .executeWithoutResult(
                                                    status -> {
                                                        jdbc.queryForObject(
                                                                "SELECT item_id FROM sys_scheduled_item_retry"
                                                                        + " WHERE job_key='purchase-intent-expiry' AND item_id=? FOR UPDATE",
                                                                UUID.class,
                                                                id);
                                                        held.countDown();
                                                        await(release);
                                                    }));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                workers.submit(job::run).get(12, TimeUnit.SECONDS);
                assertThat(
                                meters.counter(
                                                "blocbuy.scheduler.runs",
                                                "job",
                                                "purchase-intent-expiry",
                                                "outcome",
                                                "failed")
                                        .count())
                        .isEqualTo(failedBefore + 1);
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT locked_until <= clock_timestamp()"
                                                + " FROM sys_scheduler_lock WHERE lock_key='purchase-intent-expiry'",
                                        Boolean.class))
                        .isTrue();
                assertThat(expired(id)).isFalse();
            } finally {
                release.countDown();
                holder.get(5, TimeUnit.SECONDS);
            }
        }
        job.run();
        assertThat(intentRepository.dueForExpiry(intentRepository.currentTime(), 2))
                .doesNotContain(id);
    }

    @Test
    void joinCleanupWaitsForTheTeamBeforeTouchingItsIntent() throws Exception {
        CheckoutPayment pending = prepared("JOIN");
        overdue(pending.quote().intentId(), 1);
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var holder =
                    workers.submit(
                            () ->
                                    new TransactionTemplate(transactions)
                                            .executeWithoutResult(
                                                    s -> {
                                                        jdbc.queryForObject(
                                                                "SELECT id FROM biz_group_deal WHERE id=? FOR UPDATE",
                                                                UUID.class,
                                                                pending.quote().businessId());
                                                        jdbc.queryForObject(
                                                                "SELECT id FROM biz_purchase_intent WHERE id=? FOR UPDATE",
                                                                UUID.class,
                                                                pending.quote().intentId());
                                                        held.countDown();
                                                        await(release);
                                                    }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            var cleanup = workers.submit(job::run);
            try {
                Awaitility.await()
                        .pollDelay(Duration.ZERO)
                        .pollInterval(Duration.ofMillis(10))
                        .atMost(Duration.ofSeconds(5))
                        .untilAsserted(
                                () ->
                                        assertThat(
                                                        jdbc.queryForObject(
                                                                "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()"
                                                                        + " AND wait_event_type='Lock' AND position('biz_group_deal' in query)>0",
                                                                Integer.class))
                                                .isPositive());
                assertThat(cleanup.isDone()).isFalse();
                assertThat(expired(pending.quote().intentId())).isFalse();
            } finally {
                release.countDown();
            }
            holder.get(5, TimeUnit.SECONDS);
            cleanup.get(5, TimeUnit.SECONDS);
        }
        assertThat(expired(pending.quote().intentId())).isTrue();
    }

    private UUID buyer() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO sys_user(id,register_source,status) VALUES(?,'local','active')", id);
        return id;
    }

    private UUID intent() {
        return intents.intent(buyer(), "START", product);
    }

    private CheckoutPayment prepared(String operation) {
        UUID businessId = product;
        if ("JOIN".equals(operation)) {
            CheckoutPayment leader = prepared("START");
            sessions.put(leader.sessionId(), session(leader, true));
            payments.recover(leader.id(), "test", true);
            businessId = leader.quote().businessId();
        }
        UUID user = buyer();
        UUID intent = intents.intent(user, operation, businessId);
        var result = payments.prepare(user, intent, UUID.randomUUID().toString());
        return repository.findById(result.paymentId()).orElseThrow();
    }

    private CheckoutPayment latePayment(String operation) {
        CheckoutPayment before = prepared(operation);
        Timestamp deadline = overdue(before.quote().intentId(), 3);
        jdbc.update(
                "UPDATE biz_payment SET quote_valid_until=?,session_expires_at=? WHERE id=?",
                deadline,
                deadline,
                before.id());
        return repository.findById(before.id()).orElseThrow();
    }

    private Timestamp overdue(UUID id, int minutes) {
        Timestamp deadline =
                jdbc.queryForObject(
                        "SELECT clock_timestamp()-make_interval(mins => ?)",
                        Timestamp.class,
                        minutes);
        jdbc.update("UPDATE biz_purchase_intent SET quote_valid_until=? WHERE id=?", deadline, id);
        return deadline;
    }

    private boolean expired(UUID id) {
        return jdbc.queryForObject(
                "SELECT expired_at IS NOT NULL FROM biz_purchase_intent WHERE id=?",
                Boolean.class,
                id);
    }

    private void releaseBackoff(UUID id) {
        jdbc.update(
                "UPDATE sys_scheduled_item_retry SET retry_after=clock_timestamp()-interval '1 second'"
                        + " WHERE job_key='purchase-intent-expiry' AND item_id=?",
                id);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private double failures() {
        return meters.counter("blocbuy.purchase.intent.expiry.intents", "result", "failed").count();
    }

    private CheckoutGateway.Session session(CheckoutPayment p, boolean paid) {
        return new CheckoutGateway.Session(
                "cs_test_" + p.id(),
                p.id().toString(),
                p.quote().intentId().toString(),
                paid ? "pi_" + p.id() : null,
                p.quote().amount().movePointRight(2).longValueExact(),
                "sgd",
                paid ? "complete" : "open",
                paid ? "paid" : "unpaid",
                false,
                "https://checkout.stripe.com/c/pay/cs_test_" + p.id(),
                p.sessionExpiresAt());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
