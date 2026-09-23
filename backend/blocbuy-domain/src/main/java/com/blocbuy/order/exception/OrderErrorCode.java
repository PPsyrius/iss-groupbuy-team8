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
            "error.order.versionConflict"),
    PURCHASE_INTENT_NOT_FOUND(
            "ORDER_404_002",
            404,
            "Purchase intent not found",
            "error.order.purchaseIntentNotFound"),
    PURCHASE_INTENT_TERMS_INVALID(
            "ORDER_400_001",
            400,
            "Purchase intent terms are not valid",
            "error.order.purchaseIntentTermsInvalid"),
    // ORDER_409_004 was briefly reserved for a "team not joinable" code. Joinability is groupbuy's
    // rule, raised by its own facade as GROUPBUY_409_002/003/004, so this domain never owned it.
    // The number stays retired rather than reused.
    PURCHASE_INTENT_ALREADY_FULFILLED(
            "ORDER_409_003",
            409,
            "Purchase intent was already fulfilled by another payment",
            "error.order.purchaseIntentAlreadyFulfilled");

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
