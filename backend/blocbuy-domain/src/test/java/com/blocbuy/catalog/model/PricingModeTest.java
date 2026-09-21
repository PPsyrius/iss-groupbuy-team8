package com.blocbuy.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Stored pricing-mode code mapping. */
class PricingModeTest {

    @ParameterizedTest
    @EnumSource(PricingMode.class)
    void fromCodeResolvesEveryMode(PricingMode mode) {
        assertThat(PricingMode.fromCode(mode.code())).isEqualTo(mode);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"DUAL", "unknown"})
    void fromCodeRejectsUnknownCode(String code) {
        assertThatThrownBy(() -> PricingMode.fromCode(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown pricing mode code");
    }
}
