package com.blocbuy.catalog.repository;

import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Policy history and immutable quotes; mutations require the product lock in the caller's
 * transaction.
 */
public interface TeamPriceRepository {
    Optional<TeamPricePolicy> findLatestPolicy(UUID productId);

    void appendPolicy(TeamPricePolicy policy, UUID actorId, Instant now);

    void insertQuote(TeamPriceQuote quote);

    Optional<TeamPriceQuote> findQuoteForOwner(UUID quoteId, UUID ownerId);

    /** Current database wall clock, not transaction-start CURRENT_TIMESTAMP. */
    Instant currentTime();
}
