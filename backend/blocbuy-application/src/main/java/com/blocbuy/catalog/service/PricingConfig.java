package com.blocbuy.catalog.service;

import com.blocbuy.catalog.pricing.DualPricingStrategy;
import com.blocbuy.catalog.pricing.PricingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the domain pricing strategies as beans for {@link PricingStrategyResolver}. Domain
 * stays Spring-free, so strategies are pure classes wired here; a new pricing mode adds a strategy
 * class plus one bean method.
 */
@Configuration
public class PricingConfig {

    @Bean
    public PricingStrategy dualPricingStrategy() {
        return new DualPricingStrategy();
    }
}
