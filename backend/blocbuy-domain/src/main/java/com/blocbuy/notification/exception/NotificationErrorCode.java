package com.blocbuy.notification.exception;

import com.blocbuy.common.exception.ErrorCode;

/**
 * Notification domain error codes. Once published, codes are never reused or redefined; messages
 * may be localized via {@link #messageKey()}, codes may not.
 */
public enum NotificationErrorCode implements ErrorCode {
    NOTIFICATION_PLACEHOLDER(
            "NOTIFICATION_404_001", 404, "Notification not found", "error.notification.notFound"),
    NOTIFICATION_INVALID(
            "NOTIFICATION_400_001", 400, "Invalid notification", "error.notification.invalid");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;
    private final String messageKey;

    NotificationErrorCode(String code, int httpStatus, String defaultMessage, String messageKey) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
        this.messageKey = messageKey;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }
}
