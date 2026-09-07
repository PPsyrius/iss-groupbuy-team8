package com.blocbuy.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** User aggregate invariants: valid from birth, state transitions via behavioral methods. */
class UserTest {

    @Test
    void registerViaOAuthCreatesActiveUserWithoutUsername() {
        User user =
                User.registerViaOAuth(
                        RegisterSource.GOOGLE, "ada@example.com", "Ada Lovelace", "https://pic");

        assertThat(user.id()).isNotNull();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.registerSource()).isEqualTo(RegisterSource.GOOGLE);
        assertThat(user.username()).isNull();
        assertThat(user.email()).isEqualTo("ada@example.com");
        assertThat(user.nickname()).isEqualTo("Ada Lovelace");
        assertThat(user.avatarUrl()).isEqualTo("https://pic");
        assertThat(user.lastLoginTime()).isNull();
    }

    @Test
    void registerViaOAuthFallsBackToEmailPrefixForNickname() {
        User user = User.registerViaOAuth(RegisterSource.GOOGLE, "ada@example.com", null, null);

        assertThat(user.nickname()).isEqualTo("ada");
    }

    @Test
    void registerViaOAuthRejectsLocalSource() {
        assertThatThrownBy(
                        () ->
                                User.registerViaOAuth(
                                        RegisterSource.LOCAL, "ada@example.com", "Ada", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ensureActiveRejectsDisabledAccount() {
        User user = User.registerViaOAuth(RegisterSource.GOOGLE, "ada@example.com", "Ada", null);
        user.disable();

        assertThatThrownBy(user::ensureActive)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void touchLoginStampsInstant() {
        User user = User.registerViaOAuth(RegisterSource.GOOGLE, "ada@example.com", "Ada", null);
        Instant at = Instant.parse("2026-09-07T00:00:00Z");

        user.touchLogin(at);

        assertThat(user.lastLoginTime()).isEqualTo(at);
    }
}
