package com.blocbuy.catalog.service;

import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Catalog quotation facade. A START purchase intent may accept an unexpired quote through {@link
 * CatalogPurchaseSupport#lockQuotedStart}; reading one here is never acceptance.
 */
public interface TeamPriceAppService {
    TeamPricePolicy configure(UUID productId, UUID actorId, ConfigurePolicy command);

    TeamPricePolicy getPolicy(UUID productId, UUID actorId);

    /** Administrative history; missing/deleted listings are not an empty policy population. */
    PageResult<TeamPricePolicy> pagePolicyRevisions(UUID productId, UUID actorId, PageQuery page);

    TeamPriceQuote issueQuote(UUID productId, UUID ownerId);

    TeamPriceQuote getQuote(UUID quoteId, UUID ownerId);

    record ConfigurePolicy(
            BigDecimal amount,
            String currency,
            Instant validFrom,
            Instant validUntil,
            int expectedVersion) {}
}
