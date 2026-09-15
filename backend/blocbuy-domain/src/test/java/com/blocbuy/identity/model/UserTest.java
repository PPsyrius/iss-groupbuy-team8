package com.blocbuy.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void registerViaOAuthFallsBackToEmailPrefixForNickname(String displayName) {
        User user =
                User.registerViaOAuth(RegisterSource.GOOGLE, "ada@example.com", displayName, null);

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

    /** Verifies reconstitution rejects absent identity without touching other fields. */
    @Test
    void reconstituteRejectsNullIdentity() {
        assertThatThrownBy(
                        () ->
                                User.reconstitute(
                                        null,
                                        null,
                                        "ada@example.com",
                                        RegisterSource.GOOGLE,
                                        UserStatus.ACTIVE,
                                        null,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                User.reconstitute(
                                        UUID.randomUUID(),
                                        null,
                                        "ada@example.com",
                                        null,
                                        UserStatus.ACTIVE,
                                        null,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                User.reconstitute(
                                        UUID.randomUUID(),
                                        null,
                                        "ada@example.com",
                                        RegisterSource.GOOGLE,
                                        null,
                                        null,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Verifies registration requires a usable email for account resolution. */
    @Test
    void registerViaOAuthRejectsBlankEmail() {
        assertThatThrownBy(() -> User.registerViaOAuth(RegisterSource.GOOGLE, null, "Ada", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.registerViaOAuth(RegisterSource.GOOGLE, "", "Ada", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.registerViaOAuth(RegisterSource.GOOGLE, "  ", "Ada", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Verifies login stamping rejects an absent instant without mutating state. */
    @Test
    void touchLoginRejectsNullInstant() {
        User user = User.registerViaOAuth(RegisterSource.GOOGLE, "ada@example.com", "Ada", null);

        assertThatThrownBy(() -> user.touchLogin(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.lastLoginTime()).isNull();
    }

    /** Verifies the guard passes silently for an active account. */
    @Test
    void ensureActivePassesForActiveAccount() {
        User user = User.registerViaOAuth(RegisterSource.GOOGLE, "ada@example.com", "Ada", null);

        user.ensureActive();

        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    }
}
