package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.bootstrap.fixture.PaymentFixtureConfiguration;
import com.blocbuy.bootstrap.fixture.PaymentLifecycleFixture;
import com.blocbuy.bootstrap.fixture.StripeTestDatabase;
import com.blocbuy.bootstrap.scheduling.RefundWorkerJob;
import com.blocbuy.bootstrap.scheduling.TeamExpirySweepJob;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.event.TeamSucceeded;
import com.blocbuy.groupbuy.service.GroupDealExpiryService;
import com.blocbuy.groupbuy.service.GroupDealExpirySweeper;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.RefundGateway;
import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.RefundWork;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.service.PaymentBrowserService;
import com.blocbuy.payment.service.StripeCheckoutService;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The scheduled team-expiry path end to end (#28, NFR-32): lease, sweeper, per-team transactions
 * and the {@code TeamExpired} subscribers against real PostgreSQL, with only the Stripe network
 * mocked. Scheduling is switched on here and nowhere else in the test suite; the timer's first tick
 * is an hour away, so every run below is the one the test triggers.
 */
@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/stripe-fixture",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1",
            "blocbuy.payment.stripe.checkout-enabled=true",
            "blocbuy.payment.stripe.api-key=sk_test_fixture_placeholder",
            "blocbuy.payment.stripe.webhook-secret=whsec_fixture_placeholder",
            "blocbuy.payment.checkout.success-url=https://shop.example/payments/return",
            "blocbuy.payment.checkout.cancel-url=https://shop.example/payments/return",
            "blocbuy.events.enabled=true",
            "blocbuy.scheduling.enabled=true",
            "blocbuy.scheduling.team-expiry.initial-delay=PT1H",
            "blocbuy.scheduling.team-expiry.interval=PT1H",
            "blocbuy.scheduling.team-expiry.batch-size=2",
            "blocbuy.scheduling.refund-worker.initial-delay=PT1H",
            "blocbuy.scheduling.refund-worker.interval=PT1H"
        })
@ActiveProfiles("local")
@Import(PaymentFixtureConfiguration.class)
@DirtiesContext
class TeamExpirySchedulingIT {
    private static final StripeTestDatabase DATABASE =
            new StripeTestDatabase("blocbuy_stripe_local");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::url);
        properties.add("spring.datasource.username", DATABASE::username);
        properties.add("spring.datasource.password", DATABASE::password);
    }

    @Autowired private TeamExpirySweepJob job;
    @Autowired private RefundWorkerJob refundWorker;
    @Autowired private PaymentBrowserService browser;
    @Autowired private PaymentLifecycleFixture coordinator;
    @Autowired private StripeCheckoutService payments;
    @Autowired private CheckoutPaymentRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private GroupDealExpiryService expiry;
    @Autowired private GroupDealExpirySweeper sweeper;
    @MockitoBean private CheckoutGateway gateway;
    @MockitoBean private RefundGateway refundGateway;
    private final Map<String, CheckoutGateway.Session> sessions = new ConcurrentHashMap<>();
    private UUID product;

    @BeforeEach
    void setup() {
        jdbc.execute(
                "TRUNCATE sys_outbox,sys_scheduled_item_retry,sys_scheduler_lock,log_refund_attempt,"
                        + "biz_payment,log_payment_webhook_event,biz_refund,log_payment_review,"
                        + "biz_payment_review_case,biz_purchase_intent,stripe_local_expiry_request,"
                        + "biz_order,biz_group_member,biz_group_deal,biz_group_participation_receipt,"
                        + "biz_team_price_policy,biz_product,sys_user");
        product = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO biz_product(id,product_no,title,original_price,currency,stock,status,team_target_size,team_lifetime_seconds) VALUES(?,?,'Expiry test product',20,'sgd',100,'on_sale',2,7200)",
                product,
                product.toString());
        UUID admin = buyer();
        jdbc.update(
                "INSERT INTO biz_team_price_policy(product_id,amount,currency,valid_from,valid_until,create_by,update_by,version) VALUES(?,10,'sgd',clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 day',?,?,1)",
                product,
                admin,
                admin);
        when(gateway.createCheckoutSession(any()))
                .thenAnswer(call -> {
                    CheckoutPayment p = call.getArgument(0);
                    var session = session(p, "open", "unpaid");
                    sessions.put(session.id(), session);
                    return session;
                });
        when(gateway.retrieveCheckoutSession(any()))
                .thenAnswer(call -> sessions.get(call.getArgument(0)));
    }

    @Test
    void oneRunExpiresOverdueTeamsWithTheirConsequencesAndASecondRunChangesNothing() {
        UUID overdue = paidTeam();
        UUID running = paidTeam();
        overdue(overdue, 1);

        job.run();

        assertThat(dealStatus(overdue)).isEqualTo("expired");
        assertThat(orderStatus(overdue)).isEqualTo("cancelled");
        assertThat(refunds(overdue)).isEqualTo(1);
        assertThat(refundStatus(overdue)).isEqualTo("pending");
        assertThat(dealStatus(running)).isEqualTo("open");
        assertThat(orderStatus(running)).isEqualTo("pending");
        assertThat(refunds(running)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT locked_until <= clock_timestamp() FROM sys_scheduler_lock"
                                        + " WHERE lock_key = 'team-expiry-sweep'",
                                Boolean.class))
                .as("the lease is released at the end of the run")
                .isTrue();

        job.run();

        assertThat(dealStatus(overdue)).isEqualTo("expired");
        assertThat(refunds(overdue)).isEqualTo(1);
        assertThat(dealStatus(running)).isEqualTo("open");
    }

    @Test
    void committedTeamSuccessWritesOneOutboxEventWithEveryMember() {
        UUID deal = paidTeam();
        UUID leader = member(deal);
        UUID joiner = buyer();
        UUID intent = coordinator.intent(joiner, "JOIN", deal);
        var prepared = payments.prepare(joiner, intent, UUID.randomUUID().toString());
        CheckoutPayment payment = repository.findById(prepared.paymentId()).orElseThrow();
        sessions.put(payment.sessionId(), session(payment, "complete", "paid"));

        payments.recover(payment.id(), "test-operator", true);

        assertThat(dealStatus(deal)).isEqualTo("succeeded");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sys_outbox"
                                        + " WHERE event_type='TeamSucceeded' AND aggregate_id=?",
                                Long.class,
                                deal))
                .isEqualTo(1);
        String payload =
                jdbc.queryForObject(
                        "SELECT payload::text FROM sys_outbox"
                                + " WHERE event_type='TeamSucceeded' AND aggregate_id=?",
                        String.class,
                        deal);
        assertThat(JsonPath.<java.util.List<String>>read(payload, "$.recipientUserIds"))
                .containsExactlyInAnyOrder(leader.toString(), joiner.toString());
    }

    @Test
    void rolledBackTeamSuccessWritesNoOutboxEvent() {
        UUID deal = UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactions);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        transaction.executeWithoutResult(status -> {
                            events.publishEvent(
                                    new TeamSucceeded(
                                            deal, UUID.randomUUID(), 2, java.time.Instant.now()));
                            throw new IllegalStateException("force rollback");
                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("force rollback");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sys_outbox WHERE aggregate_id=?",
                                Long.class,
                                deal))
                .isZero();
    }

    @Test
    void outboxInsertFailureDuringCommitRollsBackSettlementAndTeamSuccess() {
        UUID deal = paidTeam();
        UUID joiner = buyer();
        UUID intent = coordinator.intent(joiner, "JOIN", deal);
        var prepared = payments.prepare(joiner, intent, UUID.randomUUID().toString());
        CheckoutPayment payment = repository.findById(prepared.paymentId()).orElseThrow();
        sessions.put(payment.sessionId(), session(payment, "complete", "paid"));
        Long rowsBefore = jdbc.queryForObject("SELECT count(*) FROM sys_outbox", Long.class);
        Integer stockBefore =
                jdbc.queryForObject(
                        "SELECT stock FROM biz_product WHERE id = ?", Integer.class, product);
        var paymentStatusBefore = payment.status();
        UUID paymentId = payment.id();
        jdbc.execute(
                """
                CREATE FUNCTION test_fail_team_outbox() RETURNS trigger LANGUAGE plpgsql AS
                $$ BEGIN RAISE EXCEPTION 'injected outbox commit failure'; END $$
                """);
        jdbc.execute(
                """
                CREATE TRIGGER test_fail_team_outbox BEFORE INSERT ON sys_outbox
                FOR EACH ROW WHEN (NEW.event_type = 'TeamSucceeded')
                EXECUTE FUNCTION test_fail_team_outbox()
                """);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> payments.recover(paymentId, "test-operator", true))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .hasStackTraceContaining("injected outbox commit failure");
        } finally {
            jdbc.execute("DROP TRIGGER test_fail_team_outbox ON sys_outbox");
            jdbc.execute("DROP FUNCTION test_fail_team_outbox()");
        }
        assertThat(dealStatus(deal)).isEqualTo("open");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_size FROM biz_group_deal WHERE id = ?",
                                Integer.class,
                                deal))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_group_member WHERE deal_id = ?",
                                Long.class,
                                deal))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_order WHERE deal_id = ?",
                                Long.class,
                                deal))
                .isEqualTo(1);
        assertThat(orderStatus(deal)).isEqualTo("pending");
        assertThat(repository.findById(payment.id()).orElseThrow().status())
                .isEqualTo(paymentStatusBefore);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT stock FROM biz_product WHERE id = ?",
                                Integer.class,
                                product))
                .isEqualTo(stockBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_outbox", Long.class))
                .isEqualTo(rowsBefore);
        // Retry after recovery must commit exactly one team-success effect.
        payments.recover(payment.id(), "test-operator", true);
        assertThat(dealStatus(deal)).isEqualTo("succeeded");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sys_outbox WHERE event_type = 'TeamSucceeded' AND aggregate_id = ?",
                                Long.class,
                                deal))
                .isEqualTo(1);
    }

    /**
     * A test trigger makes the middle team's expiry UPDATE fail inside its own transaction. Only
     * that team rolls back and receives durable backoff. The next run bypasses it and resolves the
     * newer team; after the backoff lapses, another run retries it.
     */
    @Test
    void aTeamWhoseExpiryFailsRollsBackAloneAndIsRetriedByTheNextRun() {
        UUID first = paidTeam();
        UUID poisoned = paidTeam();
        UUID last = paidTeam();
        overdue(first, 3);
        overdue(poisoned, 2);
        overdue(last, 1);
        jdbc.execute(
                "CREATE FUNCTION test_fail_team_expiry() RETURNS trigger LANGUAGE plpgsql AS"
                        + " $$ BEGIN RAISE EXCEPTION 'test fault for team %', NEW.id; END $$");
        jdbc.execute(
                "CREATE TRIGGER test_fail_team_expiry BEFORE UPDATE ON biz_group_deal"
                        + " FOR EACH ROW WHEN (NEW.id = '"
                        + poisoned
                        + "'::uuid) EXECUTE FUNCTION test_fail_team_expiry()");
        try {
            job.run();
        } finally {
            jdbc.execute("DROP TRIGGER test_fail_team_expiry ON biz_group_deal");
            jdbc.execute("DROP FUNCTION test_fail_team_expiry()");
        }

        assertThat(dealStatus(first)).isEqualTo("expired");
        assertThat(refunds(first)).isEqualTo(1);
        assertThat(dealStatus(last)).isEqualTo("open");
        assertThat(dealStatus(poisoned)).isEqualTo("open");
        assertThat(orderStatus(poisoned)).isEqualTo("pending");
        assertThat(refunds(poisoned)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT failure_count FROM sys_scheduled_item_retry"
                                        + " WHERE job_key='team-expiry-sweep' AND item_id=?",
                                Integer.class,
                                poisoned))
                .isEqualTo(1);

        job.run();

        assertThat(dealStatus(last)).isEqualTo("expired");
        assertThat(refunds(last)).isEqualTo(1);
        assertThat(dealStatus(poisoned)).isEqualTo("open");

        jdbc.update(
                "UPDATE sys_scheduled_item_retry SET retry_after=clock_timestamp()-interval '1 second'"
                        + " WHERE job_key='team-expiry-sweep' AND item_id=?",
                poisoned);
        job.run();

        assertThat(dealStatus(poisoned)).isEqualTo("expired");
        assertThat(orderStatus(poisoned)).isEqualTo("cancelled");
        assertThat(refunds(poisoned)).isEqualTo(1);
    }

    @Test
    void aLiveLeaseHeldByAnotherInstanceSkipsTheRun() {
        UUID overdue = paidTeam();
        overdue(overdue, 1);
        jdbc.update(
                "INSERT INTO sys_scheduler_lock(lock_key,owner,locked_until)"
                        + " VALUES('team-expiry-sweep','other-instance',clock_timestamp()+interval '5 minutes')");

        job.run();

        assertThat(dealStatus(overdue)).isEqualTo("open");

        jdbc.update(
                "UPDATE sys_scheduler_lock SET locked_until=clock_timestamp()-interval '1 second'");

        job.run();

        assertThat(dealStatus(overdue)).isEqualTo("expired");
    }

    @Test
    void expiryToRefundToBuyerReceiptIsAuditedAndRetriesWithoutDuplicatePayment() {
        UUID deal = paidTeam();
        UUID payment =
                jdbc.queryForObject(
                        "SELECT payment_id FROM biz_group_member WHERE deal_id=?",
                        UUID.class,
                        deal);
        UUID buyer =
                jdbc.queryForObject(
                        "SELECT user_id FROM biz_group_member WHERE deal_id=?", UUID.class, deal);
        overdue(deal, 1);
        job.run();

        assertThat(refundStatus(deal)).isEqualTo("pending");
        assertThat(browser.receipt(buyer, payment).refundStatus()).isEqualTo("PENDING");
        AtomicReference<String> firstKey = new AtomicReference<>();
        when(refundGateway.createRefund(any()))
                .thenAnswer(call -> {
                    assertThat(
                                    org.springframework.transaction.support
                                            .TransactionSynchronizationManager
                                            .isActualTransactionActive())
                            .isFalse();
                    RefundWork work = call.getArgument(0);
                    firstKey.set(work.providerKey());
                    throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
                })
                .thenAnswer(call -> {
                    RefundWork work = call.getArgument(0);
                    assertThat(work.providerKey()).isEqualTo(firstKey.get());
                    return new RefundGateway.Snapshot(
                            "re_expired_team",
                            work.providerIntentId(),
                            work.id().toString(),
                            work.amount().movePointRight(2).longValueExact(),
                            work.currency(),
                            "succeeded");
                });

        refundWorker.run();
        assertThat(refundStatus(deal)).isEqualTo("processing");
        assertThat(browser.receipt(buyer, payment).refundStatus()).isEqualTo("PROCESSING");
        assertThat(auditCount(payment, "PROVIDER_RETRY")).isEqualTo(1);

        jdbc.update(
                "UPDATE biz_refund SET next_attempt_at=clock_timestamp()-interval '1 second'"
                        + " WHERE payment_id=?",
                payment);
        refundWorker.run();
        refundWorker.run();

        assertThat(refundStatus(deal)).isEqualTo("succeeded");
        assertThat(browser.receipt(buyer, payment).refundStatus()).isEqualTo("SUCCEEDED");
        assertThat(browser.receipt(buyer, payment).refundPending()).isFalse();
        assertThat(auditCount(payment, "CLAIMED")).isEqualTo(2);
        assertThat(auditCount(payment, "PROVIDER_SUCCEEDED")).isEqualTo(1);
        assertThat(refunds(deal)).isEqualTo(1);
        verify(refundGateway, times(2)).createRefund(any());
    }

    @Test
    void contendingExpiryAndSettlementCommitOneTerminalEventAndOneRefundPerPayment()
            throws Exception {
        UUID deal = paidTeam();
        UUID joiner = buyer();
        UUID intent = coordinator.intent(joiner, "JOIN", deal);
        var prepared = payments.prepare(joiner, intent, UUID.randomUUID().toString());
        CheckoutPayment payment = repository.findById(prepared.paymentId()).orElseThrow();
        sessions.put(payment.sessionId(), session(payment, "complete", "paid"));
        overdue(deal, 1);
        Integer stockBefore =
                jdbc.queryForObject(
                        "SELECT stock FROM biz_product WHERE id=?", Integer.class, product);
        var start = new CountDownLatch(1);
        List<Future<?>> racers = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(3)) {
            new TransactionTemplate(transactions)
                    .executeWithoutResult(status -> {
                        jdbc.queryForObject(
                                "SELECT id FROM biz_group_deal WHERE id=? FOR UPDATE",
                                UUID.class,
                                deal);
                        for (int i = 0; i < 2; i++) {
                            racers.add(
                                    executor.submit(() -> {
                                        awaitStart(start);
                                        return expiry.expireOne(deal);
                                    }));
                        }
                        racers.add(
                                executor.submit(() -> {
                                    awaitStart(start);
                                    payments.recover(payment.id(), "test-operator", true);
                                }));
                        start.countDown();
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                        while (jdbc.queryForObject(
                                        "SELECT count(*) FROM pg_stat_activity"
                                                + " WHERE datname=current_database() AND wait_event_type='Lock'",
                                        Integer.class)
                                < 2) {
                            if (System.nanoTime() >= deadline) {
                                throw new AssertionError(
                                        "expiry/settlement did not contend on real PostgreSQL locks");
                            }
                            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                        }
                    });
            for (Future<?> racer : racers) {
                racer.get(20, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
        }
        assertThat(dealStatus(deal)).isEqualTo("expired");
        assertThat(orderStatus(deal)).isEqualTo("cancelled");
        assertThat(refunds(deal)).isEqualTo(1);
        assertThat(expiredEvents(deal)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_refund WHERE payment_id=?",
                                Long.class,
                                payment.id()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_order WHERE payment_id=?",
                                Long.class,
                                payment.id()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT stock FROM biz_product WHERE id=?", Integer.class, product))
                .isEqualTo(stockBefore);
        assertThat(expiry.expireOne(deal)).isFalse();
        assertThat(expiredEvents(deal)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"refund", "outbox"})
    void aShardedSubscriberOrOutboxFailureRollsBackAloneAndRetriesExactlyOnce(String failure) {
        UUID poisoned = paidTeam();
        UUID healthy = paidTeam();
        overdue(poisoned, 2);
        overdue(healthy, 1);
        String table = failure.equals("refund") ? "biz_refund" : "sys_outbox";
        String condition =
                failure.equals("refund")
                        ? "NEW.deal_id='" + poisoned + "'::uuid"
                        : "NEW.event_type='TeamExpired' AND NEW.aggregate_id='"
                                + poisoned
                                + "'::uuid";
        jdbc.execute(
                "CREATE FUNCTION test_fail_sharded_expiry() RETURNS trigger LANGUAGE plpgsql AS"
                        + " $$ BEGIN RAISE EXCEPTION 'injected sharded expiry failure'; END $$");
        jdbc.execute(
                "CREATE TRIGGER test_fail_sharded_expiry BEFORE INSERT ON "
                        + table
                        + " FOR EACH ROW WHEN ("
                        + condition
                        + ") EXECUTE FUNCTION test_fail_sharded_expiry()");
        try {
            sweepEveryShard();
        } finally {
            jdbc.execute("DROP TRIGGER test_fail_sharded_expiry ON " + table);
            jdbc.execute("DROP FUNCTION test_fail_sharded_expiry()");
        }
        assertThat(dealStatus(poisoned)).isEqualTo("open");
        assertThat(orderStatus(poisoned)).isEqualTo("pending");
        assertThat(refunds(poisoned)).isZero();
        assertThat(expiredEvents(poisoned)).isZero();
        assertThat(dealStatus(healthy)).isEqualTo("expired");
        assertThat(orderStatus(healthy)).isEqualTo("cancelled");
        assertThat(refunds(healthy)).isEqualTo(1);
        assertThat(expiredEvents(healthy)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT failure_count FROM sys_scheduled_item_retry"
                                        + " WHERE job_key='team-expiry-sweep' AND item_id=?",
                                Integer.class,
                                poisoned))
                .isEqualTo(1);
        sweepEveryShard();
        assertThat(dealStatus(poisoned)).isEqualTo("open");
        jdbc.update(
                "UPDATE sys_scheduled_item_retry SET retry_after=clock_timestamp()-interval '1 second'"
                        + " WHERE job_key='team-expiry-sweep' AND item_id=?",
                poisoned);
        sweepEveryShard();
        sweepEveryShard();
        assertThat(dealStatus(poisoned)).isEqualTo("expired");
        assertThat(orderStatus(poisoned)).isEqualTo("cancelled");
        assertThat(refunds(poisoned)).isEqualTo(1);
        assertThat(expiredEvents(poisoned)).isEqualTo(1);
        assertThat(refunds(healthy)).isEqualTo(1);
        assertThat(expiredEvents(healthy)).isEqualTo(1);
    }

    private void sweepEveryShard() {
        for (int index = 0; index < 6; index++) {
            sweeper.sweep(500, 6, index, () -> true);
        }
    }

    private long expiredEvents(UUID deal) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM sys_outbox WHERE event_type='TeamExpired' AND aggregate_id=?",
                Long.class,
                deal);
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("race start gate timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("race interrupted", e);
        }
    }

    /** A paid leader's team: one member, one pending order, one successful payment. */
    private UUID paidTeam() {
        UUID buyer = buyer();
        UUID intent = coordinator.intent(buyer, "START", product);
        var prepared = payments.prepare(buyer, intent, UUID.randomUUID().toString());
        CheckoutPayment payment = repository.findById(prepared.paymentId()).orElseThrow();
        sessions.put(payment.sessionId(), session(payment, "complete", "paid"));
        payments.recover(payment.id(), "test-operator", true);
        return jdbc.queryForObject(
                "SELECT deal_id FROM biz_group_member WHERE payment_id=?",
                UUID.class,
                payment.id());
    }

    private UUID member(UUID deal) {
        return jdbc.queryForObject(
                "SELECT user_id FROM biz_group_member WHERE deal_id=? AND member_role='leader'",
                UUID.class,
                deal);
    }

    /** Moves a deadline into the past; older deadlines are swept first. */
    private void overdue(UUID deal, int minutesAgo) {
        jdbc.update(
                "UPDATE biz_group_deal SET expire_time=clock_timestamp()-make_interval(mins => ?)"
                        + " WHERE id=?",
                minutesAgo,
                deal);
    }

    private String dealStatus(UUID deal) {
        return jdbc.queryForObject(
                "SELECT status FROM biz_group_deal WHERE id=?", String.class, deal);
    }

    private String orderStatus(UUID deal) {
        return jdbc.queryForObject(
                "SELECT status FROM biz_order WHERE deal_id=?", String.class, deal);
    }

    private long refunds(UUID deal) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM biz_refund r JOIN biz_group_member m ON m.payment_id=r.payment_id"
                        + " WHERE m.deal_id=?",
                Long.class,
                deal);
    }

    private String refundStatus(UUID deal) {
        return jdbc.queryForObject(
                "SELECT r.status FROM biz_refund r JOIN biz_group_member m ON m.payment_id=r.payment_id"
                        + " WHERE m.deal_id=?",
                String.class,
                deal);
    }

    private long auditCount(UUID payment, String result) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM log_refund_attempt a JOIN biz_refund r ON r.id=a.refund_id"
                        + " WHERE r.payment_id=? AND a.result=?",
                Long.class,
                payment,
                result);
    }

    private UUID buyer() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO sys_user(id,register_source,status) VALUES(?,'local','active')", id);
        return id;
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
}
