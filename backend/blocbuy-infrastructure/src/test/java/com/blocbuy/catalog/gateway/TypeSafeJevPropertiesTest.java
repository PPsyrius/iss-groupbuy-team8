package com.blocbuy.catalog.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Jev settings: safe defaults while disabled, API key required when enabled. */
class TypeSafeJevPropertiesTest {

    /** Verifies safe endpoint, model, and timeout defaults while disabled. */
    @Test
    void defaultsOptionalSettingsWhenDisabled() {
        TypeSafeJevProperties properties = new TypeSafeJevProperties(false, null, null, null, 0, 0);

        assertThat(properties.endpoint()).isEqualTo("https://api.typesafe.ai/v1/systemone");
        assertThat(properties.model()).isEqualTo("jev-latest");
        assertThat(properties.connectTimeoutMillis()).isEqualTo(1000);
        assertThat(properties.readTimeoutMillis()).isEqualTo(2000);
    }

    /** Verifies blank optional settings receive the same safe defaults. */
    @Test
    void blankOptionalSettingsAlsoUseDefaults() {
        TypeSafeJevProperties properties = new TypeSafeJevProperties(false, null, " ", " ", -1, -1);

        assertThat(properties.endpoint()).isEqualTo("https://api.typesafe.ai/v1/systemone");
        assertThat(properties.model()).isEqualTo("jev-latest");
    }

    /** Verifies that enabling provider access requires a nonblank API key. */
    @Test
    void enabledIntegrationRequiresApiKey() {
        assertThatThrownBy(() -> new TypeSafeJevProperties(true, null, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TYPESAFE_API_KEY");
        assertThatThrownBy(() -> new TypeSafeJevProperties(true, " ", null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TYPESAFE_API_KEY");
    }

    /** Verifies preservation of explicit endpoint, model, and timeout settings. */
    @Test
    void preservesExplicitSettings() {
        TypeSafeJevProperties properties =
                new TypeSafeJevProperties(true, "test-key", "http://localhost", "jev-test", 2, 3);

        assertThat(properties.endpoint()).isEqualTo("http://localhost");
        assertThat(properties.model()).isEqualTo("jev-test");
        assertThat(properties.connectTimeoutMillis()).isEqualTo(2);
        assertThat(properties.readTimeoutMillis()).isEqualTo(3);
    }

    /** Verifies the API key never appears in toString. */
    @Test
    void toStringNeverRevealsApiKey() {
        TypeSafeJevProperties properties =
                new TypeSafeJevProperties(
                        true, "tsj-key-9c1e", "http://localhost", "jev-test", 2, 3);

        assertThat(properties.toString())
                .doesNotContain("tsj-key-9c1e")
                .contains("apiKey=<redacted>");
        assertThat(properties.apiKey()).isEqualTo("tsj-key-9c1e");
    }

    /** Verifies an absent API key is shown as unset in toString. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u2003"})
    void toStringMarksAbsentApiKeyAsUnset(String apiKey) {
        TypeSafeJevProperties properties =
                new TypeSafeJevProperties(false, apiKey, null, null, 0, 0);

        assertThat(properties.toString()).contains("apiKey=<unset>");
        assertThat(properties.apiKey()).isEqualTo(apiKey);
    }

    /** Verifies non-secret settings remain visible in toString. */
    @Test
    void toStringKeepsNonSecretSettingsVisible() {
        TypeSafeJevProperties properties =
                new TypeSafeJevProperties(
                        true, "tsj-key-9c1e", "http://localhost", "jev-test", 2, 3);

        assertThat(properties.toString())
                .isEqualTo(
                        "TypeSafeJevProperties[enabled=true, apiKey=<redacted>, "
                                + "endpoint=http://localhost, model=jev-test, "
                                + "connectTimeoutMillis=2, readTimeoutMillis=3]");
    }
}
