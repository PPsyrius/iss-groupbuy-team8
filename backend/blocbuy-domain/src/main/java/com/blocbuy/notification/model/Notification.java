package com.blocbuy.notification.model;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.notification.exception.NotificationErrorCode;
import java.time.Instant;
import java.util.UUID;

/** Immutable in-app notification; one effect per event and recipient, no read-state mutation. */
public record Notification(
        UUID id,
        UUID eventId,
        UUID userId,
        NotificationType type,
        UUID referenceId,
        String title,
        String content,
        Instant createTime) {
    public Notification {
        if (id == null
                || eventId == null
                || userId == null
                || type == null
                || referenceId == null
                || createTime == null
                || !validText(title, 128)
                || !validText(content, 1024)) {
            throw new BizException(NotificationErrorCode.NOTIFICATION_INVALID);
        }
    }

    private static boolean validText(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum;
    }
}
