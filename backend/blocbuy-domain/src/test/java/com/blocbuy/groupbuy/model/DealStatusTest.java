package com.blocbuy.groupbuy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Stored-code mapping: every persisted code resolves, unknown codes fail fast. */
class DealStatusTest {

    @ParameterizedTest
    @EnumSource(DealStatus.class)
    void fromCodeResolvesAllCodes(DealStatus status) {
        assertThat(DealStatus.fromCode(status.code())).isEqualTo(status);
    }

    @Test
    void fromCodeRejectsUnknownCode() {
        assertThatThrownBy(() -> DealStatus.fromCode("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
