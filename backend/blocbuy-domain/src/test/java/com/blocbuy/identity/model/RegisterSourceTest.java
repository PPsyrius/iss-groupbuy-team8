package com.blocbuy.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Stored-code mapping: every persisted code resolves, unknown codes fail fast. */
class RegisterSourceTest {

    @ParameterizedTest
    @EnumSource(RegisterSource.class)
    void fromCodeResolvesAllCodes(RegisterSource source) {
        assertThat(RegisterSource.fromCode(source.code())).isEqualTo(source);
    }

    @Test
    void fromCodeRejectsUnknownCode() {
        assertThatThrownBy(() -> RegisterSource.fromCode("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
