package com.blocbuy.catalog.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Catalog-side view of the groupbuy deal chosen to price a listing. The groupbuy application
 * service hands this across the domain boundary (catalog never sees groupbuy aggregates); a null
 * offer means the product currently has no open, unexpired deal.
 *
 * @param dealId backing deal; lets the detail view link the buyer to the deal
 * @param dealPrice per-unit group price in major units
 * @param currency ISO-4217 code, e.g. {@code "sgd"}
 * @param targetSize paid members required for the deal to succeed
 * @param currentSize current paid member count
 * @param expireTime instant the deal closes if the target is unmet; UTC
 */
public record GroupDealOffer(
        UUID dealId,
        BigDecimal dealPrice,
        String currency,
        int targetSize,
        int currentSize,
        Instant expireTime) {

    public GroupDealOffer {
        if (dealId == null) {
            throw new IllegalArgumentException("dealId must not be null");
        }
        if (dealPrice == null || dealPrice.signum() < 0) {
            throw new IllegalArgumentException("dealPrice must not be null or negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }
}
