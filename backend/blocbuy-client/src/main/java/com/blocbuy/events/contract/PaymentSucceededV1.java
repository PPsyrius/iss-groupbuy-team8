package com.blocbuy.events.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Public payment-success reference payload; it intentionally contains no amount or provider data.
 */
public record PaymentSucceededV1(
        UUID paymentId,
        UUID intentId,
        UUID recipientUserId,
        String operation,
        UUID businessId,
        Instant confirmedAt) {

    public PaymentSucceededV1 {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(intentId);
        Objects.requireNonNull(recipientUserId);
        if (!"START".equals(operation) && !"JOIN".equals(operation)) {
            throw new IllegalArgumentException("unsupported operation");
        }
        Objects.requireNonNull(businessId);
        Objects.requireNonNull(confirmedAt);
    }
}
