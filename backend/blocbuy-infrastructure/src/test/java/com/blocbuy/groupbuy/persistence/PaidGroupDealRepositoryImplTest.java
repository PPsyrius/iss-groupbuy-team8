package com.blocbuy.groupbuy.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.PaidParticipation;
import com.blocbuy.groupbuy.model.ParticipationResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Protocol guards: impossible row counts and incomplete receipts fail fast as technical errors. */
@ExtendWith(MockitoExtension.class)
class PaidGroupDealRepositoryImplTest {
    private static final UUID PAYMENT = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID ACTOR = UUID.fromString("b0000000-0000-7000-8000-000000000001");
    private static final UUID DEAL = UUID.fromString("c0000000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

    @Mock private PaidGroupDealMapper mapper;
    @InjectMocks private PaidGroupDealRepositoryImpl repository;

    @Test
    void reserveAndLockRejectsUnexpectedInsertCount() {
        when(mapper.reserveReceipt(eq(PAYMENT), any(), eq(ACTOR))).thenReturn(2);

        assertThatThrownBy(() -> repository.reserveAndLock(PAYMENT, "fp", ACTOR))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reserveAndLockRejectsMissingRow() {
        when(mapper.reserveReceipt(eq(PAYMENT), any(), eq(ACTOR))).thenReturn(1);
        when(mapper.lockReceipt(PAYMENT)).thenReturn(null);

        assertThatThrownBy(() -> repository.reserveAndLock(PAYMENT, "fp", ACTOR))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reserveAndLockRejectsCommittedRowWithoutResult() {
        when(mapper.reserveReceipt(eq(PAYMENT), any(), eq(ACTOR))).thenReturn(0);
        when(mapper.lockReceipt(PAYMENT)).thenReturn(receipt("fp", null));

        assertThatThrownBy(() -> repository.reserveAndLock(PAYMENT, "fp", ACTOR))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reserveAndLockReturnsPendingReceipt() {
        when(mapper.reserveReceipt(eq(PAYMENT), any(), eq(ACTOR))).thenReturn(1);
        when(mapper.lockReceipt(PAYMENT)).thenReturn(receipt("fp", null));

        var pending = repository.reserveAndLock(PAYMENT, "fp", ACTOR);

        assertThat(pending.fingerprint()).isEqualTo("fp");
        assertThat(pending.result()).isNull();
    }

    @Test
    void reserveAndLockReturnsCommittedReplay() {
        ParticipationResult original = new ParticipationResult("applied", DEAL, "open", 1, null);
        when(mapper.reserveReceipt(eq(PAYMENT), any(), eq(ACTOR))).thenReturn(0);
        when(mapper.lockReceipt(PAYMENT)).thenReturn(receipt("fp", original));

        var replay = repository.reserveAndLock(PAYMENT, "fp", ACTOR);

        assertThat(replay.result()).isEqualTo(original);
    }

    @Test
    void insertStartedDealRequiresSingleRowWrites() {
        when(mapper.insertDeal(any())).thenReturn(0);
        GroupDeal deal = deal();
        PaidParticipation paid =
                new PaidParticipation(PAYMENT, ACTOR, new BigDecimal("12.00"), "sgd");

        assertThatThrownBy(() -> repository.insertStartedDeal(deal, paid, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void insertJoinedMemberPersistsProgressAndMemberTogether() {
        GroupDeal before = deal();
        PaidParticipation joiner =
                new PaidParticipation(
                        UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("12.00"), "sgd");
        GroupDeal after = before.joinPaid(joiner, false, NOW);
        when(mapper.updateDeal(any(), eq(1), eq("open"))).thenReturn(1);
        when(mapper.insertMember(after.id(), joiner.paymentId(), joiner.userId(), "member", NOW))
                .thenReturn(1);

        repository.insertJoinedMember(before, after, joiner, NOW);

        verify(mapper).updateDeal(any(), eq(1), eq("open"));
        verify(mapper).insertMember(after.id(), joiner.paymentId(), joiner.userId(), "member", NOW);
    }

    @Test
    void completePersistsTerminalResult() {
        when(mapper.completeReceipt(eq(PAYMENT), any(), eq(ACTOR), eq(NOW))).thenReturn(1);
        ParticipationResult result = new ParticipationResult("applied", DEAL, "open", 1, null);

        repository.complete(PAYMENT, result, ACTOR, NOW);

        verify(mapper)
                .completeReceipt(
                        eq(PAYMENT),
                        argThat(
                                row ->
                                        "applied".equals(row.getOutcome())
                                                && DEAL.equals(row.getDealId())
                                                && "open".equals(row.getDealStatus())),
                        eq(ACTOR),
                        eq(NOW));
    }

    private static ParticipationReceiptDO receipt(String fingerprint, ParticipationResult result) {
        ParticipationReceiptDO row = new ParticipationReceiptDO();
        row.setRequestFingerprint(fingerprint);
        if (result != null) {
            row.setDealId(result.dealId());
            row.setOutcome(result.outcome());
            row.setDealStatus(result.dealStatus());
            row.setCurrentSize(result.currentSize());
            row.setReasonCode(result.reasonCode());
        }
        return row;
    }

    private static GroupDeal deal() {
        return GroupDeal.reconstitute(
                DEAL,
                "DEAL-1",
                UUID.randomUUID(),
                ACTOR,
                new BigDecimal("12.00"),
                "sgd",
                2,
                1,
                DealStatus.OPEN,
                NOW.minusSeconds(60),
                NOW.plusSeconds(60),
                null);
    }
}
