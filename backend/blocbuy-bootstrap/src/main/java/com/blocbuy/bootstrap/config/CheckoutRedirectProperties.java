package com.blocbuy.bootstrap.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Return URLs required for an enabled Stripe Checkout Session. */
@Validated
@ConfigurationProperties(prefix = "blocbuy.payment.checkout")
public record CheckoutRedirectProperties(
        @NotBlank(message = "BLOCBUY_PAYMENT_CHECKOUT_SUCCESS_URL must be set") String successUrl,
        @NotBlank(message = "BLOCBUY_PAYMENT_CHECKOUT_CANCEL_URL must be set") String cancelUrl) {}
