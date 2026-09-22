package com.blocbuy.payment.service;

import com.blocbuy.groupbuy.event.TeamExpired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns an expired team into refund obligations for the buyers who already paid into it.
 *
 * <p>{@code BEFORE_COMMIT} is mandatory here, not a preference: {@code recordExpiredTeam} states it
 * must run inside the caller's team-expiry transaction. The team's closure and the money owed for
 * it are one fact — recorded after the commit, they would leave a window in which the team is
 * expired and nobody is owed anything, and a failure to record would no longer undo the expiry.
 *
 * <p>Ordered after {@link com.blocbuy.order.service.OrderLifecycleSubscriber} so the locks are
 * taken in the global order team → intent → order → payment.
 *
 * <p>Registered only with the checkout profile: where payments are switched off there is no money
 * in the team and therefore no refund obligation, while order cancellation still happens as usual.
 */
@Slf4j
@RequiredArgsConstructor
public class TeamExpiryRefundSubscriber {

    private final StripeCheckoutService payments;

    /** Obligations only; the worker sends the money later, outside this transaction. */
    @Order(20)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTeamExpired(TeamExpired event) {
        payments.recordExpiredTeam(event.dealId());
        log.info(
                "Team {} expired; refund obligations recorded for its paid members",
                event.dealId());
    }
}
