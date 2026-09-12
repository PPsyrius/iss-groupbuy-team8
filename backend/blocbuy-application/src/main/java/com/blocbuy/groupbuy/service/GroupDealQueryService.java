package com.blocbuy.groupbuy.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read port the catalog domain uses to price listings. Groupbuy aggregates never cross the domain
 * boundary; this service hands out plain offer views instead.
 */
public interface GroupDealQueryService {

    /**
     * Retention guard for catalog deletion; includes every historical deal, not just open deals.
     */
    boolean hasProductReferences(UUID productId);

    /**
     * Best open, unexpired deal per product: lowest {@code deal_price} wins (the "what joining
     * saves" headline number). One batch query for the whole collection — never per product.
     * Products without an open deal are absent from the result.
     */
    Map<UUID, OpenDealOffer> findBestOpenDealsByProductIds(Collection<UUID> productIds);

    /** Groupbuy-side view of one open deal, safe to hand across the domain boundary. */
    record OpenDealOffer(
            UUID dealId,
            UUID productId,
            BigDecimal dealPrice,
            String currency,
            int targetSize,
            int currentSize,
            Instant expireTime) {}
}
