package com.blocbuy.groupbuy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Stored-code mapping: every persisted role code resolves, unknown codes fail fast. */
class MemberRoleTest {

    @ParameterizedTest
    @EnumSource(MemberRole.class)
    void fromCodeResolvesAllCodes(MemberRole role) {
        assertThat(MemberRole.fromCode(role.code())).isEqualTo(role);
    }

    @Test
    void storedCodesMatchTheWriters() {
        assertThat(MemberRole.LEADER.code()).isEqualTo("leader");
        assertThat(MemberRole.MEMBER.code()).isEqualTo("member");
    }

    @Test
    void fromCodeRejectsUnknownCode() {
        assertThatThrownBy(() -> MemberRole.fromCode("owner"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
