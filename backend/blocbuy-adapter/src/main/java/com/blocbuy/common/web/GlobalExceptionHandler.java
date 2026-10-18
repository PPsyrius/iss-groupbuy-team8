package com.blocbuy.common.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
 * <p>Every error body is written as {@code application/json} whatever the caller's {@code Accept}
 * says, so the envelope survives clients that do not accept JSON.
 *
 * <p>Logging policy: business rejections ({@link BizException}) are logged at WARN once here;
 * unknown 5xx are logged at ERROR once here. The other mapped 4xx (validation, bad input,
 * authentication, permission, not found, 405/406/415) are not logged, to keep scanner and
 * malformed-request noise out of the logs. Lower layers must not re-log the same exception.
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
        if (!ex.fieldViolations().isEmpty()) {
            return toResponse(errorCode, ex.fieldViolations());
        }
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

    /** Path/query param that failed type conversion, e.g. a non-UUID {id} → 400, never 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return toResponse(CommonErrorCode.BAD_REQUEST, null);
    }

    /**
     * Missing required query parameter: 400 without exposing request values or exception details.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(
            MissingServletRequestParameterException ex) {
        return toResponse(CommonErrorCode.BAD_REQUEST, null);
    }

    /** Missing required request header (e.g. the CSRF marker): 400, never 500. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        return toResponse(CommonErrorCode.BAD_REQUEST, null);
    }

    /** Sa-Token: not logged in → 401. */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException ex) {
        return toResponse(CommonErrorCode.UNAUTHENTICATED, null);
    }

    /** Sa-Token: logged in but lacks permission → 403. */
    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<Result<Void>> handleNotPermission(Exception ex) {
        return toResponse(CommonErrorCode.FORBIDDEN, null);
    }

    /** Unknown path → 404 (Spring 6.1+). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex) {
        return toResponse(CommonErrorCode.NOT_FOUND, null);
    }

    /** Wrong HTTP method on a known path → 405, keeping the {@code Allow} header (RFC 9110). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex) {
        return toResponse(CommonErrorCode.METHOD_NOT_ALLOWED, null, ex.getHeaders());
    }

    /** Request {@code Content-Type} the endpoint cannot consume → 415, keeping {@code Accept}. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex) {
        return toResponse(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, null, ex.getHeaders());
    }

    /** {@code Accept} excludes every representation the endpoint produces → 406. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Result<Void>> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex) {
        return toResponse(CommonErrorCode.NOT_ACCEPTABLE, null);
    }

    /** Last resort: one ERROR log with traceId; client receives a generic 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        log.error("unhandled exception, traceId={}", MDC.get(ApiConstants.TRACE_ID_MDC_KEY), ex);
        return toResponse(CommonErrorCode.INTERNAL_ERROR, null);
    }

    /**
     * Error body + HTTP status from the same enum — {@link ResponseEntity} is used only here, at
     * the protocol boundary, to apply the status; controllers never assemble error responses. The
     * explicit JSON {@code Content-Type} is deliberate: without it Spring negotiates the body
     * against the caller's {@code Accept}, and an {@code Accept} that excludes JSON makes the write
     * fail inside this handler, which abandons the original exception for a bare status or a 500.
     */
    private static ResponseEntity<Result<Void>> toResponse(ErrorCode errorCode, Object errors) {
        return ResponseEntity.status(errorCode.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.error(errorCode, errors));
    }

    private static ResponseEntity<Result<Void>> toResponse(
            ErrorCode errorCode, Object errors, HttpHeaders headers) {
        return ResponseEntity.status(errorCode.httpStatus())
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.error(errorCode, errors));
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }

    /** Safe field-level validation error; never carries rejected values that may be sensitive. */
    public record FieldViolation(String field, String message) {}
}
