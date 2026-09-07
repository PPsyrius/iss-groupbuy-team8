package com.blocbuy.identity.model;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(identity.lastLoginTime()).isNull();
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
    }
}
