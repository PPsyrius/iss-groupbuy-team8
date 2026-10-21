package com.blocbuy.bootstrap.config;

import com.blocbuy.payment.gateway.StripeProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Required sandbox credentials when Checkout is enabled; never log these values. */
@Validated
@ConfigurationProperties(prefix = "blocbuy.payment.stripe")
public record StripeCheckoutRuntimeProperties(
        @NotBlank(message = "STRIPE_API_KEY must be set")
                @Pattern(
                        regexp = StripeProperties.SANDBOX_KEY_PREFIX + ".+",
                        message = "STRIPE_API_KEY must be a Stripe test key")
                String apiKey,
        @NotBlank(message = "STRIPE_WEBHOOK_SECRET must be set") String webhookSecret) {

    @Override
    public String toString() {
        return "StripeCheckoutRuntimeProperties[apiKey="
                + mask(apiKey)
                + ", webhookSecret="
                + mask(webhookSecret)
                + "]";
    }

    private static String mask(String secret) {
        return secret == null || secret.isBlank() ? "<unset>" : "<redacted>";
    }
}
