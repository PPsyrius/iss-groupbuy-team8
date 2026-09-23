package com.blocbuy.order.exception;

import com.blocbuy.common.exception.ErrorCode;

/**
 * Order domain error codes. Once published, codes are never reused or redefined; messages may be
 * localized via {@link #messageKey()}, codes may not.
 */
public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND("ORDER_404_001", 404, "Order not found", "error.order.notFound"),
    ORDER_STATE_INVALID(
            "ORDER_409_001",
            409,
            "Order is in a state that does not allow this operation",
            "error.order.stateInvalid"),
    ORDER_VERSION_CONFLICT(
            "ORDER_409_002",
            409,
            "Order was modified concurrently; retry with the latest version",
            "error.order.versionConflict");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;
    private final String messageKey;

    OrderErrorCode(String code, int httpStatus, String defaultMessage, String messageKey) {
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
