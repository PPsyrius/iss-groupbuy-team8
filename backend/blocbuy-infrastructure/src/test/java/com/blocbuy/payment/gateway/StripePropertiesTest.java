package com.blocbuy.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Stripe settings require a nonblank server-side API key. */
class StripePropertiesTest {

    @Test
    void nonblankApiKeyIsConfigured() {
        assertThat(new StripeProperties("sk_test_123", "whsec_123").configured()).isTrue();
    }

    @Test
    void nullOrBlankApiKeyIsUnconfigured() {
        assertThat(new StripeProperties(null, "whsec_123").configured()).isFalse();
        assertThat(new StripeProperties("", "whsec_123").configured()).isFalse();
        assertThat(new StripeProperties("  ", "whsec_123").configured()).isFalse();
    }
}
