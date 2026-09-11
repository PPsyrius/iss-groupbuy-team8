package com.blocbuy.groupbuy.exception;

import com.blocbuy.common.exception.ErrorCode;

/** Stable errors for paid-application idempotency, separate from deal transitions. */
public enum ParticipationErrorCode implements ErrorCode {
    PAYMENT_REUSED;

    /** Identifies reuse of a payment for a different participation request. */
    @Override
    public String code() {
        return "GROUPBUY_409_005";
    }

    /** Classifies changed-intent payment reuse as a conflict for HTTP adapters. */
    @Override
    public int httpStatus() {
        return 409;
    }

    /** Explains that the payment is already bound to another participation intent. */
    @Override
    public String defaultMessage() {
        return "Payment was already bound to a different participation";
    }

    /** Returns the translation key for changed-intent payment reuse. */
    @Override
    public String messageKey() {
        return "error.groupbuy.paymentReused";
    }
}
