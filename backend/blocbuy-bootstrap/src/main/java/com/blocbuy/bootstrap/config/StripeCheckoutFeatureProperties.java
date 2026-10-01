package com.blocbuy.bootstrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-owned Checkout switch; absent means off in every environment. */
@ConfigurationProperties(prefix = "blocbuy.payment.stripe")
public record StripeCheckoutFeatureProperties(boolean checkoutEnabled) {}
