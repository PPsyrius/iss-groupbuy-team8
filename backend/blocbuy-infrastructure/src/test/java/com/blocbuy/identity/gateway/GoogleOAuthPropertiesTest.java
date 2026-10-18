package com.blocbuy.identity.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Client settings: explicit values preserved, endpoints and TTL defaulted, blanks unconfigured. */
class GoogleOAuthPropertiesTest {

    @Test
    void configuredClientPreservesAllValues() {
        GoogleOAuthProperties properties =
                new GoogleOAuthProperties(
                        "cid",
                        "secret",
                        "https://cb",
                        "https://auth.example",
                        "https://token.example",
                        60);

        assertThat(properties.configured()).isTrue();
        assertThat(properties.authorizeUrl()).isEqualTo("https://auth.example");
        assertThat(properties.tokenUrl()).isEqualTo("https://token.example");
        assertThat(properties.stateTtlSeconds()).isEqualTo(60L);
    }

    @Test
    void blankSecretsAreUnconfigured() {
        assertThat(
                        new GoogleOAuthProperties(" ", "secret", "https://cb", null, null, 0)
                                .configured())
                .isFalse();
        assertThat(new GoogleOAuthProperties("cid", "", "https://cb", null, null, 0).configured())
                .isFalse();
        assertThat(new GoogleOAuthProperties("cid", "secret", "  ", null, null, 0).configured())
                .isFalse();
        assertThat(new GoogleOAuthProperties(null, null, null, null, null, 0).configured())
                .isFalse();
        assertThat(new GoogleOAuthProperties("cid", null, "https://cb", null, null, 0).configured())
                .isFalse();
        assertThat(new GoogleOAuthProperties("cid", "secret", null, null, null, 0).configured())
                .isFalse();
    }

    @Test
    void endpointsAndTtlFallBackToDefaults() {
        GoogleOAuthProperties properties =
                new GoogleOAuthProperties("cid", "secret", "https://cb", null, "  ", -5);

        assertThat(properties.authorizeUrl())
                .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(properties.tokenUrl()).isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(properties.stateTtlSeconds()).isEqualTo(300L);
    }

    @Test
    void blankAuthorizeUrlAndNullTokenUrlFallBackToDefaults() {
        GoogleOAuthProperties properties =
                new GoogleOAuthProperties("cid", "secret", "https://cb", "  ", null, 60);

        assertThat(properties.authorizeUrl())
                .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(properties.tokenUrl()).isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(properties.stateTtlSeconds()).isEqualTo(60L);
    }

    @Test
    void toStringNeverRevealsClientSecret() {
        GoogleOAuthProperties properties =
                new GoogleOAuthProperties("cid", "gsec-test-7f3a", "https://cb", null, null, 0);

        assertThat(properties.toString())
                .doesNotContain("gsec-test-7f3a")
                .contains("clientSecret=<redacted>");
    }

    @Test
    void toStringMarksAbsentClientSecretAsUnset() {
        assertThat(new GoogleOAuthProperties("cid", null, "https://cb", null, null, 0).toString())
                .contains("clientSecret=<unset>");
        assertThat(new GoogleOAuthProperties("cid", "  ", "https://cb", null, null, 0).toString())
                .contains("clientSecret=<unset>");
    }

    @Test
    void toStringKeepsNonSecretSettingsVisible() {
        GoogleOAuthProperties properties =
                new GoogleOAuthProperties(
                        "cid-visible",
                        "gsec-test-7f3a",
                        "https://cb.example/callback",
                        "https://auth.example",
                        "https://token.example",
                        60);

        assertThat(properties.toString())
                .contains("clientId=cid-visible")
                .contains("redirectUri=https://cb.example/callback")
                .contains("authorizeUrl=https://auth.example")
                .contains("tokenUrl=https://token.example")
                .contains("stateTtlSeconds=60");
    }
}
