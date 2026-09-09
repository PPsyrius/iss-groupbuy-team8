package com.blocbuy.groupbuy.exception;

import com.blocbuy.common.exception.ErrorCode;

/** Stable errors for paid-application idempotency, separate from deal transitions. */
public enum ParticipationErrorCode implements ErrorCode {
    PAYMENT_REUSED;

    @Override
    public String code() {
        return "GROUPBUY_409_005";
    }

    @Override
    public int httpStatus() {
        return 409;
    }

    @Override
    public String defaultMessage() {
        return "Payment was already bound to a different participation";
    }

    @Override
    public String messageKey() {
        return "error.groupbuy.paymentReused";
    }
}
