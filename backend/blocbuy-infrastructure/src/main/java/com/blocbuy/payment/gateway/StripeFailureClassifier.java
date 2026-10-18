package com.blocbuy.payment.gateway;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a {@link StripeException} into a stable business code, deciding whether asking again can
 * help, and logs only metadata that is safe to keep.
 *
 * <p>stripe-java 34 builds the exception type from the HTTP status ({@code
 * LiveStripeResponseGetter.exceptionFromStatus}): 400/404 become {@link IdempotencyException} when
 * the error type is {@code idempotency_error}, otherwise {@link InvalidRequestException}; 401
 * becomes {@link AuthenticationException}; 402 {@link CardException}; 403 {@code
 * PermissionException}, a subclass of {@link AuthenticationException}; 429 {@code
 * RateLimitException}; every other status, including 409 and all 5xx, {@code ApiException}; and a
 * transport failure {@code ApiConnectionException}.
 *
 * <p>Permanent refusals map to {@link PaymentErrorCode#PAYMENT_PROVIDER_REJECTED}. A request
 * replayed under the same idempotency key gets the same answer from Stripe, so a credential,
 * validation, card or idempotency-parameter refusal cannot heal by itself. Everything else,
 * including any subtype not named here, maps to {@link PaymentErrorCode#PAYMENT_PROVIDER_ERROR} and
 * stays retryable. A few error codes always mean "try later", whatever the type; they are checked
 * first.
 *
 * <p>The provider's message and response body can carry customer or account data, so the log line
 * holds only the operation, the exception's simple class name, HTTP status, Stripe error code,
 * request id and the disposition. The exception is never passed to the logger, and the returned
 * {@link BizException} has no cause.
 */
@Slf4j
final class StripeFailureClassifier {
    private static final Set<String> RETRYABLE_CODES =
            Set.of("rate_limit", "lock_timeout", "idempotency_key_in_use");

    private StripeFailureClassifier() {
        // static helpers only
    }

    /**
     * Logs {@code failure}'s safe metadata and returns the business exception the caller throws.
     *
     * @param operation the gateway operation that failed, for the log line
     * @param failure the provider failure; its message is never read
     * @return {@code PAYMENT_PROVIDER_REJECTED} if permanent, otherwise {@code
     *     PAYMENT_PROVIDER_ERROR}
     */
    static BizException refusal(String operation, StripeException failure) {
        boolean permanent = permanent(failure);
        log.warn(
                "Stripe call failed: operation={}, type={}, status={}, code={}, requestId={},"
                        + " disposition={}",
                operation,
                failure.getClass().getSimpleName(),
                failure.getStatusCode(),
                failure.getCode(),
                failure.getRequestId(),
                permanent ? "review" : "retry");
        return new BizException(
                permanent
                        ? PaymentErrorCode.PAYMENT_PROVIDER_REJECTED
                        : PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
    }

    /** Whether asking Stripe again, with the same request and key, cannot change the answer. */
    static boolean permanent(StripeException failure) {
        if (failure.getCode() != null && RETRYABLE_CODES.contains(failure.getCode())) {
            return false;
        }
        return failure instanceof AuthenticationException
                || failure instanceof InvalidRequestException
                || failure instanceof IdempotencyException
                || failure instanceof CardException;
    }
}
