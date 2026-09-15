package com.blocbuy.groupbuy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Terminal outcome shape: applied needs progress, compensation needs a reason, never both. */
class ParticipationResultTest {
    private static final UUID DEAL = UUID.fromString("b0000000-0000-7000-8000-000000000001");

    @Test
    void appliedCarriesProgressWithoutReason() {
        ParticipationResult result = ParticipationResult.applied(snapshot());

        assertThat(result.outcome()).isEqualTo("applied");
        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.reasonCode()).isNull();
    }

    @Test
    void compensationCarriesReasonWithoutProgress() {
        ParticipationResult result = ParticipationResult.compensation(DEAL, "GROUPBUY_404_001");

        assertThat(result.outcome()).isEqualTo("compensation_required");
        assertThat(result.dealId()).isEqualTo(DEAL);
        assertThat(result.reasonCode()).isEqualTo("GROUPBUY_404_001");
        assertThat(result.dealStatus()).isNull();
        assertThat(result.currentSize()).isNull();
    }

    @Test
    void rejectsMalformedOutcomes() {
        assertThatThrownBy(() -> new ParticipationResult("applied", null, "open", 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParticipationResult("nope", DEAL, null, null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParticipationResult("applied", DEAL, null, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParticipationResult("applied", DEAL, "open", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParticipationResult("applied", DEAL, "open", 1, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParticipationResult.compensation(DEAL, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static GroupDeal snapshot() {
        return GroupDeal.reconstitute(
                DEAL,
                "DEAL-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("12.00"),
                "sgd",
                2,
                1,
                DealStatus.OPEN,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2027-12-31T23:59:59Z"),
                null);
    }
}
