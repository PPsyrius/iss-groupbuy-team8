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
import java.time.Duration;
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
    void reserveRejectsImpossibleTwoRowInsert() {
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
    void reserveRejectsCommittedReceiptMissingResult() {
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
    void insertStartedDealRejectsZeroRowDealInsert() {
        when(mapper.insertDeal(any())).thenReturn(0);
        GroupDeal deal = deal();
        PaidParticipation paid =
                new PaidParticipation(PAYMENT, ACTOR, new BigDecimal("12.00"), "sgd");

        assertThatThrownBy(() -> repository.insertStartedDeal(deal, paid, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void joinedMemberWritesProgressAndMembershipTogether() {
        GroupDeal before = deal();
        PaidParticipation joiner =
                new PaidParticipation(
                        UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("12.00"), "sgd");
        GroupDeal after = before.joinPaid(joiner, false, NOW).deal();
        when(mapper.updateDeal(any(), eq(1), eq("open"))).thenReturn(1);
        UUID sharer = UUID.randomUUID();
        when(mapper.insertMember(
                        after.id(), joiner.paymentId(), joiner.userId(), "member", sharer, NOW))
                .thenReturn(1);

        repository.insertJoinedMember(before, after, joiner, sharer, NOW);

        verify(mapper).updateDeal(any(), eq(1), eq("open"));
        verify(mapper)
                .insertMember(
                        after.id(), joiner.paymentId(), joiner.userId(), "member", sharer, NOW);
    }

    @Test
    void linkingTheOrderReportsWhetherOneMemberRowMatched() {
        UUID order = UUID.randomUUID();
        when(mapper.linkOrder(DEAL, PAYMENT, order)).thenReturn(1, 0);

        assertThat(repository.linkOrder(DEAL, PAYMENT, order)).isTrue();
        assertThat(repository.linkOrder(DEAL, PAYMENT, order)).isFalse();
    }

    @Test
    void thePaidMemberProjectionMapsThroughAndKeepsMissesEmpty() {
        var row = new PaidMemberDO();
        row.setDealId(DEAL);
        row.setUserId(ACTOR);
        row.setStatus("paid");
        when(mapper.selectPaidMember(PAYMENT)).thenReturn(row, (PaidMemberDO) null);

        assertThat(repository.findPaidMember(PAYMENT))
                .hasValueSatisfying(member -> {
                    assertThat(member.dealId()).isEqualTo(DEAL);
                    assertThat(member.userId()).isEqualTo(ACTOR);
                    assertThat(member.status()).isEqualTo("paid");
                    // Null until the order backfill lands in the same transaction.
                    assertThat(member.orderId()).isNull();
                });
        assertThat(repository.findPaidMember(PAYMENT)).isEmpty();
    }

    @Test
    void completeMapsResultToReceiptRowFields() {
        when(mapper.completeReceipt(eq(PAYMENT), any(), eq(ACTOR), eq(NOW))).thenReturn(1);
        ParticipationResult result = new ParticipationResult("applied", DEAL, "open", 1, null);

        repository.complete(PAYMENT, result, ACTOR, NOW);

        verify(mapper)
                .completeReceipt(
                        eq(PAYMENT),
                        argThat(row ->
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

    @Test
    void dueForExpiryPassesTheBoundedBatchStraightThrough() {
        when(mapper.selectDueForExpiry(NOW, 50)).thenReturn(java.util.List.of(DEAL));

        assertThat(repository.dueForExpiry(NOW, 50)).containsExactly(DEAL);
    }

    @Test
    void failedExpiryBackoffRequiresOneRetryRow() {
        Duration delay = Duration.ofMinutes(5);
        when(mapper.deferExpiryRetry(DEAL, 300.0)).thenReturn(1, 0);

        repository.deferExpiryRetry(DEAL, delay);

        assertThatThrownBy(() -> repository.deferExpiryRetry(DEAL, delay))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedExpiryBackoffRejectsMissingOrNonPositiveValues() {
        Duration delay = Duration.ofMinutes(5);

        assertThatThrownBy(() -> repository.deferExpiryRetry(null, delay))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.deferExpiryRetry(DEAL, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.deferExpiryRetry(DEAL, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Writes only the status and the audit timestamp; frozen terms are never recalculated. */
    @Test
    void expireWritesTheResolvedStatusUnderTheStatusGuard() {
        when(mapper.expireDeal(
                        argThat(row ->
                                DEAL.equals(row.getId())
                                        && "expired".equals(row.getStatus())
                                        && NOW.equals(row.getUpdateTime()))))
                .thenReturn(1);

        repository.expire(deal(), deal().expire(NOW.plusSeconds(60)), NOW);

        verify(mapper).expireDeal(any());
    }

    /**
     * Zero rows means another writer resolved this deal first — a replay, not a broken protocol.
     */
    @Test
    void expireReportsAConcurrentResolutionAsABusinessRejection() {
        GroupDeal before = deal();
        GroupDeal after = before.expire(NOW.plusSeconds(60));
        when(mapper.expireDeal(any())).thenReturn(0);

        assertThatThrownBy(() -> repository.expire(before, after, NOW))
                .isInstanceOf(com.blocbuy.common.exception.BizException.class)
                .satisfies(failure ->
                        assertThat(
                                        ((com.blocbuy.common.exception.BizException) failure)
                                                .errorCode())
                                .isEqualTo(
                                        com.blocbuy.groupbuy.exception.GroupbuyErrorCode
                                                .DEAL_CLOSED));
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
