package com.blocbuy.catalog.pricing;

import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Default dual pricing: the individual price is the product list price; the group-buy price is the
 * best active deal's price. A deal whose currency differs from the product's is ignored (data
 * inconsistency must never mis-price a listing) — the listing falls back to individual price only.
 */
public final class DualPricingStrategy implements PricingStrategy {

    @Override
    public BigDecimal quoteTeamPrice(Product product, TeamPricePolicy policy, Instant now) {
        policy.requireQuotable(product, now);
        return policy.amount();
    }

    @Override
    public PricingMode mode() {
        return PricingMode.DUAL;
    }

    @Override
    public ListingPrice resolve(Product product, GroupDealOffer activeDeal) {
        if (product == null) {
            throw new IllegalArgumentException("product must not be null");
        }
        var groupPrice =
                activeDeal != null && product.currency().equalsIgnoreCase(activeDeal.currency())
                        ? activeDeal.dealPrice()
                        : null;
        return new ListingPrice(product.originalPrice(), groupPrice, product.currency());
    }
}
