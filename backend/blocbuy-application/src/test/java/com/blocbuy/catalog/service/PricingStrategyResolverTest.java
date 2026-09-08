package com.blocbuy.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.pricing.DualPricingStrategy;
import com.blocbuy.catalog.pricing.PricingStrategy;
import com.blocbuy.common.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Resolver: one strategy per mode; duplicates fail at startup; unknown modes fail loudly. */
class PricingStrategyResolverTest {

    @Test
    void resolvesRegisteredStrategyByMode() {
        var resolver = new PricingStrategyResolver(List.of(new DualPricingStrategy()));

        PricingStrategy strategy = resolver.strategyFor(PricingMode.DUAL);

        assertThat(strategy).isInstanceOf(DualPricingStrategy.class);
    }

    @Test
    void duplicateModeFailsFastAtStartup() {
        assertThatThrownBy(
                        () ->
                                new PricingStrategyResolver(
                                        List.of(
                                                new DualPricingStrategy(),
                                                new DualPricingStrategy())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownModeIsBusinessErrorNotSilentFallback() {
        var resolver = new PricingStrategyResolver(List.of());

        assertThatThrownBy(() -> resolver.strategyFor(PricingMode.DUAL))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(CatalogErrorCode.PRICING_MODE_UNSUPPORTED);
    }
}
