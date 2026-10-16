package com.blocbuy.notification.assembler;

import com.blocbuy.common.util.UuidV7;
import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.model.NotificationEvent;
import com.blocbuy.notification.model.NotificationEvent.PaymentSucceeded;
import com.blocbuy.notification.model.NotificationEvent.TeamExpired;
import com.blocbuy.notification.model.NotificationEvent.TeamSucceeded;
import java.time.Instant;
import java.util.List;

/**
 * Renders one immutable notification per recipient. Text is fixed, short and plain (the client
 * renders it as text, never HTML); it carries no names, amounts or other personal data.
 */
public final class NotificationEventAssembler {
    static final String TEAM_SUCCEEDED_TITLE = "Your team is complete";
    static final String TEAM_EXPIRED_TITLE = "Your team has expired";
    static final String TEAM_EXPIRED_CONTENT =
            "Your group-buy team closed before it reached its target size.";
    static final String PAYMENT_TITLE = "Payment confirmed";
    static final String PAYMENT_START_CONTENT =
            "Your payment was confirmed and your group-buy team has started.";
    static final String PAYMENT_JOIN_CONTENT =
            "Your payment was confirmed and you have joined the group-buy team.";

    private NotificationEventAssembler() {}

    /** Fresh UUIDv7 row IDs; the (eventId, userId) key makes replays no-ops regardless. */
    public static List<Notification> toNotifications(NotificationEvent event, Instant now) {
        String title =
                switch (event) {
                    case TeamSucceeded ignored -> TEAM_SUCCEEDED_TITLE;
                    case TeamExpired ignored -> TEAM_EXPIRED_TITLE;
                    case PaymentSucceeded ignored -> PAYMENT_TITLE;
                };
        String content =
                switch (event) {
                    case TeamSucceeded team ->
                            "Your group-buy team reached its target size of "
                                    + team.teamSize()
                                    + " members.";
                    case TeamExpired ignored -> TEAM_EXPIRED_CONTENT;
                    case PaymentSucceeded payment ->
                            payment.startedTeam() ? PAYMENT_START_CONTENT : PAYMENT_JOIN_CONTENT;
                };
        return event.recipientUserIds().stream()
                .map(
                        userId ->
                                new Notification(
                                        UuidV7.next(),
                                        event.eventId(),
                                        userId,
                                        event.type(),
                                        event.referenceId(),
                                        title,
                                        content,
                                        now))
                .toList();
    }
}
