package com.blocbuy.catalog.query;

import java.math.BigDecimal;
import java.util.UUID;

/** Listing summary row for the browse grid; {@code groupPrice} null when no open deal. */
public record ListingView(
        UUID id,
        String title,
        String subtitle,
        String coverImage,
        String category,
        BigDecimal individualPrice,
        BigDecimal groupPrice,
        String currency) {}
