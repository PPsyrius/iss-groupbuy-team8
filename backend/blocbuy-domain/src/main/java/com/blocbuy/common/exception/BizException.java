package com.blocbuy.common.exception;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Expected business rejection. Constructible only from an {@link ErrorCode} plus optional safe
 * structured details — free-form strings and bare runtime exceptions are forbidden for business
 * failures.
 *
 * <p>System failures (DB down, serialization errors, NPEs) are NOT represented by this type; let
 * them propagate to the global handler as unknown exceptions.
 */
public final class BizException extends RuntimeException {

    @java.io.Serial private static final long serialVersionUID = 1L;

    // transient: ErrorCode/Map implementations are not guaranteed Serializable, and exceptions are
    // not persisted or remoted in this codebase — identity matters only within the request
    private final transient ErrorCode errorCode;
    private final transient Map<String, Object> details;
    private final transient List<FieldViolation> fieldViolations;

    public BizException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public BizException(ErrorCode errorCode, Map<String, Object> details) {
        this(errorCode, details, List.of());
    }

    private BizException(
            ErrorCode errorCode,
            Map<String, Object> details,
            List<FieldViolation> fieldViolations) {
        super(toMessage(errorCode));
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Collections.unmodifiableMap(details);
        this.fieldViolations = List.copyOf(fieldViolations);
    }

    /** A request-field rejection with a safe, admin-facing message and no rejected value. */
    public static BizException forField(ErrorCode errorCode, String field, String message) {
        return new BizException(errorCode, Map.of(), List.of(new FieldViolation(field, message)));
    }

    /** Adapt field paths when a use case embeds another command in its request. */
    public BizException withFieldPrefix(String prefix) {
        if (fieldViolations.isEmpty()) {
            return this;
        }
        return new BizException(
                errorCode,
                details,
                fieldViolations.stream()
                        .map(v -> new FieldViolation(prefix + v.field(), v.message()))
                        .toList());
    }

    public List<FieldViolation> fieldViolations() {
        return fieldViolations;
    }

    /** JSON request path and safe message, matching Bean Validation field errors. */
    public record FieldViolation(String field, String message) {}

    private static String toMessage(ErrorCode errorCode) {
        if (errorCode == null) {
            throw new IllegalArgumentException("errorCode must not be null");
        }
        return errorCode.code() + ": " + errorCode.defaultMessage();
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** Safe structured details for the error body; must never contain sensitive data. */
    public Map<String, Object> details() {
        return details;
    }
}
