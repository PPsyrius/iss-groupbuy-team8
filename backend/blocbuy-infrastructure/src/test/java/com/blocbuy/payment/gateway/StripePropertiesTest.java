package com.blocbuy.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Stripe settings require a nonblank server-side API key. */
class StripePropertiesTest {

    @Test
    void nonblankApiKeyIsConfigured() {
        assertThat(new StripeProperties("sk_test_123", "whsec_123").configured()).isTrue();
        assertThat(new StripeProperties("sk_test_123", "whsec_123").sandbox()).isTrue();
        assertThat(new StripeProperties("sk_live_123", "whsec_123").sandbox()).isFalse();
    }

    @Test
    void nullOrBlankApiKeyIsUnconfigured() {
        assertThat(new StripeProperties(null, "whsec_123").configured()).isFalse();
        assertThat(new StripeProperties("", "whsec_123").configured()).isFalse();
        assertThat(new StripeProperties("  ", "whsec_123").configured()).isFalse();
        assertThat(new StripeProperties(null, "whsec_123").sandbox()).isFalse();
    }

    @Test
    void toStringNeverRevealsSecrets() {
        StripeProperties properties = new StripeProperties("sk_test_123", "whsec_123");

        assertThat(properties.toString())
                .doesNotContain("sk_test_123")
                .doesNotContain("whsec_123")
                .contains("apiKey=<redacted>")
                .contains("webhookSecret=<redacted>");
    }

    @Test
    void toStringMarksAbsentSecretsAsUnset() {
        assertThat(new StripeProperties(null, null).toString())
                .contains("apiKey=<unset>")
                .contains("webhookSecret=<unset>");
        assertThat(new StripeProperties(" ", "").toString())
                .contains("apiKey=<unset>")
                .contains("webhookSecret=<unset>");
    }
}
