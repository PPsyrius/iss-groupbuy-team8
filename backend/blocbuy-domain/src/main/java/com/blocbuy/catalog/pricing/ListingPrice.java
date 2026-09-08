package com.blocbuy.catalog.pricing;

import java.math.BigDecimal;

/**
 * Resolved price pair for one listing view. {@code groupPrice} is null when the product has no
 * active deal (or the mode defines no group component) — the UI then shows the individual price
 * only. All amounts are major units in {@code currency}.
 */
public record ListingPrice(BigDecimal individualPrice, BigDecimal groupPrice, String currency) {

    public ListingPrice {
        if (individualPrice == null || individualPrice.signum() < 0) {
            throw new IllegalArgumentException("individualPrice must not be null or negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }

    /** True when a group-buy price is available to display alongside the individual price. */
    public boolean hasGroupPrice() {
        return groupPrice != null;
    }
}
