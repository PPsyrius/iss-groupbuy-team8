package com.blocbuy.common.exception;

/**
 * Cross-domain status codes. {@link #SUCCESS} backs the {@code Result} envelope's success factory;
 * the rest are failures. Domain-specific codes live in each domain's own {@code exception} package;
 * codes once published are never reused or redefined.
 */
public enum CommonErrorCode implements ErrorCode {
    SUCCESS("COMMON_200_000", 200, "Success", "result.common.success"),
    BAD_REQUEST("COMMON_400_001", 400, "Invalid request", "error.common.badRequest"),
    VALIDATION_FAILED(
            "COMMON_400_002", 400, "Request validation failed", "error.common.validationFailed"),
    UNAUTHENTICATED(
            "COMMON_401_001", 401, "Authentication required", "error.common.unauthenticated"),
    FORBIDDEN("COMMON_403_001", 403, "Permission denied", "error.common.forbidden"),
    NOT_FOUND("COMMON_404_001", 404, "Resource not found", "error.common.notFound"),
    STATE_CONFLICT("COMMON_409_001", 409, "State conflict", "error.common.stateConflict"),
    PRECONDITION_FAILED(
            "COMMON_412_001",
            412,
            "Concurrency precondition failed",
            "error.common.preconditionFailed"),
    RATE_LIMITED("COMMON_429_001", 429, "Too many requests", "error.common.rateLimited"),
    INTERNAL_ERROR("COMMON_500_001", 500, "Internal server error", "error.common.internalError");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;
    private final String messageKey;

    CommonErrorCode(String code, int httpStatus, String defaultMessage, String messageKey) {
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
