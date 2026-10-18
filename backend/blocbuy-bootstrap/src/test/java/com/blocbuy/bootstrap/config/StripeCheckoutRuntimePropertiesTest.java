package com.blocbuy.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Checkout credentials never appear in toString, so a logged record cannot leak them. */
class StripeCheckoutRuntimePropertiesTest {

    /** Verifies neither raw secret appears in toString, and both are shown as redacted. */
    @Test
    void toStringNeverRevealsSecrets() {
        StripeCheckoutRuntimeProperties properties =
                new StripeCheckoutRuntimeProperties("sk_test_checkout_4d2b", "whsec_checkout_9e1a");

        assertThat(properties.toString())
                .doesNotContain("sk_test_checkout_4d2b")
                .doesNotContain("whsec_checkout_9e1a")
                .contains("apiKey=<redacted>")
                .contains("webhookSecret=<redacted>");
    }

    /** Verifies absent secrets are shown as unset in toString. */
    @Test
    void toStringMarksAbsentSecretsAsUnset() {
        assertThat(new StripeCheckoutRuntimeProperties(null, null).toString())
                .contains("apiKey=<unset>")
                .contains("webhookSecret=<unset>");
    }
}
