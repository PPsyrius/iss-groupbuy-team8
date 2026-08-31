package com.blocbuy.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe connection settings, bound from {@code blocbuy.payment.stripe.*}. Secrets arrive via
 * environment variables ({@code STRIPE_API_KEY}, {@code STRIPE_WEBHOOK_SECRET}) — never committed
 * values. Missing secrets are tolerated at startup so the skeleton boots without a Stripe account;
 * the gateway fails fast with a provider error when actually called unconfigured.
 *
 * @param apiKey Stripe secret API key (server-side only)
 * @param webhookSecret signing secret of the configured webhook endpoint ({@code whsec_...})
 */
@ConfigurationProperties(prefix = "blocbuy.payment.stripe")
public record StripeProperties(String apiKey, String webhookSecret) {

    /** True when the minimum configuration for live calls is present. */
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
