package com.blocbuy.payment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.event.TeamExpired;
import com.blocbuy.payment.exception.PaymentErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** An expired team owes its paid members money; recording that is part of the expiry itself. */
class TeamExpiryRefundSubscriberTest {

    private static final UUID DEAL = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final Instant AT = Instant.parse("2026-09-22T10:00:00Z");

    private final StripeCheckoutService payments = mock(StripeCheckoutService.class);
    private final TeamExpiryRefundSubscriber subscriber = new TeamExpiryRefundSubscriber(payments);

    @Test
    void expiryRecordsRefundObligationsForThatTeam() {
        subscriber.onTeamExpired(new TeamExpired(DEAL, PRODUCT, AT));

        verify(payments).recordExpiredTeam(DEAL);
    }

    /**
     * Failing to record the obligation must roll the expiry back with it. The subscriber therefore
     * lets the failure escape rather than swallowing it and leaving a closed team owing nobody.
     */
    @Test
    void aFailureToRecordObligationsEscapesAndRollsTheExpiryBack() {
        doThrow(new BizException(PaymentErrorCode.PAYMENT_STATE_INVALID))
                .when(payments)
                .recordExpiredTeam(DEAL);
        TeamExpired event = new TeamExpired(DEAL, PRODUCT, AT);

        assertThatThrownBy(() -> subscriber.onTeamExpired(event)).isInstanceOf(BizException.class);
    }
}
