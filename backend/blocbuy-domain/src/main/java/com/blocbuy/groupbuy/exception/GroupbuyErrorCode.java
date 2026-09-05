package com.blocbuy.groupbuy.exception;

import com.blocbuy.common.exception.ErrorCode;

/**
 * Groupbuy domain error codes. Once published, codes are never reused or redefined; messages may be
 * localized via {@link #messageKey()}, codes may not.
 */
public enum GroupbuyErrorCode implements ErrorCode {
    GROUPBUY_PLACEHOLDER(
            "GROUPBUY_404_001", 404, "Group deal not found", "error.groupbuy.dealNotFound");

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
