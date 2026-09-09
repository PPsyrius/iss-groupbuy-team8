package com.blocbuy.catalog.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable owner-scoped amount snapshot, not a payment or inventory reservation. */
public record TeamPriceQuote(
        UUID id,
        UUID productId,
        UUID ownerId,
        int policyVersion,
        BigDecimal amount,
        String currency,
        Instant issuedAt,
        Instant expiresAt) {

    public TeamPriceQuote {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        amount = TeamPricePolicy.exactAmount(amount);
        if (policyVersion < 1 || !"sgd".equals(currency) || !issuedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("invalid persisted quote snapshot");
        }
    }
}
