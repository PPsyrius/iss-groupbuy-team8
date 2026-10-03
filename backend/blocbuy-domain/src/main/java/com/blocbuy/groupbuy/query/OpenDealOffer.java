package com.blocbuy.groupbuy.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable display projection; purchase admission always reads the locked deal aggregate. */
public record OpenDealOffer(
        UUID dealId,
        UUID productId,
        BigDecimal dealPrice,
        String currency,
        int targetSize,
        int currentSize,
        Instant expireTime) {
    public OpenDealOffer {
        Objects.requireNonNull(dealId, "dealId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(dealPrice, "dealPrice");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(expireTime, "expireTime");
    }
}
