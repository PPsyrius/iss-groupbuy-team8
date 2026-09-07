package com.blocbuy.common.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Single global exception entry point (hard gate). Controllers must not assemble error responses
 * themselves.
 *
 * <p>Body shape: the unified {@link Result} envelope — {@code code} + {@code message} from the
 * error-code enum, plus {@code errors} for field validation / safe details and {@code traceId}. The
 * HTTP status comes from the same enum; failures are never wrapped in HTTP 200. Never leaks SQL,
 * stack traces, internal paths, or raw third-party responses.
 *
 * <p>Logging policy: expected 4xx logged at WARN once here; unknown 5xx logged at ERROR once here.
 * Lower layers must not re-log the same exception.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Business rejections: status and code come from the domain's error-code enum. */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex) {
        ErrorCode errorCode = ex.errorCode();
        log.warn(
                "business rejection: code={}, detail={}",
                errorCode.code(),
                errorCode.defaultMessage());
        return toResponse(errorCode, ex.details().isEmpty() ? null : ex.details());
    }

    /** Bean Validation on @RequestBody: 400 with safe per-field errors. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldViolation> violations =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(GlobalExceptionHandler::toViolation)
                        .toList();
        return toResponse(CommonErrorCode.VALIDATION_FAILED, violations);
    }

    /** Bean Validation on @RequestParam/@PathVariable. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> violations =
                ex.getConstraintViolations().stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .toList();
        return toResponse(CommonErrorCode.VALIDATION_FAILED, violations);
    }

    /** Malformed JSON / unreadable body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        return toResponse(CommonErrorCode.BAD_REQUEST, null);
    }

    /** Sa-Token: not logged in → 401. */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException ex) {
        return toResponse(CommonErrorCode.UNAUTHENTICATED, null);
    }

    /** Sa-Token: logged in but lacks permission → 403. */
    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Result<Void>> handleNotPermission(NotPermissionException ex) {
        return toResponse(CommonErrorCode.FORBIDDEN, null);
    }

    /** Unknown path → 404 (Spring 6.1+). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex) {
        return toResponse(CommonErrorCode.NOT_FOUND, null);
    }

    /** Last resort: one ERROR log with traceId; client receives a generic 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        log.error("unhandled exception, traceId={}", MDC.get(ApiConstants.TRACE_ID_MDC_KEY), ex);
        return toResponse(CommonErrorCode.INTERNAL_ERROR, null);
    }

    /**
     * Error body + HTTP status from the same enum — {@link ResponseEntity} is used only here, at
     * the protocol boundary, to apply the status; controllers never assemble error responses.
     */
    private static ResponseEntity<Result<Void>> toResponse(ErrorCode errorCode, Object errors) {
        return ResponseEntity.status(errorCode.httpStatus()).body(Result.error(errorCode, errors));
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }

    /** Safe field-level validation error; never carries rejected values that may be sensitive. */
    public record FieldViolation(String field, String message) {}
}
