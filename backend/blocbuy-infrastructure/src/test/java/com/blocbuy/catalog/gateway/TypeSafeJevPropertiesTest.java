package com.blocbuy.catalog.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TypeSafeJevPropertiesTest {

    @Test
    void defaultsOptionalSettingsWhenDisabled() {
        TypeSafeJevProperties properties = new TypeSafeJevProperties(false, null, null, null, 0, 0);

        assertThat(properties.endpoint()).isEqualTo("https://api.typesafe.ai/v1/systemone");
        assertThat(properties.model()).isEqualTo("jev-latest");
        assertThat(properties.connectTimeoutMillis()).isEqualTo(1000);
        assertThat(properties.readTimeoutMillis()).isEqualTo(2000);
    }

    @Test
    void blankOptionalSettingsAlsoUseDefaults() {
        TypeSafeJevProperties properties = new TypeSafeJevProperties(false, null, " ", " ", -1, -1);

        assertThat(properties.endpoint()).isEqualTo("https://api.typesafe.ai/v1/systemone");
        assertThat(properties.model()).isEqualTo("jev-latest");
    }

    @Test
    void enabledIntegrationRequiresApiKey() {
        assertThatThrownBy(() -> new TypeSafeJevProperties(true, null, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TYPESAFE_API_KEY");
        assertThatThrownBy(() -> new TypeSafeJevProperties(true, " ", null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TYPESAFE_API_KEY");
    }

    @Test
    void preservesExplicitSettings() {
        TypeSafeJevProperties properties =
                new TypeSafeJevProperties(true, "test-key", "http://localhost", "jev-test", 2, 3);

        assertThat(properties.endpoint()).isEqualTo("http://localhost");
        assertThat(properties.model()).isEqualTo("jev-test");
        assertThat(properties.connectTimeoutMillis()).isEqualTo(2);
        assertThat(properties.readTimeoutMillis()).isEqualTo(3);
    }
}
