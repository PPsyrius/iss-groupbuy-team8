package com.blocbuy.groupbuy.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Published facts carry no optional identity: a subscriber may rely on every field. */
class TeamLifecycleEventTest {

    private static final UUID DEAL = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final Instant AT = Instant.parse("2026-09-22T10:00:00Z");

    @Test
    void succeededCarriesTheTeamItsProductAndItsFinalSize() {
        TeamSucceeded event = new TeamSucceeded(DEAL, PRODUCT, 2, AT);

        assertThat(event.dealId()).isEqualTo(DEAL);
        assertThat(event.productId()).isEqualTo(PRODUCT);
        assertThat(event.size()).isEqualTo(2);
        assertThat(event.at()).isEqualTo(AT);
    }

    @Test
    void succeededRejectsAnIncompleteFact() {
        assertMalformed(() -> new TeamSucceeded(null, PRODUCT, 2, AT));
        assertMalformed(() -> new TeamSucceeded(DEAL, null, 2, AT));
        assertMalformed(() -> new TeamSucceeded(DEAL, PRODUCT, 2, null));
    }

    /** A team of one cannot have succeeded; the smallest team this product allows is two. */
    @Test
    void succeededRejectsATeamSmallerThanTwo() {
        assertMalformed(() -> new TeamSucceeded(DEAL, PRODUCT, 1, AT));
    }

    @Test
    void expiredCarriesTheTeamItsProductAndItsResolutionTime() {
        TeamExpired event = new TeamExpired(DEAL, PRODUCT, AT);

        assertThat(event.dealId()).isEqualTo(DEAL);
        assertThat(event.productId()).isEqualTo(PRODUCT);
        assertThat(event.at()).isEqualTo(AT);
    }

    @Test
    void expiredRejectsAnIncompleteFact() {
        assertMalformed(() -> new TeamExpired(null, PRODUCT, AT));
        assertMalformed(() -> new TeamExpired(DEAL, null, AT));
        assertMalformed(() -> new TeamExpired(DEAL, PRODUCT, null));
    }

    private static void assertMalformed(ThrowingCallable construction) {
        assertThatThrownBy(construction).isInstanceOf(IllegalArgumentException.class);
    }
}
