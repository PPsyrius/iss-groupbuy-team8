package com.blocbuy.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.event.TeamSucceeded;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.exception.ParticipationErrorCode;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.NewDealTerms;
import com.blocbuy.groupbuy.model.PaidParticipation;
import com.blocbuy.groupbuy.model.ParticipationReceipt;
import com.blocbuy.groupbuy.model.ParticipationResult;
import com.blocbuy.groupbuy.model.StandardPaidGroupDealCreator;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Trusted payment application: receipt replay, fingerprint guard, compensation without refund. */
class PaidGroupDealServiceTest {
    private final PaidGroupDealRepository repository = mock(PaidGroupDealRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PaidGroupDealService service = new PaidGroupDealService(repository, events);
    private final Instant now = Instant.parse("2026-09-09T00:00:00Z");
    private final PaidParticipation paid =
            new PaidParticipation(
                    UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("12.00"), "sgd");
    private final NewDealTerms terms =
            new NewDealTerms(
                    UUID.randomUUID(),
                    "DEAL-1",
                    UUID.randomUUID(),
                    new BigDecimal("12.00"),
                    "sgd",
                    2,
                    now.plusSeconds(60));

    /**
     * Verifies initiation requests both paid-leader persistence and the matching terminal receipt.
     */
    @Test
    void startPersistsLeaderAndReceiptTogether() {
        when(repository.reserveAndLock(eq(paid.paymentId()), any(), eq(paid.userId())))
                .thenAnswer(
                        invocation -> new ParticipationReceipt(invocation.getArgument(1), null));
        when(repository.currentTime()).thenReturn(now);
        var result = service.startConfirmed(terms, paid);
        assertThat(result.outcome()).isEqualTo("applied");
        assertThat(result.currentSize()).isEqualTo(1);
        verify(repository).insertStartedDeal(any(), eq(paid), eq(now));
        verify(repository).complete(paid.paymentId(), result, paid.userId(), now);
    }

    /** Verifies a completed receipt short-circuits clock reads and initiation writes. */
    @Test
    void replayReturnsOriginalSnapshotWithoutReadingDeal() {
        var original = new ParticipationResult("applied", terms.id(), "open", 1, null);
        when(repository.reserveAndLock(eq(paid.paymentId()), any(), eq(paid.userId())))
                .thenAnswer(
                        invocation ->
                                new ParticipationReceipt(invocation.getArgument(1), original));
        assertThat(service.startConfirmed(terms, paid)).isEqualTo(original);
        verify(repository, never()).currentTime();
        verify(repository, never()).insertStartedDeal(any(), any(), any());
    }

    /** Verifies a different fingerprint is rejected before any clock-dependent application. */
    @Test
    void changedPaymentRequestCannotReuseReceipt() {
        when(repository.reserveAndLock(eq(paid.paymentId()), any(), eq(paid.userId())))
                .thenReturn(new ParticipationReceipt("different", null));
        assertThatThrownBy(() -> service.startConfirmed(terms, paid))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ParticipationErrorCode.PAYMENT_REUSED);
        verify(repository, never()).currentTime();
    }

    /**
     * Verifies receipt-first locking precedes time and membership reads for final-place admission.
     */
    @Test
    void joinReadsTimeAndMembershipAfterDealLock() {
        var leader =
                new PaidParticipation(UUID.randomUUID(), UUID.randomUUID(), paid.amount(), "sgd");
        var deal = new StandardPaidGroupDealCreator().initiate(terms, leader, now.minusSeconds(1));
        pendingReceipt();
        when(repository.lockDeal(terms.id())).thenReturn(Optional.of(deal));
        when(repository.currentTime()).thenReturn(now);
        var result = service.joinConfirmed(terms.id(), paid);
        assertThat(result.dealStatus()).isEqualTo("succeeded");
        var order = inOrder(repository);
        order.verify(repository).reserveAndLock(eq(paid.paymentId()), any(), eq(paid.userId()));
        order.verify(repository).lockDeal(terms.id());
        order.verify(repository).currentTime();
        order.verify(repository).hasActiveMember(terms.id(), paid.userId());
        order.verify(repository).insertJoinedMember(eq(deal), any(), eq(paid), eq(now));
    }

    /** Verifies an absent deal records its stable rejection without inserting a member. */
    @Test
    void paidRejectionIsDurableCompensationNotRefund() {
        pendingReceipt();
        when(repository.lockDeal(terms.id())).thenReturn(Optional.empty());
        when(repository.currentTime()).thenReturn(now);
        var result = service.joinConfirmed(terms.id(), paid);
        assertThat(result.outcome()).isEqualTo("compensation_required");
        assertThat(result.reasonCode()).isEqualTo("GROUPBUY_404_001");
        verify(repository).complete(paid.paymentId(), result, paid.userId(), now);
        verify(repository, never()).insertJoinedMember(any(), any(), any(), any());
        verify(events, never()).publishEvent(any(TeamSucceeded.class));
    }

    /** Verifies storage failure propagates rather than recording a misleading terminal receipt. */
    @Test
    void technicalFailureEscapesWithoutCompletingReceipt() {
        pendingReceipt();
        when(repository.currentTime()).thenReturn(now);
        doThrow(new IllegalStateException("database failure"))
                .when(repository)
                .insertStartedDeal(any(), any(), any());
        assertThatThrownBy(() -> service.startConfirmed(terms, paid))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).complete(any(), any(), any(), any());
    }

    /** Verifies initiation at expiry records compensation without creating an aggregate. */
    @Test
    void expiredStartPersistsCompensationWithoutCreatingDeal() {
        pendingReceipt();
        when(repository.currentTime()).thenReturn(terms.expireTime());
        var result = service.startConfirmed(terms, paid);
        assertThat(result.outcome()).isEqualTo("compensation_required");
        verify(repository).complete(paid.paymentId(), result, paid.userId(), terms.expireTime());
        verify(repository, never()).insertStartedDeal(any(), any(), any());
    }

    /** Verifies another payment from an existing buyer records duplicate-member compensation. */
    @Test
    void existingMemberWithAnotherPaymentGetsCompensation() {
        var leader =
                new PaidParticipation(UUID.randomUUID(), UUID.randomUUID(), paid.amount(), "sgd");
        var deal = new StandardPaidGroupDealCreator().initiate(terms, leader, now.minusSeconds(1));
        pendingReceipt();
        when(repository.lockDeal(terms.id())).thenReturn(Optional.of(deal));
        when(repository.currentTime()).thenReturn(now);
        when(repository.hasActiveMember(terms.id(), paid.userId())).thenReturn(true);
        var result = service.joinConfirmed(terms.id(), paid);
        assertThat(result.reasonCode()).isEqualTo("GROUPBUY_409_003");
        verify(repository, never()).insertJoinedMember(any(), any(), any(), any());
        verify(repository).complete(paid.paymentId(), result, paid.userId(), now);
    }

    /** Verifies equivalent exact-cent amounts produce the same request fingerprint. */
    @Test
    void sameCentsHaveIdenticalCanonicalFingerprint() {
        pendingReceipt();
        when(repository.currentTime()).thenReturn(now);
        service.startConfirmed(terms, paid);
        service.startConfirmed(
                terms,
                new PaidParticipation(
                        paid.paymentId(), paid.userId(), new BigDecimal("12.000"), "sgd"));
        var fingerprints = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2))
                .reserveAndLock(eq(paid.paymentId()), fingerprints.capture(), eq(paid.userId()));
        assertThat(fingerprints.getAllValues().get(0))
                .isEqualTo(fingerprints.getAllValues().get(1));
    }

    /** Verifies null initiation facts fail fast with a business error before any clock read. */
    @Test
    void startConfirmedRejectsNullTermsOrPayment() {
        assertThatThrownBy(() -> service.startConfirmed(null, paid))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION);
        assertThatThrownBy(() -> service.startConfirmed(terms, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION);
        verify(repository, never()).currentTime();
    }

    /** Verifies null join facts fail fast with a business error before any lock is taken. */
    @Test
    void joinConfirmedRejectsNullDealIdOrPayment() {
        assertThatThrownBy(() -> service.joinConfirmed(null, paid))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION);

        UUID dealId = terms.id();
        assertThatThrownBy(() -> service.joinConfirmed(dealId, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(GroupbuyErrorCode.INVALID_PARTICIPATION);
        verify(repository, never()).currentTime();
    }

    /** Verifies a completed join receipt replays without locking the deal or clock. */
    @Test
    void joinReplayReturnsOriginalSnapshotWithoutLockingDeal() {
        var original = new ParticipationResult("applied", terms.id(), "open", 2, null);
        when(repository.reserveAndLock(eq(paid.paymentId()), any(), eq(paid.userId())))
                .thenAnswer(
                        invocation ->
                                new ParticipationReceipt(invocation.getArgument(1), original));

        assertThat(service.joinConfirmed(terms.id(), paid)).isEqualTo(original);
        verify(repository, never()).lockDeal(any());
        verify(repository, never()).currentTime();
        verify(repository, never()).insertJoinedMember(any(), any(), any(), any());
        verify(events, never()).publishEvent(any(TeamSucceeded.class));
    }

    /**
     * Stubs a newly reserved receipt that echoes the request fingerprint with no terminal result.
     */
    private void pendingReceipt() {
        when(repository.reserveAndLock(eq(paid.paymentId()), any(), eq(paid.userId())))
                .thenAnswer(
                        invocation -> new ParticipationReceipt(invocation.getArgument(1), null));
    }

    /** Verifies a different fingerprint is rejected on join before any membership read. */
    @Test
    void joinChangedPaymentCannotReuseReceipt() {
        when(repository.reserveAndLock(eq(paid.paymentId()), any(), eq(paid.userId())))
                .thenReturn(new ParticipationReceipt("different", null));

        UUID dealId = terms.id();
        assertThatThrownBy(() -> service.joinConfirmed(dealId, paid))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ParticipationErrorCode.PAYMENT_REUSED);
        verify(repository, never()).lockDeal(any());
        verify(repository, never()).currentTime();
    }

    /** Verifies a join storage failure propagates rather than recording a terminal receipt. */
    @Test
    void joinTechnicalFailureEscapesWithoutCompletingReceipt() {
        var leader =
                new PaidParticipation(UUID.randomUUID(), UUID.randomUUID(), paid.amount(), "sgd");
        var deal = new StandardPaidGroupDealCreator().initiate(terms, leader, now.minusSeconds(1));
        pendingReceipt();
        when(repository.lockDeal(terms.id())).thenReturn(Optional.of(deal));
        when(repository.currentTime()).thenReturn(now);
        doThrow(new IllegalStateException("database failure"))
                .when(repository)
                .insertJoinedMember(any(), any(), any(), any());

        UUID dealId = terms.id();
        assertThatThrownBy(() -> service.joinConfirmed(dealId, paid))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).complete(any(), any(), any(), any());
    }

    /** Verifies distinct payment facts cannot share a request fingerprint. */
    @Test
    void differentAmountsHaveDifferentFingerprints() {
        pendingReceipt();
        when(repository.currentTime()).thenReturn(now);
        service.startConfirmed(terms, paid);
        var dearer =
                new PaidParticipation(
                        paid.paymentId(), paid.userId(), new BigDecimal("13.00"), "sgd");
        service.startConfirmed(terms, dearer);

        var fingerprints = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2))
                .reserveAndLock(eq(paid.paymentId()), fingerprints.capture(), eq(paid.userId()));
        assertThat(fingerprints.getAllValues().get(0))
                .isNotEqualTo(fingerprints.getAllValues().get(1));
    }

    /** Verifies expiry produces a durable closed-deal reason and no membership transition. */
    @Test
    void expiredJoinPersistsClosedReasonWithoutChangingMembership() {
        var leader =
                new PaidParticipation(UUID.randomUUID(), UUID.randomUUID(), paid.amount(), "sgd");
        var deal = new StandardPaidGroupDealCreator().initiate(terms, leader, now);
        pendingReceipt();
        when(repository.lockDeal(terms.id())).thenReturn(Optional.of(deal));
        when(repository.currentTime()).thenReturn(terms.expireTime());
        var result = service.joinConfirmed(terms.id(), paid);
        assertThat(result.outcome()).isEqualTo("compensation_required");
        assertThat(result.reasonCode()).isEqualTo("GROUPBUY_409_002");
        assertThat(result.currentSize()).isNull();
        verify(repository).complete(paid.paymentId(), result, paid.userId(), terms.expireTime());
        verify(repository, never()).insertJoinedMember(any(), any(), any(), any());
    }

    @Test
    void joinBeforeTargetPersistsOpenProgressAndReceipt() {
        var threePersonTerms =
                new NewDealTerms(
                        terms.id(),
                        terms.dealNo(),
                        terms.productId(),
                        terms.price(),
                        terms.currency(),
                        3,
                        terms.expireTime());
        var leader =
                new PaidParticipation(UUID.randomUUID(), UUID.randomUUID(), paid.amount(), "sgd");
        var deal =
                new StandardPaidGroupDealCreator()
                        .initiate(threePersonTerms, leader, now.minusSeconds(1));
        pendingReceipt();
        when(repository.lockDeal(terms.id())).thenReturn(Optional.of(deal));
        when(repository.currentTime()).thenReturn(now);

        var result = service.joinConfirmed(terms.id(), paid);

        assertThat(result.outcome()).isEqualTo("applied");
        assertThat(result.dealStatus()).isEqualTo("open");
        assertThat(result.currentSize()).isEqualTo(2);
        verify(repository).insertJoinedMember(eq(deal), any(), eq(paid), eq(now));
        verify(repository).complete(paid.paymentId(), result, paid.userId(), now);
        verify(events, never()).publishEvent(any(TeamSucceeded.class));
    }

    /** The team that fills up announces it; the consequences belong to subscribers, not to us. */
    @Test
    void joinReachingTargetPublishesTeamSucceededWithTheWrittenSnapshot() {
        var leader =
                new PaidParticipation(UUID.randomUUID(), UUID.randomUUID(), paid.amount(), "sgd");
        var deal = new StandardPaidGroupDealCreator().initiate(terms, leader, now.minusSeconds(1));
        pendingReceipt();
        when(repository.lockDeal(terms.id())).thenReturn(Optional.of(deal));
        when(repository.currentTime()).thenReturn(now);

        var result = service.joinConfirmed(terms.id(), paid);

        assertThat(result.dealStatus()).isEqualTo("succeeded");
        var published = ArgumentCaptor.forClass(TeamSucceeded.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue())
                .isEqualTo(new TeamSucceeded(terms.id(), terms.productId(), 2, now));
        var persistedThenPublished = inOrder(repository, events);
        var written = ArgumentCaptor.forClass(GroupDeal.class);
        persistedThenPublished
                .verify(repository)
                .insertJoinedMember(eq(deal), written.capture(), eq(paid), eq(now));
        assertThat(written.getValue().status()).isEqualTo(DealStatus.SUCCEEDED);
        assertThat(written.getValue().successTime()).isEqualTo(now);
        persistedThenPublished
                .verify(repository)
                .complete(paid.paymentId(), result, paid.userId(), now);
        persistedThenPublished.verify(events).publishEvent(any(TeamSucceeded.class));
    }

    /** An initiated team is never full on its own, so creation announces nothing. */
    @Test
    void startNeverPublishesTeamSucceeded() {
        when(repository.reserveAndLock(eq(paid.paymentId()), any(), eq(paid.userId())))
                .thenAnswer(
                        invocation -> new ParticipationReceipt(invocation.getArgument(1), null));
        when(repository.currentTime()).thenReturn(now);

        service.startConfirmed(terms, paid);

        verify(events, never()).publishEvent(any(Object.class));
    }
}
