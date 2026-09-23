package com.blocbuy.order.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.event.TeamExpired;
import com.blocbuy.groupbuy.event.TeamSucceeded;
import com.blocbuy.order.repository.OrderRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A team's resolution reaches the orders of record through one status-guarded sweep each. */
@ExtendWith(MockitoExtension.class)
class OrderLifecycleSubscriberTest {

    private static final UUID DEAL = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final Instant AT = Instant.parse("2026-09-22T10:00:00Z");

    @Mock private OrderRepository orders;
    @InjectMocks private OrderLifecycleSubscriber subscriber;

    @Test
    void successConfirmsEveryPendingOrderOfThatTeamAndNothingElse() {
        when(orders.confirmByDealId(DEAL)).thenReturn(2);

        subscriber.onTeamSucceeded(new TeamSucceeded(DEAL, PRODUCT, 2, AT));

        verify(orders).confirmByDealId(DEAL);
        verifyNoMoreInteractions(orders);
    }

    /** The cancel instant is the team's resolution time, not a clock read inside the subscriber. */
    @Test
    void expiryCancelsPendingOrdersStampedWithTheTeamsResolutionTime() {
        when(orders.cancelByDealId(DEAL, AT)).thenReturn(1);

        subscriber.onTeamExpired(new TeamExpired(DEAL, PRODUCT, AT));

        verify(orders).cancelByDealId(DEAL, AT);
        verifyNoMoreInteractions(orders);
    }

    /** A replayed event sweeps zero rows; the subscriber must treat that as an ordinary outcome. */
    @Test
    void replayedEventsAffectNoRowsAndStillSucceed() {
        when(orders.confirmByDealId(DEAL)).thenReturn(0);
        when(orders.cancelByDealId(DEAL, AT)).thenReturn(0);

        subscriber.onTeamSucceeded(new TeamSucceeded(DEAL, PRODUCT, 2, AT));
        subscriber.onTeamExpired(new TeamExpired(DEAL, PRODUCT, AT));

        verify(orders).confirmByDealId(DEAL);
        verify(orders).cancelByDealId(DEAL, AT);
    }
}
