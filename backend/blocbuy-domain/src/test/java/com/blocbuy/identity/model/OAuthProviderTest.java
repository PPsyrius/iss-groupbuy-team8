package com.blocbuy.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Stored OAuth provider code mapping. */
class OAuthProviderTest {

    @ParameterizedTest
    @EnumSource(OAuthProvider.class)
    void fromCodeResolvesEveryProvider(OAuthProvider provider) {
        assertThat(OAuthProvider.fromCode(provider.code())).isEqualTo(provider);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"GOOGLE", "unknown"})
    void fromCodeRejectsUnknownCode(String code) {
        assertThatThrownBy(() -> OAuthProvider.fromCode(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown OAuth provider code");
    }
}
