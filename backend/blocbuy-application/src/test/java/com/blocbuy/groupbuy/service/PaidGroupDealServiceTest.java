package com.blocbuy.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
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

class PaidGroupDealServiceTest {
    private final PaidGroupDealRepository repository = mock(PaidGroupDealRepository.class);
    private final PaidGroupDealService service = new PaidGroupDealService(repository);
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

    @Test
    void changedPaymentRequestCannotReuseReceipt() {
        when(repository.reserveAndLock(eq(paid.paymentId()), any(), eq(paid.userId())))
                .thenReturn(new ParticipationReceipt("different", null));
        assertThatThrownBy(() -> service.startConfirmed(terms, paid))
                .isInstanceOf(BizException.class);
        verify(repository, never()).currentTime();
    }

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
    }

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

    @Test
    void expiredStartPersistsCompensationWithoutCreatingDeal() {
        pendingReceipt();
        when(repository.currentTime()).thenReturn(terms.expireTime());
        var result = service.startConfirmed(terms, paid);
        assertThat(result.outcome()).isEqualTo("compensation_required");
        verify(repository).complete(paid.paymentId(), result, paid.userId(), terms.expireTime());
        verify(repository, never()).insertStartedDeal(any(), any(), any());
    }

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

    @Test
    void sameCentsHaveIdenticalCanonicalFingerprint() {
        pendingReceipt();
        when(repository.currentTime()).thenReturn(now);
        service.startConfirmed(terms, paid);
        service.startConfirmed(
                terms,
                new PaidParticipation(
                        paid.paymentId(), paid.userId(), new BigDecimal("12.000"), "sgd"));
        var fingerprints = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository, org.mockito.Mockito.times(2))
                .reserveAndLock(eq(paid.paymentId()), fingerprints.capture(), eq(paid.userId()));
        assertThat(fingerprints.getAllValues().get(0))
                .isEqualTo(fingerprints.getAllValues().get(1));
    }

    private void pendingReceipt() {
        when(repository.reserveAndLock(eq(paid.paymentId()), any(), eq(paid.userId())))
                .thenAnswer(
                        invocation -> new ParticipationReceipt(invocation.getArgument(1), null));
    }

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
}
