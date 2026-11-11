package com.blocbuy.notification.assembler;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.model.NotificationEvent;
import com.blocbuy.notification.model.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationEventAssemblerTest {
    private static final Instant NOW = Instant.parse("2026-10-16T08:00:00Z");
    private static final UUID EVENT = UUID.fromString("01990000-0000-7000-8000-000000000001");
    private static final UUID REF = UUID.fromString("01990000-0000-7000-8000-000000000002");
    private static final UUID A = UUID.fromString("01990000-0000-7000-8000-00000000000a");
    private static final UUID B = UUID.fromString("01990000-0000-7000-8000-00000000000b");

    @Test
    void teamSucceededRendersOneNotificationPerRecipientWithTheTeamSize() {
        var rows =
                NotificationEventAssembler.toNotifications(
                        new NotificationEvent.TeamSucceeded(EVENT, REF, 4, List.of(A, B)), NOW);
        assertThat(rows).extracting(Notification::userId).containsExactly(A, B);
        assertThat(rows)
                .allSatisfy(row -> {
                    assertThat(row.eventId()).isEqualTo(EVENT);
                    assertThat(row.referenceId()).isEqualTo(REF);
                    assertThat(row.type()).isEqualTo(NotificationType.TEAM_SUCCEEDED);
                    assertThat(row.title()).isEqualTo("Your team is complete");
                    assertThat(row.content())
                            .isEqualTo(
                                    "Your group-buy team reached its target size of 4"
                                            + " members.");
                    assertThat(row.createTime()).isEqualTo(NOW);
                });
        assertThat(rows.get(0).id()).isNotEqualTo(rows.get(1).id());
    }

    @Test
    void teamExpiredAndPaymentVariantsUseTheirOwnText() {
        var expired =
                NotificationEventAssembler.toNotifications(
                        new NotificationEvent.TeamExpired(EVENT, REF, List.of(A)), NOW);
        assertThat(expired)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.type()).isEqualTo(NotificationType.TEAM_EXPIRED);
                    assertThat(row.title())
                            .isEqualTo(NotificationEventAssembler.TEAM_EXPIRED_TITLE);
                    assertThat(row.content())
                            .isEqualTo(NotificationEventAssembler.TEAM_EXPIRED_CONTENT);
                });
        var started =
                NotificationEventAssembler.toNotifications(
                        new NotificationEvent.PaymentSucceeded(EVENT, REF, A, true), NOW);
        var joined =
                NotificationEventAssembler.toNotifications(
                        new NotificationEvent.PaymentSucceeded(EVENT, REF, B, false), NOW);
        assertThat(started)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.userId()).isEqualTo(A);
                    assertThat(row.type()).isEqualTo(NotificationType.PAYMENT_SUCCEEDED);
                    assertThat(row.title()).isEqualTo("Payment confirmed");
                    assertThat(row.content())
                            .isEqualTo(NotificationEventAssembler.PAYMENT_START_CONTENT);
                });
        assertThat(joined)
                .singleElement()
                .satisfies(row ->
                        assertThat(row.content())
                                .isEqualTo(NotificationEventAssembler.PAYMENT_JOIN_CONTENT));
    }

    @Test
    void noRecipientsRendersNothing() {
        assertThat(
                        NotificationEventAssembler.toNotifications(
                                new NotificationEvent.TeamExpired(EVENT, REF, List.of()), NOW))
                .isEmpty();
    }
}
