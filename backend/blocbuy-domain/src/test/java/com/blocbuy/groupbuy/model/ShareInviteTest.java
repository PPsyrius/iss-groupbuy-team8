package com.blocbuy.groupbuy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Invite references are opaque, URL-safe and unguessable; they attribute only other buyers. */
class ShareInviteTest {

    private static final UUID DEAL = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID SHARER = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final UUID BUYER = UUID.fromString("a0000000-0000-7000-8000-000000000003");

    @Test
    void aMintedReferenceIs22Base64UrlCharactersFromTheRandomSource() {
        ShareInvite invite = ShareInvite.mint(DEAL, SHARER, new Random(7));

        assertThat(invite.ref()).matches("[A-Za-z0-9_-]{22}");
        assertThat(invite.dealId()).isEqualTo(DEAL);
        assertThat(invite.sharerUserId()).isEqualTo(SHARER);
        assertThat(invite.id()).isNotNull();
        assertThat(ShareInvite.mint(DEAL, SHARER, new Random(7)).ref()).isEqualTo(invite.ref());
        assertThat(ShareInvite.mint(DEAL, SHARER, new Random(8)).ref()).isNotEqualTo(invite.ref());
    }

    @Test
    void itAttributesAnotherBuyerJoiningTheSameTeamOnly() {
        ShareInvite invite = ShareInvite.mint(DEAL, SHARER, new Random(1));

        assertThat(invite.attributes(DEAL, BUYER)).isTrue();
        assertThat(invite.attributes(UUID.randomUUID(), BUYER)).isFalse();
        assertThat(invite.attributes(DEAL, SHARER)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "short", "AAAAAAAAAAAAAAAAAAAAA=", "AAAAAAAAAAAAAAAAAAAAAAA"})
    void aMalformedReferenceIsRejected(String ref) {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new ShareInvite(id, ref, DEAL, SHARER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyIdentityIsRequired() {
        String ref = "AAAAAAAAAAAAAAAAAAAAAA";
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new ShareInvite(null, ref, DEAL, SHARER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShareInvite(id, ref, null, SHARER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShareInvite(id, ref, DEAL, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
