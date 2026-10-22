package com.blocbuy.order.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pins how Spring dispatches the team-resolution observers, which the unit tests of each subscriber
 * cannot show: they call the handlers directly. Here the events go through the real
 * {@code @TransactionalEventListener} machinery, so the tests fail if an {@code @Order} or a
 * transaction phase changes.
 *
 * <ul>
 *   <li>Orders are cancelled before refund obligations are recorded, keeping the global lock order
 *       team, intent, order, payment ({@code @Order(10)} before {@code @Order(20)}).
 *   <li>Both run before commit, inside the expiry transaction: a failure to record the obligations
 *       rolls the expiry back, and nothing runs without a transaction.
 *   <li>The open-deal offer cache is evicted only after a commit, never after a rollback.
 * </ul>
 */
class TeamResolutionListenerOrderTest {

    private static final UUID DEAL = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final UUID MEMBER = UUID.fromString("a0000000-0000-7000-8000-000000000003");
    private static final UUID PAYMENT = UUID.fromString("a0000000-0000-7000-8000-000000000004");
    private static final Instant AT = Instant.parse("2026-10-22T10:00:00Z");

    private final OrderRepository orders = mock(OrderRepository.class);
    private final TeamExpiryRefundService refunds = mock(TeamExpiryRefundService.class);
    private final OpenDealOfferCache cache = mock(OpenDealOfferCache.class);

    private AnnotationConfigApplicationContext context;
    private ApplicationEventPublisher events;
    private TransactionTemplate transaction;

    @BeforeEach
    void startContext() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(OrderRepository.class, () -> orders);
        context.registerBean(TeamExpiryRefundService.class, () -> refunds);
        context.registerBean(OpenDealOfferCache.class, () -> cache);
        context.register(Listeners.class);
        context.refresh();
        events = context;
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void expiryCancelsOrdersBeforeRecordingRefundObligations() {
        transaction.executeWithoutResult(status -> events.publishEvent(expired()));

        InOrder order = inOrder(orders, refunds);
        order.verify(orders).cancelByDealId(DEAL, AT);
        order.verify(refunds).recordExpiredTeam(DEAL);
    }

    @Test
    void aFailedRefundRecordRollsTheExpiryBackAndKeepsTheCacheEntry() {
        doThrow(new BizException(PaymentErrorCode.PAYMENT_STATE_INVALID))
                .when(refunds)
                .recordExpiredTeam(DEAL);

        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> events.publishEvent(expired())))
                .isInstanceOf(BizException.class);
        verify(orders).cancelByDealId(DEAL, AT);
        verify(cache, never()).evict(any());
    }

    @Test
    void resolutionListenersDoNotRunWithoutATransaction() {
        events.publishEvent(expired());
        events.publishEvent(new TeamSucceeded(DEAL, PRODUCT, 2, AT));

        verifyNoInteractions(orders, refunds, cache);
    }

    @Test
    void successConfirmsOrdersBeforeCommitAndThenEvictsTheOffer() {
        transaction.executeWithoutResult(
                status -> events.publishEvent(new TeamSucceeded(DEAL, PRODUCT, 2, AT)));

        InOrder order = inOrder(orders, cache);
        order.verify(orders).confirmByDealId(DEAL);
        order.verify(cache).evict(List.of(PRODUCT));
        verifyNoInteractions(refunds);
    }

    @Test
    void aJoinEvictsTheOfferOnlyAfterCommit() {
        MemberJoined joined = new MemberJoined(DEAL, PRODUCT, MEMBER, PAYMENT, null, AT);

        transaction.executeWithoutResult(
                status -> {
                    events.publishEvent(joined);
                    status.setRollbackOnly();
                });
        verify(cache, never()).evict(any());

        transaction.executeWithoutResult(status -> events.publishEvent(joined));
        verify(cache).evict(List.of(PRODUCT));
    }

    private static TeamExpired expired() {
        return new TeamExpired(DEAL, PRODUCT, AT);
    }

    /** The three subscribers as the services wire them, behind a transaction manager. */
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Listeners {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new SynchronizationOnlyTransactionManager();
        }

        @Bean
        OrderLifecycleSubscriber orderLifecycleSubscriber(OrderRepository orders) {
            return new OrderLifecycleSubscriber(orders);
        }

        @Bean
        TeamExpiryRefundSubscriber teamExpiryRefundSubscriber(TeamExpiryRefundService refunds) {
            return new TeamExpiryRefundSubscriber(refunds);
        }

        @Bean
        OpenDealOfferCacheSubscriber openDealOfferCacheSubscriber(OpenDealOfferCache cache) {
            return new OpenDealOfferCacheSubscriber(cache);
        }
    }

    /**
     * Holds no resource: it only drives transaction synchronization, which is all the transactional
     * event listeners need to see before-commit, after-commit and rollback.
     */
    private static final class SynchronizationOnlyTransactionManager
            extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // nothing to begin
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // nothing to commit
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // nothing to roll back
        }
    }
}
