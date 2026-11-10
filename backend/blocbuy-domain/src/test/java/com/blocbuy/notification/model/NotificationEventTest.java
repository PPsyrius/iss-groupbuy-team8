package com.blocbuy.notification.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationEventTest {
    private static final UUID EVENT = UUID.fromString("01990000-0000-7000-8000-000000000001");
    private static final UUID REF = UUID.fromString("01990000-0000-7000-8000-000000000002");
    private static final UUID A = UUID.fromString("01990000-0000-7000-8000-00000000000a");
    private static final UUID B = UUID.fromString("01990000-0000-7000-8000-00000000000b");

    @Test
    void teamEventsReferenceTheTeamAndDeduplicateRecipientsInOrder() {
        var succeeded = new NotificationEvent.TeamSucceeded(EVENT, REF, 3, List.of(B, A, B));
        assertThat(succeeded.type()).isEqualTo(NotificationType.TEAM_SUCCEEDED);
        assertThat(succeeded.referenceId()).isEqualTo(REF);
        assertThat(succeeded.recipientUserIds()).containsExactly(B, A);

        var expired = new NotificationEvent.TeamExpired(EVENT, REF, List.of());
        assertThat(expired.type()).isEqualTo(NotificationType.TEAM_EXPIRED);
        assertThat(expired.referenceId()).isEqualTo(REF);
        assertThat(expired.recipientUserIds()).isEmpty();
    }

    @Test
    void paymentEventHasExactlyOneRecipientAndReferencesThePayment() {
        var payment = new NotificationEvent.PaymentSucceeded(EVENT, REF, A, true);
        assertThat(payment.type()).isEqualTo(NotificationType.PAYMENT_SUCCEEDED);
        assertThat(payment.referenceId()).isEqualTo(REF);
        assertThat(payment.recipientUserIds()).containsExactly(A);
        assertThat(payment.startedTeam()).isTrue();
    }

    @Test
    void rejectsMissingIdentitiesInvalidSizeAndNullRecipients() {
        List<UUID> withNull = Arrays.asList(A, null);
        List<Runnable> invalid =
                List.of(
                        () -> new NotificationEvent.TeamSucceeded(null, REF, 2, List.of(A)),
                        () -> new NotificationEvent.TeamSucceeded(EVENT, null, 2, List.of(A)),
                        () -> new NotificationEvent.TeamSucceeded(EVENT, REF, 1, List.of(A)),
                        () -> new NotificationEvent.TeamSucceeded(EVENT, REF, 2, null),
                        () -> new NotificationEvent.TeamSucceeded(EVENT, REF, 2, withNull),
                        () -> new NotificationEvent.TeamExpired(null, REF, List.of(A)),
                        () -> new NotificationEvent.TeamExpired(EVENT, null, List.of(A)),
                        () -> new NotificationEvent.TeamExpired(EVENT, REF, withNull),
                        () -> new NotificationEvent.PaymentSucceeded(null, REF, A, false),
                        () -> new NotificationEvent.PaymentSucceeded(EVENT, null, A, false),
                        () -> new NotificationEvent.PaymentSucceeded(EVENT, REF, null, false));
        invalid.forEach(construct ->
                assertThatThrownBy(construct::run)
                        .isInstanceOf(BizException.class)
                        .hasMessageContaining("NOTIFICATION_400_001"));
    }
}
