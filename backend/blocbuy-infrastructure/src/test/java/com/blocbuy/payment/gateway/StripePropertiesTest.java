package com.blocbuy.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

        assertThat(properties)
                .hasToString("StripeProperties[apiKey=<redacted>, webhookSecret=<redacted>]");
        assertThat(properties.apiKey()).isEqualTo("sk_test_123");
        assertThat(properties.webhookSecret()).isEqualTo("whsec_123");
    }

    @Test
    void toStringMarksAbsentSecretsAsUnset() {
        assertThat(new StripeProperties(null, null))
                .hasToString("StripeProperties[apiKey=<unset>, webhookSecret=<unset>]");
        assertThat(new StripeProperties(" ", ""))
                .hasToString("StripeProperties[apiKey=<unset>, webhookSecret=<unset>]");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u2003"})
    void toStringMarksOnlyTheAbsentApiKeyAsUnset(String apiKey) {
        StripeProperties properties = new StripeProperties(apiKey, "whsec_123");

        assertThat(properties)
                .hasToString("StripeProperties[apiKey=<unset>, webhookSecret=<redacted>]");
        assertThat(properties.apiKey()).isEqualTo(apiKey);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u2003"})
    void toStringMarksOnlyTheAbsentWebhookSecretAsUnset(String webhookSecret) {
        StripeProperties properties = new StripeProperties("sk_test_123", webhookSecret);

        assertThat(properties)
                .hasToString("StripeProperties[apiKey=<redacted>, webhookSecret=<unset>]");
        assertThat(properties.webhookSecret()).isEqualTo(webhookSecret);
    }
}
