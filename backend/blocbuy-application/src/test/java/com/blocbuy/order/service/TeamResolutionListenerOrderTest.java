package com.blocbuy.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.event.MemberJoined;
import com.blocbuy.groupbuy.event.TeamExpired;
import com.blocbuy.groupbuy.event.TeamSucceeded;
import com.blocbuy.groupbuy.gateway.OpenDealOfferCache;
import com.blocbuy.groupbuy.service.OpenDealOfferCacheSubscriber;
import com.blocbuy.order.repository.OrderRepository;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.service.TeamExpiryRefundService;
import com.blocbuy.payment.service.TeamExpiryRefundSubscriber;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pins ADR 0002's dispatch of the team-resolution events to the order, refund and offer-cache
 * subscribers: orders ({@code @Order(10)}) before refund obligations ({@code @Order(20)}), both
 * before commit, and cache eviction after commit. The subscribers' own unit tests call the handlers
 * directly; here the events go through Spring's {@code @TransactionalEventListener} machinery, and
 * one journal records the subscribers' calls, the end of the publishing code and the commit or
 * rollback. A changed {@code @Order} or phase therefore changes the recorded sequence.
 *
 * <p>Not covered: {@code IntegrationOutboxWriter} lives in the infrastructure module, and a new
 * listener added elsewhere is not detected. {@code BestOpenDealCacheIT} checks the cache against
 * real Redis and PostgreSQL; this test needs neither.
 */
class TeamResolutionListenerOrderTest {

    private static final UUID DEAL = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final UUID MEMBER = UUID.fromString("a0000000-0000-7000-8000-000000000003");
    private static final UUID PAYMENT = UUID.fromString("a0000000-0000-7000-8000-000000000004");
    private static final Instant AT = Instant.parse("2026-10-22T10:00:00Z");

    private final List<String> journal = new ArrayList<>();
    private final OrderRepository orders = mock(OrderRepository.class);
    private final TeamExpiryRefundService refunds = mock(TeamExpiryRefundService.class);
    private final OpenDealOfferCache cache = mock(OpenDealOfferCache.class);

    private AnnotationConfigApplicationContext context;
    private TransactionTemplate transaction;

    @BeforeEach
    void startContext() {
        doAnswer(call -> journal.add("cancel orders") ? 1 : 0)
                .when(orders)
                .cancelByDealId(DEAL, AT);
        doAnswer(call -> journal.add("confirm orders") ? 1 : 0).when(orders).confirmByDealId(DEAL);
        doAnswer(call -> journal.add("record refunds")).when(refunds).recordExpiredTeam(DEAL);
        doAnswer(call -> journal.add("evict offer")).when(cache).evict(List.of(PRODUCT));

        context = new AnnotationConfigApplicationContext();
        context.register(TransactionalListeners.class);
        context.registerBean(
                PlatformTransactionManager.class, () -> new JournalTransactionManager(journal));
        // Registered against their @Order: with equal or missing @Order values the refund
        // subscriber
        // would run first, so only the annotations produce the expected sequence.
        context.registerBean(
                OpenDealOfferCacheSubscriber.class, () -> new OpenDealOfferCacheSubscriber(cache));
        context.registerBean(
                TeamExpiryRefundSubscriber.class, () -> new TeamExpiryRefundSubscriber(refunds));
        context.registerBean(
                OrderLifecycleSubscriber.class, () -> new OrderLifecycleSubscriber(orders));
        context.refresh();
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void expiryCancelsOrdersThenRecordsRefundsBeforeCommitAndEvictsAfter() {
        publishAndCommit(new TeamExpired(DEAL, PRODUCT, AT));

        assertThat(journal)
                .containsExactly(
                        "published", "cancel orders", "record refunds", "commit", "evict offer");
    }

    @Test
    void successConfirmsOrdersBeforeCommitAndEvictsAfter() {
        publishAndCommit(new TeamSucceeded(DEAL, PRODUCT, 2, AT));

        assertThat(journal).containsExactly("published", "confirm orders", "commit", "evict offer");
    }

    @Test
    void aFailureToRecordRefundsRollsTheExpiryBackWithoutEviction() {
        doAnswer(
                        call -> {
                            journal.add("record refunds");
                            throw new BizException(PaymentErrorCode.PAYMENT_STATE_INVALID);
                        })
                .when(refunds)
                .recordExpiredTeam(DEAL);

        assertThatThrownBy(() -> publishAndCommit(new TeamExpired(DEAL, PRODUCT, AT)))
                .isInstanceOf(BizException.class);
        assertThat(journal)
                .containsExactly("published", "cancel orders", "record refunds", "rollback");
    }

    @Test
    void aJoinEvictsTheOfferAfterCommitAndNotAfterRollback() {
        MemberJoined joined = new MemberJoined(DEAL, PRODUCT, MEMBER, PAYMENT, null, AT);

        transaction.executeWithoutResult(
                status -> {
                    context.publishEvent(joined);
                    status.setRollbackOnly();
                });
        assertThat(journal).containsExactly("rollback");

        journal.clear();
        publishAndCommit(joined);
        assertThat(journal).containsExactly("published", "commit", "evict offer");
    }

    @Test
    void withoutATransactionNoSubscriberRuns() {
        // A plain @EventListener would run here at once, before the publisher's later writes (ADR
        // 0002).
        context.publishEvent(new TeamExpired(DEAL, PRODUCT, AT));
        context.publishEvent(new TeamSucceeded(DEAL, PRODUCT, 2, AT));

        assertThat(journal).isEmpty();
    }

    private void publishAndCommit(Object event) {
        transaction.executeWithoutResult(
                status -> {
                    context.publishEvent(event);
                    journal.add("published");
                });
    }

    @EnableTransactionManagement
    static class TransactionalListeners {}

    /** Holds no resource; records commit and rollback and drives transaction synchronization. */
    private static final class JournalTransactionManager
            extends AbstractPlatformTransactionManager {

        private final List<String> journal;

        JournalTransactionManager(List<String> journal) {
            this.journal = journal;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            journal.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            journal.add("rollback");
        }
    }
}
