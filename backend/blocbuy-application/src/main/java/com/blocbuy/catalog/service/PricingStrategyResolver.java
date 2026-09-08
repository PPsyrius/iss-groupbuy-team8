package com.blocbuy.catalog.service;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.pricing.PricingStrategy;
import com.blocbuy.common.exception.BizException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Picks the {@link PricingStrategy} for a product's {@code pricing_mode}. Strategies are Spring
 * beans registered here at startup; a duplicate mode fails the context, an unknown persisted mode
 * fails the request loudly (data error — never silently re-price).
 */
@Component
public final class PricingStrategyResolver {

    private final Map<PricingMode, PricingStrategy> strategies;

    public PricingStrategyResolver(List<PricingStrategy> strategies) {
        this.strategies = indexByMode(strategies);
    }

    /** One strategy per mode; duplicates are a wiring bug and fail the context at startup. */
    private static Map<PricingMode, PricingStrategy> indexByMode(List<PricingStrategy> strategies) {
        Map<PricingMode, PricingStrategy> byMode = new EnumMap<>(PricingMode.class);
        for (PricingStrategy strategy : strategies) {
            if (byMode.put(strategy.mode(), strategy) != null) {
                throw new IllegalStateException(
                        "duplicate PricingStrategy for mode " + strategy.mode());
            }
        }
        return Map.copyOf(byMode);
    }

    /** Strategy for the given mode; throws when the persisted mode has no registered strategy. */
    public PricingStrategy strategyFor(PricingMode mode) {
        PricingStrategy strategy = strategies.get(mode);
        if (strategy == null) {
            throw new BizException(CatalogErrorCode.PRICING_MODE_UNSUPPORTED);
        }
        return strategy;
    }
}
