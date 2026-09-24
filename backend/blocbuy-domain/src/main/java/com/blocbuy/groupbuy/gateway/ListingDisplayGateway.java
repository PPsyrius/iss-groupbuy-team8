package com.blocbuy.groupbuy.gateway;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer-owned port through which groupbuy reads the display fields of the products its teams
 * buy. Catalog implements it in its application layer, so groupbuy never imports catalog and the
 * existing catalog → groupbuy dependency is not reversed into a cycle.
 */
public interface ListingDisplayGateway {

    /**
     * Display fields of the given active products, in one batch lookup (never per product — N+1 is
     * forbidden). Unknown or deleted products are simply absent from the map; empty input returns
     * an empty map.
     */
    Map<UUID, ListingDisplay> findByProductIds(Collection<UUID> productIds);

    /**
     * Provider-neutral listing display fields.
     *
     * @param productId product identity
     * @param title current listing title
     * @param coverImage current cover image URL; may be null
     */
    record ListingDisplay(UUID productId, String title, String coverImage) {}
}
