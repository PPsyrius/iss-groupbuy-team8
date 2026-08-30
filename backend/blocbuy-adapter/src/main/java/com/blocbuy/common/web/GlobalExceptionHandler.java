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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
 * <p>Body shape: RFC 9457 {@link ProblemDetail} + {@code code} + {@code traceId} (+ {@code errors}
 * for field validation). Never leaks SQL, stack traces, internal paths, or raw third-party
 * responses.
 *
 * <p>Logging policy: expected 4xx logged at WARN once here; unknown 5xx logged at ERROR once here.
 * Lower layers must not re-log the same exception.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String ERRORS_PROPERTY = "errors";
  private static final String VALIDATION_DETAIL = CommonErrorCode.VALIDATION_FAILED.defaultMessage();

  /** Business rejections: status and code come from the domain's error-code enum. */
  @ExceptionHandler(BizException.class)
  public ProblemDetail handleBiz(BizException ex) {
    ErrorCode errorCode = ex.errorCode();
    ProblemDetail body = problem(errorCode, errorCode.defaultMessage());
    if (!ex.details().isEmpty()) {
      body.setProperty(ERRORS_PROPERTY, ex.details());
    }
    log.warn("business rejection: code={}, detail={}", errorCode.code(), errorCode.defaultMessage());
    return body;
  }

  /** Bean Validation on @RequestBody: 400 with safe per-field errors. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    List<FieldViolation> violations =
        ex.getBindingResult().getFieldErrors().stream()
            .map(GlobalExceptionHandler::toViolation)
            .toList();
    ProblemDetail body = problem(CommonErrorCode.VALIDATION_FAILED, VALIDATION_DETAIL);
    body.setProperty(ERRORS_PROPERTY, violations);
    return body;
  }

  /** Bean Validation on @RequestParam/@PathVariable. */
  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
    List<String> violations =
        ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + " " + v.getMessage())
            .toList();
    ProblemDetail body = problem(CommonErrorCode.VALIDATION_FAILED, VALIDATION_DETAIL);
    body.setProperty(ERRORS_PROPERTY, violations);
    return body;
  }

  /** Malformed JSON / unreadable body. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleNotReadable(HttpMessageNotReadableException ex) {
    return problem(CommonErrorCode.BAD_REQUEST, "Request body is missing or malformed");
  }

  /** Sa-Token: not logged in → 401. */
  @ExceptionHandler(NotLoginException.class)
  public ProblemDetail handleNotLogin(NotLoginException ex) {
    return problem(CommonErrorCode.UNAUTHENTICATED, CommonErrorCode.UNAUTHENTICATED.defaultMessage());
  }

  /** Sa-Token: logged in but lacks permission → 403. */
  @ExceptionHandler(NotPermissionException.class)
  public ProblemDetail handleNotPermission(NotPermissionException ex) {
    return problem(CommonErrorCode.FORBIDDEN, CommonErrorCode.FORBIDDEN.defaultMessage());
  }

  /** Unknown path → 404 (Spring 6.1+). */
  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail handleNoResource(NoResourceFoundException ex) {
    return problem(CommonErrorCode.NOT_FOUND, CommonErrorCode.NOT_FOUND.defaultMessage());
  }

  /** Last resort: one ERROR log with traceId; client receives a generic 500. */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnknown(Exception ex) {
    log.error("unhandled exception, traceId={}", MDC.get(ApiConstants.TRACE_ID_MDC_KEY), ex);
    return problem(CommonErrorCode.INTERNAL_ERROR, CommonErrorCode.INTERNAL_ERROR.defaultMessage());
  }

  private static ProblemDetail problem(ErrorCode errorCode, String detail) {
    ProblemDetail body =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.valueOf(errorCode.httpStatus()), detail);
    body.setTitle(errorCode.defaultMessage());
    body.setProperty("code", errorCode.code());
    body.setProperty("traceId", MDC.get(ApiConstants.TRACE_ID_MDC_KEY));
    return body;
  }

  private static FieldViolation toViolation(FieldError error) {
    return new FieldViolation(error.getField(), error.getDefaultMessage());
  }

  /** Safe field-level validation error; never carries rejected values that may be sensitive. */
  public record FieldViolation(String field, String message) {}
}
