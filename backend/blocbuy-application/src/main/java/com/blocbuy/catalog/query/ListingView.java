package com.blocbuy.catalog.query;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Listing summary row for the browse grid; {@code groupPrice} and {@code deal} null when no open
 * deal. {@code deal} is the same open deal the detail view returns.
 */
public record ListingView(
        UUID id,
        String title,
        String subtitle,
        String coverImage,
        String category,
        BigDecimal individualPrice,
        BigDecimal groupPrice,
        String currency,
        ActiveDealView deal) {}
