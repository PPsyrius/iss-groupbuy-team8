package com.blocbuy.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Stored-code mapping: every persisted code resolves, unknown codes fail fast. */
class UserStatusTest {

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    void fromCodeResolvesAllCodes(UserStatus status) {
        assertThat(UserStatus.fromCode(status.code())).isEqualTo(status);
    }

    @Test
    void fromCodeRejectsUnknownCode() {
        assertThatThrownBy(() -> UserStatus.fromCode("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
