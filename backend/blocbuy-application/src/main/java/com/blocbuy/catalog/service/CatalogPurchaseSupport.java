package com.blocbuy.catalog.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Catalog's public collaboration surface for a purchase. The order module prices and settles a
 * checkout but must not read {@code biz_product} or the price policy itself: cross-domain access to
 * another module's mappers, DOs or aggregates is forbidden, so everything catalog knows about a
 * purchasable product arrives through this facade.
 *
 * <p>Every method runs inside the caller's transaction and performs no remote I/O. Callers are the
 * payment-integration adapters in the order module.
 */
public interface CatalogPurchaseSupport {

    /**
     * Prices a START: takes the product row lock, reads the latest team-price revision and the
     * product's team terms, and validates all three together.
     *
     * @param now the caller's transaction clock, compared against the policy validity window
     * @throws com.blocbuy.common.exception.BizException {@code PRODUCT_NOT_FOUND} when the product
     *     is gone, {@code PRODUCT_NOT_PURCHASABLE} when it is not on sale, out of stock or not in
     *     SGD, {@code PRODUCT_NOT_STARTABLE} when the group-buy terms are unset or out of bounds,
     *     {@code TEAM_PRICE_UNAVAILABLE} when no revision covers the whole quote window or the team
     *     price is not below the original price
     */
    StartQuote lockStartQuote(UUID productId, Instant now);

    /**
     * Prices a START at the amount of a team-price quote the buyer was already shown (#183). Takes
     * the same product row lock and product checks as {@link #lockStartQuote}, but never reads the
     * latest policy: a revision made after the quote was issued does not move its price. Expiry is
     * judged against the database clock read after the lock, not the caller's earlier clock.
     *
     * @throws com.blocbuy.common.exception.BizException {@code PRODUCT_NOT_FOUND}, {@code
     *     PRODUCT_NOT_PURCHASABLE} or {@code PRODUCT_NOT_STARTABLE} as for {@link #lockStartQuote},
     *     {@code TEAM_PRICE_QUOTE_NOT_FOUND} when the quote is missing, belongs to another buyer or
     *     prices another product, {@code TEAM_PRICE_QUOTE_EXPIRED} when it has lapsed, {@code
     *     TEAM_PRICE_UNAVAILABLE} when its amount no longer undercuts the original price
     */
    StartQuote lockQuotedStart(UUID productId, UUID quoteId, UUID buyerId);

    /**
     * Validates that a product can back a checkout right now, without locking it. Used by JOIN,
     * which takes its price from the team rather than from a policy.
     *
     * @throws com.blocbuy.common.exception.BizException {@code PRODUCT_NOT_FOUND} or {@code
     *     PRODUCT_NOT_PURCHASABLE}
     */
    PurchasableProduct requirePurchasable(UUID productId);

    /**
     * Takes one unit off an on-sale product and counts the sale in a single conditional update.
     *
     * @return false when the product is gone, not on sale, or out of stock
     */
    boolean consumeOneUnit(UUID productId);

    /** What catalog contributes to a START quote; the amount is per buyer, in major units. */
    record StartQuote(
            UUID productId,
            String title,
            String coverImage,
            BigDecimal amount,
            String currency,
            int targetSize,
            int lifetimeSeconds) {}

    /** The product snapshot a checkout needs once its price is already decided. */
    record PurchasableProduct(UUID productId, String title, String coverImage, String currency) {}
}
