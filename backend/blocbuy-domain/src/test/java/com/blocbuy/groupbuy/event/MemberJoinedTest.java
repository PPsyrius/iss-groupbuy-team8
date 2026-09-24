package com.blocbuy.groupbuy.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** An accepted join names every identity a subscriber relies on; only the sharer is optional. */
class MemberJoinedTest {

    private static final UUID DEAL = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final UUID MEMBER = UUID.fromString("a0000000-0000-7000-8000-000000000003");
    private static final UUID PAYMENT = UUID.fromString("a0000000-0000-7000-8000-000000000004");
    private static final UUID SHARER = UUID.fromString("a0000000-0000-7000-8000-000000000005");
    private static final Instant AT = Instant.parse("2026-09-25T10:00:00Z");

    @Test
    void anAttributedJoinCarriesItsSharer() {
        MemberJoined event = new MemberJoined(DEAL, PRODUCT, MEMBER, PAYMENT, SHARER, AT);

        assertThat(event.attributed()).isTrue();
        assertThat(event.sharerUserId()).isEqualTo(SHARER);
    }

    @Test
    void anUnattributedJoinHasNoSharer() {
        assertThat(new MemberJoined(DEAL, PRODUCT, MEMBER, PAYMENT, null, AT).attributed())
                .isFalse();
    }

    @Test
    void everyIdentityButTheSharerIsRequired() {
        assertMalformed(() -> new MemberJoined(null, PRODUCT, MEMBER, PAYMENT, SHARER, AT));
        assertMalformed(() -> new MemberJoined(DEAL, null, MEMBER, PAYMENT, SHARER, AT));
        assertMalformed(() -> new MemberJoined(DEAL, PRODUCT, null, PAYMENT, SHARER, AT));
        assertMalformed(() -> new MemberJoined(DEAL, PRODUCT, MEMBER, null, SHARER, AT));
        assertMalformed(() -> new MemberJoined(DEAL, PRODUCT, MEMBER, PAYMENT, SHARER, null));
    }

    @Test
    void nobodyIsTheirOwnSharer() {
        assertMalformed(() -> new MemberJoined(DEAL, PRODUCT, MEMBER, PAYMENT, MEMBER, AT));
    }

    private static void assertMalformed(ThrowingCallable construction) {
        assertThatThrownBy(construction).isInstanceOf(IllegalArgumentException.class);
    }
}
