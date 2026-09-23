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

    /** Every pair in the matrix, so no service is ever tempted to re-decide one with if/else. */
    @ParameterizedTest
    @EnumSource(DealStatus.class)
    void onlyOpenMovesAndNeverToItself(DealStatus source) {
        for (DealStatus target : DealStatus.values()) {
            boolean legal = source == DealStatus.OPEN && target != DealStatus.OPEN;
            assertThat(source.canTransitionTo(target)).isEqualTo(legal);
        }
    }

    @ParameterizedTest
    @EnumSource(DealStatus.class)
    void noStatusTransitionsToNothing(DealStatus source) {
        assertThat(source.canTransitionTo(null)).isFalse();
    }
}
