package com.blocbuy.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Masks the textual representation without changing the credentials used by Checkout. */
class StripeCheckoutRuntimePropertiesTest {

    /** Verifies neither raw secret appears in toString, and both are shown as redacted. */
    @Test
    void toStringNeverRevealsSecrets() {
        StripeCheckoutRuntimeProperties properties =
                new StripeCheckoutRuntimeProperties("sk_test_checkout_4d2b", "whsec_checkout_9e1a");

        assertThat(properties.toString())
                .isEqualTo(
                        "StripeCheckoutRuntimeProperties[apiKey=<redacted>,"
                                + " webhookSecret=<redacted>]");
        assertThat(properties.apiKey()).isEqualTo("sk_test_checkout_4d2b");
        assertThat(properties.webhookSecret()).isEqualTo("whsec_checkout_9e1a");
    }

    /** Verifies absent secrets are shown as unset in toString. */
    @Test
    void toStringMarksAbsentSecretsAsUnset() {
        assertThat(new StripeCheckoutRuntimeProperties(null, null).toString())
                .isEqualTo(
                        "StripeCheckoutRuntimeProperties[apiKey=<unset>, webhookSecret=<unset>]");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u2003"})
    void toStringMarksOnlyTheAbsentApiKeyAsUnset(String apiKey) {
        StripeCheckoutRuntimeProperties properties =
                new StripeCheckoutRuntimeProperties(apiKey, "whsec_checkout_9e1a");

        assertThat(properties.toString())
                .isEqualTo(
                        "StripeCheckoutRuntimeProperties[apiKey=<unset>,"
                                + " webhookSecret=<redacted>]");
        assertThat(properties.apiKey()).isEqualTo(apiKey);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u2003"})
    void toStringMarksOnlyTheAbsentWebhookSecretAsUnset(String webhookSecret) {
        StripeCheckoutRuntimeProperties properties =
                new StripeCheckoutRuntimeProperties("sk_test_checkout_4d2b", webhookSecret);

        assertThat(properties.toString())
                .isEqualTo(
                        "StripeCheckoutRuntimeProperties[apiKey=<redacted>,"
                                + " webhookSecret=<unset>]");
        assertThat(properties.webhookSecret()).isEqualTo(webhookSecret);
    }
}
