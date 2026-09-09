package com.blocbuy.groupbuy.exception;

import com.blocbuy.common.exception.ErrorCode;

/**
 * Groupbuy domain error codes. Once published, codes are never reused or redefined; messages may be
 * localized via {@link #messageKey()}, codes may not.
 */
public enum GroupbuyErrorCode implements ErrorCode {
    GROUPBUY_PLACEHOLDER(
            "GROUPBUY_404_001", 404, "Group deal not found", "error.groupbuy.dealNotFound"),
    INVALID_PARTICIPATION(
            "GROUPBUY_400_001",
            400,
            "Invalid paid participation",
            "error.groupbuy.invalidParticipation"),
    PAYMENT_MISMATCH(
            "GROUPBUY_409_001",
            409,
            "Payment does not match deal price",
            "error.groupbuy.paymentMismatch"),
    DEAL_CLOSED(
            "GROUPBUY_409_002",
            409,
            "Group deal is not accepting members",
            "error.groupbuy.dealClosed"),
    ALREADY_MEMBER(
            "GROUPBUY_409_003", 409, "Buyer is already a member", "error.groupbuy.alreadyMember"),
    DEAL_FULL("GROUPBUY_409_004", 409, "Group deal is full", "error.groupbuy.dealFull");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;
    private final String messageKey;

    GroupbuyErrorCode(String code, int httpStatus, String defaultMessage, String messageKey) {
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
