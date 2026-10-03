package com.blocbuy.groupbuy.service;

import com.blocbuy.groupbuy.query.OpenDealOffer;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read port the catalog domain uses to price listings. Groupbuy aggregates never cross the domain
 * boundary; this service hands out plain offer views instead.
 */
public interface GroupDealQueryService {

    /** Global count of non-deleted, unexpired open deals with seats remaining. */
    long countOpenDeals();

    /**
     * Retention guard for catalog deletion; includes every historical deal, not just open deals.
     */
    boolean hasProductReferences(UUID productId);

    /** Historical reference count for the admin editor, with the deletion veto's scope. */
    long countProductReferences(UUID productId);

    /**
     * Best open, unexpired deal per product: lowest {@code deal_price} wins (the "what joining
     * saves" headline number). One cache batch and at most one source batch for misses. Products
     * without an open deal are absent from the result.
     */
    Map<UUID, OpenDealOffer> findBestOpenDealsByProductIds(Collection<UUID> productIds);
}
