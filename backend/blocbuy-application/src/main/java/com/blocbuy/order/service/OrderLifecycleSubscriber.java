package com.blocbuy.order.service;

import com.blocbuy.groupbuy.event.TeamExpired;
import com.blocbuy.groupbuy.event.TeamSucceeded;
import com.blocbuy.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Carries a team's resolution into the orders of record. Subscribes to groupbuy's published facts
 * rather than being called inline, so neither domain reaches into the other's aggregates: the
 * events are immutable records, and this class touches only its own repository port.
 *
 * <p><b>Why {@code BEFORE_COMMIT} and not {@code AFTER_COMMIT}.</b> {@link TeamSucceeded} is
 * published while the buyer who completed the team is still being settled — their own order row is
 * saved by the fulfilment path <em>after</em> the join that filled the team. A plain
 * {@code @EventListener} would run {@code confirmByDealId} at that moment and miss it, leaving that
 * buyer's order pending forever. {@code BEFORE_COMMIT} defers until every write in the transaction
 * is done but the transaction is still open, so the ordering is correct by construction and a
 * failure here rolls the team's resolution back with it.
 *
 * <p>Both handlers are status-guarded sweeps in SQL, so an event replay affects zero rows and is
 * harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderLifecycleSubscriber {

    private final OrderRepository orders;

    /** Runs before the payment domain's refund subscriber: team → intent → order → payment. */
    @Order(10)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTeamSucceeded(TeamSucceeded event) {
        int confirmed = orders.confirmByDealId(event.dealId());
        log.info("Team {} succeeded; confirmed {} pending orders", event.dealId(), confirmed);
    }

    /** The cancel instant is the team's resolution time, not this subscriber's own clock. */
    @Order(10)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTeamExpired(TeamExpired event) {
        int cancelled = orders.cancelByDealId(event.dealId(), event.at());
        log.info("Team {} expired; cancelled {} pending orders", event.dealId(), cancelled);
    }
}
