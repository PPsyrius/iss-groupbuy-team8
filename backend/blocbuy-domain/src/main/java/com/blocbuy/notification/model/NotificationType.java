package com.blocbuy.notification.model;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.notification.exception.NotificationErrorCode;
import java.util.Arrays;

/** Event names agreed in #450; the consumer in #446 owns payload-to-notification rendering. */
public enum NotificationType {
    TEAM_SUCCEEDED("TeamSucceeded"),
    TEAM_EXPIRED("TeamExpired"),
    PAYMENT_SUCCEEDED("PaymentSucceeded");

    private final String code;

    NotificationType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static NotificationType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new BizException(NotificationErrorCode.NOTIFICATION_INVALID));
    }
}
