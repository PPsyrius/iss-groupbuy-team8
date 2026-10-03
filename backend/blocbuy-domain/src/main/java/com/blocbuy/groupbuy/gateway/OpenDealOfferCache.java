package com.blocbuy.groupbuy.gateway;

import com.blocbuy.groupbuy.query.OpenDealOffer;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Derived display cache. Missing keys are misses; a present empty value is a cached absence. */
public interface OpenDealOfferCache {
    /** One batch lookup. An unavailable store behaves as an empty hit map. */
    Lookup findByProductIds(Collection<UUID> productIds);

    /** Availability prevents a second Redis wait for population after a failed lookup. */
    record Lookup(Map<UUID, Optional<OpenDealOffer>> hits, boolean available) {
        public Lookup {
            hits = Map.copyOf(hits);
        }
    }

    /** One batch population including absences; every entry expires. Failure is best effort. */
    void putAll(Map<UUID, Optional<OpenDealOffer>> offers);

    /** Best effort invalidation after the corresponding database transaction commits. */
    void evict(Collection<UUID> productIds);
}
