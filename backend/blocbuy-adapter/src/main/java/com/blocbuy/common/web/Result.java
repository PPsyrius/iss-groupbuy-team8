package com.blocbuy.common.web;

import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

/**
 * Unified API response envelope (hard gate). Every JSON endpoint returns this shape: {@code code}
 * (stable machine code from an {@link ErrorCode}), {@code data} (payload, absent on errors), {@code
 * message} (English default, i18n by the code's message key later). {@code errors} carries safe
 * structured detail (e.g. field violations) and {@code traceId} correlates with server logs; both
 * appear only when present.
 *
 * <p>The HTTP status still reflects the outcome (no 200-wrapped failures); it comes from the same
 * {@link ErrorCode} in the global exception handler, never from controllers.
 *
 * <p>Exemptions: endpoints that are protocol handshakes rather than JSON resources (302 OAuth
 * redirects) may return headers only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Result<T>(String code, T data, String message, Object errors, String traceId) {

    /** Success with payload; code and message come from {@link CommonErrorCode#SUCCESS}. */
    public static <T> Result<T> ok(T data) {
        return new Result<>(
                CommonErrorCode.SUCCESS.code(),
                data,
                CommonErrorCode.SUCCESS.defaultMessage(),
                null,
                null);
    }

    /** Success without payload (e.g. logout, webhook acknowledgement). */
    public static Result<Void> ok() {
        return ok(null);
    }

    /**
     * Failure from an error-code enum; callers assemble it only in the global exception handler.
     */
    public static Result<Void> error(ErrorCode errorCode) {
        return error(errorCode, null);
    }

    /**
     * Failure with safe structured detail, e.g. field violations or {@code BizException} details.
     */
    public static Result<Void> error(ErrorCode errorCode, Object errors) {
        if (errorCode == null) {
            throw new IllegalArgumentException("errorCode must not be null");
        }
        return new Result<>(
                errorCode.code(),
                null,
                errorCode.defaultMessage(),
                errors,
                MDC.get(ApiConstants.TRACE_ID_MDC_KEY));
    }
}
