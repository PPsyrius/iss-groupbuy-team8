package com.blocbuy.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** OAuthIdentity binding invariants. */
class OAuthIdentityTest {

    @Test
    void bindCreatesBindingForUser() {
        UUID userId = UUID.randomUUID();

        OAuthIdentity identity =
                OAuthIdentity.bind(
                        userId,
                        OAuthProvider.GOOGLE,
                        "sub-123",
                        "ada@example.com",
                        "Ada",
                        null,
                        "{}");

        assertThat(identity.id()).isNotNull();
        assertThat(identity.userId()).isEqualTo(userId);
        assertThat(identity.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(identity.providerUserId()).isEqualTo("sub-123");
        assertThat(identity.email()).isEqualTo("ada@example.com");
        assertThat(identity.displayName()).isEqualTo("Ada");
        assertThat(identity.avatarUrl()).isNull();
        assertThat(identity.rawProfile()).isEqualTo("{}");
        assertThat(identity.lastLoginTime()).isNull();
    }

    @Test
    void bindRejectsBlankIdentity() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() ->
                        OAuthIdentity.bind(
                                null, OAuthProvider.GOOGLE, "sub-123", "a@b.c", "Ada", null, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        OAuthIdentity.bind(userId, null, "sub-123", "a@b.c", "Ada", null, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        OAuthIdentity.bind(
                                userId, OAuthProvider.GOOGLE, null, "a@b.c", "Ada", null, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        OAuthIdentity.bind(
                                userId, OAuthProvider.GOOGLE, "  ", "a@b.c", "Ada", null, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void touchLoginRejectsNullInstant() {
        OAuthIdentity identity =
                OAuthIdentity.bind(
                        UUID.randomUUID(),
                        OAuthProvider.GOOGLE,
                        "sub-123",
                        "old@example.com",
                        "Old",
                        null,
                        "{}");

        assertThatThrownBy(() -> identity.touchLogin(null, "a@b.c", "A", null, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(identity.lastLoginTime()).isNull();
        assertThat(identity.email()).isEqualTo("old@example.com");
    }

    @Test
    void reconstitutePreservesSnapshot() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant at = Instant.parse("2026-09-07T00:00:00Z");

        OAuthIdentity identity =
                OAuthIdentity.reconstitute(
                        id,
                        userId,
                        OAuthProvider.GOOGLE,
                        "sub-123",
                        "ada@example.com",
                        "Ada",
                        "https://pic",
                        "{\"sub\":\"sub-123\"}",
                        at);

        assertThat(identity.id()).isEqualTo(id);
        assertThat(identity.userId()).isEqualTo(userId);
        assertThat(identity.email()).isEqualTo("ada@example.com");
        assertThat(identity.displayName()).isEqualTo("Ada");
        assertThat(identity.avatarUrl()).isEqualTo("https://pic");
        assertThat(identity.rawProfile()).isEqualTo("{\"sub\":\"sub-123\"}");
        assertThat(identity.lastLoginTime()).isEqualTo(at);
    }

    @Test
    void reconstituteRejectsNullId() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() ->
                        OAuthIdentity.reconstitute(
                                null,
                                userId,
                                OAuthProvider.GOOGLE,
                                "sub-123",
                                "ada@example.com",
                                "Ada",
                                "https://pic",
                                "{}",
                                null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void touchLoginRefreshesSnapshot() {
        OAuthIdentity identity =
                OAuthIdentity.bind(
                        UUID.randomUUID(),
                        OAuthProvider.GOOGLE,
                        "sub-123",
                        "old@example.com",
                        "Old",
                        null,
                        "{}");
        Instant at = Instant.parse("2026-09-07T00:00:00Z");

        identity.touchLogin(at, "new@example.com", "New", "https://pic", "{\"sub\":\"sub-123\"}");

        assertThat(identity.lastLoginTime()).isEqualTo(at);
        assertThat(identity.email()).isEqualTo("new@example.com");
        assertThat(identity.displayName()).isEqualTo("New");
        assertThat(identity.avatarUrl()).isEqualTo("https://pic");
        assertThat(identity.rawProfile()).isEqualTo("{\"sub\":\"sub-123\"}");
    }
}
