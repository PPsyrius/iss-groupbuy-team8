package com.blocbuy.order.exception;

import com.blocbuy.common.exception.ErrorCode;

/**
 * Order domain error codes. Once published, codes are never reused or redefined; messages may be
 * localized via {@link #messageKey()}, codes may not.
 */
public enum OrderErrorCode implements ErrorCode {
    ORDER_PLACEHOLDER("ORDER_404_001", 404, "Order not found", "error.order.notFound");

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
