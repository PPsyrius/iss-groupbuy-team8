package com.blocbuy.catalog.pricing;

import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;

/**
 * Pricing strategy: resolves the displayed price pair for one listing view. Isolates pricing rules
 * so a new pricing mode (new {@link PricingMode} constant + new implementation) never touches
 * browse logic; selection is driven by {@code biz_product.pricing_mode} via the application-layer
 * resolver.
 *
 * <p>Implementations are pure domain objects (no Spring, no I/O) and must be stateless.
 */
public interface PricingStrategy {

    /** Mode this strategy answers for; one strategy per mode. */
    PricingMode mode();

    /**
     * Resolves the price pair for {@code product}. {@code activeDeal} is the best open deal the
     * groupbuy side picked for this product, or null when none exists; strategies decide whether a
     * deal applies at all.
     */
    ListingPrice resolve(Product product, GroupDealOffer activeDeal);
}
