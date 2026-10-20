package com.blocbuy.payment.web;

import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.web.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stripe-only extension: leave the team's global exception mapping unchanged. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
        assignableTypes = {StripeCheckoutController.class, StripeWebhookController.class})
public class StripeRequestExceptionHandler {
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.status(CommonErrorCode.BAD_REQUEST.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.error(CommonErrorCode.BAD_REQUEST, null));
    }
}
