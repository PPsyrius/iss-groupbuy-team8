package com.blocbuy.bootstrap.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Required sandbox credentials when Checkout is enabled; never log these values. */
@Validated
@ConfigurationProperties(prefix = "blocbuy.payment.stripe")
public record StripeCheckoutRuntimeProperties(
        @NotBlank(message = "STRIPE_API_KEY must be set")
                @Pattern(regexp = "sk_test_.+", message = "STRIPE_API_KEY must start with sk_test_")
                String apiKey,
        @NotBlank(message = "STRIPE_WEBHOOK_SECRET must be set") String webhookSecret) {}
