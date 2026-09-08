package com.blocbuy.catalog.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Full listing view for the detail page; {@code deal} null when no open deal. */
public record ListingDetailView(
        UUID id,
        String title,
        String subtitle,
        String description,
        String coverImage,
        List<String> images,
        String category,
        BigDecimal individualPrice,
        BigDecimal groupPrice,
        String currency,
        int stock,
        int soldCount,
        ActiveDealView deal) {

    /** Defensive copy: view records never alias a caller's mutable list. */
    public ListingDetailView {
        images = images == null ? List.of() : List.copyOf(images);
    }
}
