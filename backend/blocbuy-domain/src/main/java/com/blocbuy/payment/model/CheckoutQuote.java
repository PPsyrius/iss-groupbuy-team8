package com.blocbuy.payment.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Immutable server-owned price commitment. Never bind this type to a browser request. */
public record CheckoutQuote(
        UUID intentId,
        UUID userId,
        String operation,
        UUID businessId,
        BigDecimal amount,
        String currency,
        Instant quoteValidUntil,
        Instant businessDeadline) {
    public CheckoutQuote {
        Objects.requireNonNull(intentId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(businessId);
        Objects.requireNonNull(quoteValidUntil);
        Objects.requireNonNull(businessDeadline);
        quoteValidUntil = quoteValidUntil.truncatedTo(ChronoUnit.MICROS);
        businessDeadline = businessDeadline.truncatedTo(ChronoUnit.MICROS);
        if (!"START".equals(operation) && !"JOIN".equals(operation)) {
            throw new IllegalArgumentException("Unsupported purchase operation");
        }
        if (!"sgd".equals(currency)) {
            throw new IllegalArgumentException("This checkout slice supports SGD only");
        }
        amount = Objects.requireNonNull(amount).setScale(2, RoundingMode.UNNECESSARY);
        if (amount.signum() <= 0 || amount.precision() > 18) {
            throw new IllegalArgumentException("Invalid amount");
        }
        // scale=2 and precision<=18 already bound minor units below Long.MAX_VALUE.
    }

    public boolean expiredAt(Instant now) {
        return !now.isBefore(businessDeadline) || !now.isBefore(quoteValidUntil);
    }
}
