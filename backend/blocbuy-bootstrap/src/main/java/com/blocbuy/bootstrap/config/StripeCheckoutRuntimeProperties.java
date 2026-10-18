package com.blocbuy.bootstrap.config;

import com.blocbuy.payment.gateway.StripeProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Required sandbox credentials when Checkout is enabled; never log these values. */
@Validated
@ConfigurationProperties(prefix = "blocbuy.payment.stripe")
public record StripeCheckoutRuntimeProperties(
        @NotBlank(message = "STRIPE_API_KEY must be set") String apiKey,
        @NotBlank(message = "STRIPE_WEBHOOK_SECRET must be set") String webhookSecret) {

    /**
     * Derived check, so a failure report shows this boolean and never the rejected key. A missing
     * key is left to {@code @NotBlank}.
     */
    @AssertTrue(message = "STRIPE_API_KEY must be a Stripe test key")
    public boolean isSandboxKey() {
        return apiKey == null
                || (apiKey.startsWith(StripeProperties.SANDBOX_KEY_PREFIX)
                        && apiKey.length() > StripeProperties.SANDBOX_KEY_PREFIX.length());
    }
}
