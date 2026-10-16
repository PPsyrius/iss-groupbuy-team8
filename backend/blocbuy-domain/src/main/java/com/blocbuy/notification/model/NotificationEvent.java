package com.blocbuy.notification.model;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.notification.exception.NotificationErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A supported integration event translated into notification terms (backend ADR 0007). Commerce
 * resolves recipients before publishing, so notification never looks them up. Recipient lists are
 * de-duplicated in their original order; an empty list is valid and produces no notification.
 */
public sealed interface NotificationEvent
        permits NotificationEvent.TeamSucceeded,
                NotificationEvent.TeamExpired,
                NotificationEvent.PaymentSucceeded {

    /** Integration envelope eventId: the idempotency key, never an SQS MessageId. */
    UUID eventId();

    NotificationType type();

    /** Team ID for team events, payment ID for payment events. */
    UUID referenceId();

    List<UUID> recipientUserIds();

    /** {@code TeamSucceeded} v1: the team reached its target size. */
    record TeamSucceeded(UUID eventId, UUID teamId, int teamSize, List<UUID> recipientUserIds)
            implements NotificationEvent {
        public TeamSucceeded {
            require(eventId != null && teamId != null && teamSize >= 2);
            recipientUserIds = List.copyOf(distinct(recipientUserIds));
        }

        @Override
        public NotificationType type() {
            return NotificationType.TEAM_SUCCEEDED;
        }

        @Override
        public UUID referenceId() {
            return teamId;
        }
    }

    /** {@code TeamExpired} v1: the team closed before reaching its target size. */
    record TeamExpired(UUID eventId, UUID teamId, List<UUID> recipientUserIds)
            implements NotificationEvent {
        public TeamExpired {
            require(eventId != null && teamId != null);
            recipientUserIds = List.copyOf(distinct(recipientUserIds));
        }

        @Override
        public NotificationType type() {
            return NotificationType.TEAM_EXPIRED;
        }

        @Override
        public UUID referenceId() {
            return teamId;
        }
    }

    /** {@code PaymentSucceeded} v1 for one payer; {@code startedTeam} is the START operation. */
    record PaymentSucceeded(UUID eventId, UUID paymentId, UUID recipientUserId, boolean startedTeam)
            implements NotificationEvent {
        public PaymentSucceeded {
            require(eventId != null && paymentId != null && recipientUserId != null);
        }

        @Override
        public NotificationType type() {
            return NotificationType.PAYMENT_SUCCEEDED;
        }

        @Override
        public UUID referenceId() {
            return paymentId;
        }

        @Override
        public List<UUID> recipientUserIds() {
            return List.of(recipientUserId);
        }
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new BizException(NotificationErrorCode.NOTIFICATION_INVALID);
        }
    }

    private static List<UUID> distinct(List<UUID> userIds) {
        if (userIds == null || userIds.stream().anyMatch(Objects::isNull)) {
            throw new BizException(NotificationErrorCode.NOTIFICATION_INVALID);
        }
        return new ArrayList<>(new LinkedHashSet<>(userIds));
    }
}
