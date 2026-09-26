package com.blocbuy.groupbuy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.groupbuy.event.MemberJoined;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A credit is derived only from an attributed join and never credits the invitee themselves. */
class ShareCreditTest {

    private static final UUID DEAL = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final UUID MEMBER = UUID.fromString("a0000000-0000-7000-8000-000000000003");
    private static final UUID PAYMENT = UUID.fromString("a0000000-0000-7000-8000-000000000004");
    private static final UUID SHARER = UUID.fromString("a0000000-0000-7000-8000-000000000005");
    private static final Instant AT = Instant.parse("2026-09-25T10:00:00Z");

    @Test
    void anAttributedJoinCreditsItsSharerForThatParticipation() {
        ShareCredit credit =
                ShareCredit.of(new MemberJoined(DEAL, PRODUCT, MEMBER, PAYMENT, SHARER, AT));

        assertThat(credit).isEqualTo(new ShareCredit(DEAL, PAYMENT, SHARER, MEMBER, AT));
    }

    @Test
    void anUnattributedJoinEarnsNoCredit() {
        MemberJoined unattributed = new MemberJoined(DEAL, PRODUCT, MEMBER, PAYMENT, null, AT);

        assertThatThrownBy(() -> ShareCredit.of(unattributed))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSharerIsNeverCreditedForTheirOwnJoin() {
        assertThatThrownBy(() -> new ShareCredit(DEAL, PAYMENT, MEMBER, MEMBER, AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyFieldIsRequired() {
        assertThatThrownBy(() -> new ShareCredit(null, PAYMENT, SHARER, MEMBER, AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShareCredit(DEAL, null, SHARER, MEMBER, AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShareCredit(DEAL, PAYMENT, SHARER, null, AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShareCredit(DEAL, PAYMENT, SHARER, MEMBER, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
