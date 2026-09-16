package com.blocbuy.identity.exception;

import com.blocbuy.common.exception.ErrorCode;

/**
 * Identity domain error codes. Once published, codes are never reused or redefined; messages may be
 * localized via {@link #messageKey()}, codes may not.
 */
public enum IdentityErrorCode implements ErrorCode {
    USER_NOT_FOUND("IDENTITY_404_001", 404, "User not found", "error.identity.userNotFound"),
    OAUTH_STATE_INVALID(
            "IDENTITY_401_001",
            401,
            "OAuth state is invalid or expired",
            "error.identity.oauthStateInvalid"),
    OAUTH_TOKEN_INVALID(
            "IDENTITY_401_002",
            401,
            "Identity provider token verification failed",
            "error.identity.oauthTokenInvalid"),
    OAUTH_ACCESS_DENIED(
            "IDENTITY_401_003",
            401,
            "OAuth authorization was denied by the user",
            "error.identity.oauthAccessDenied"),
    ACCOUNT_DISABLED(
            "IDENTITY_403_001", 403, "Account is disabled", "error.identity.accountDisabled"),
    EMAIL_ALREADY_REGISTERED(
            "IDENTITY_409_001",
            409,
            "Email is already registered via another channel",
            "error.identity.emailAlreadyRegistered"),
    OAUTH_PROVIDER_ERROR(
            "IDENTITY_502_001",
            502,
            "Identity provider request failed",
            "error.identity.oauthProviderError"),
    IDENTITY_MISCONFIGURED(
            "IDENTITY_500_001",
            500,
            "Identity service is misconfigured",
            "error.identity.misconfigured");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;
    private final String messageKey;

    IdentityErrorCode(String code, int httpStatus, String defaultMessage, String messageKey) {
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
