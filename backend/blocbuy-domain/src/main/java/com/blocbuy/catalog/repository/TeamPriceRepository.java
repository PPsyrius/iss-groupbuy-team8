package com.blocbuy.catalog.repository;

import com.blocbuy.catalog.model.PriceReferences;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Policy history and immutable quotes; mutations require the product lock in the caller's
 * transaction.
 */
public interface TeamPriceRepository {
    /** Any policy or quote history prevents deleting its product, including deleted revisions. */
    boolean hasProductReferences(UUID productId);

    /** Count the same retained policy and quote rows used by the deletion veto. */
    PriceReferences countProductReferences(UUID productId);

    Optional<TeamPricePolicy> findLatestPolicy(UUID productId);

    /** Non-deleted revisions, including expired/future policies, ordered by version descending. */
    PageResult<TeamPricePolicy> pagePolicyRevisions(UUID productId, PageQuery page);

    /**
     * Latest non-deleted revision per requested product in one batch; missing policies are absent.
     */
    Map<UUID, TeamPricePolicy> findLatestPolicies(Collection<UUID> productIds);

    /**
     * Non-deleted listings whose latest non-deleted policy is valid at database transaction time.
     */
    long countListingsWithActivePolicy();

    void appendPolicy(TeamPricePolicy policy, UUID actorId, Instant now);

    void insertQuote(TeamPriceQuote quote);

    Optional<TeamPriceQuote> findQuoteForOwner(UUID quoteId, UUID ownerId);

    /** Current database wall clock, not transaction-start CURRENT_TIMESTAMP. */
    Instant currentTime();
}
