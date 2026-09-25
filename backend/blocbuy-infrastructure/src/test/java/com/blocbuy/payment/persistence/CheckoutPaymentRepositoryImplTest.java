package com.blocbuy.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.payment.model.CheckoutPayment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Checkout repository guards: absent rows, page bounds and write conflicts. */
class CheckoutPaymentRepositoryImplTest {
    private final PaymentMapper mapper = mock(PaymentMapper.class);
    private final CheckoutPaymentRepositoryImpl repository =
            new CheckoutPaymentRepositoryImpl(mapper);

    private BizPaymentDO row() {
        Instant now = Instant.parse("2026-09-20T12:00:00Z");
        var row = new BizPaymentDO();
        row.setId(UUID.randomUUID());
        row.setOrderId(UUID.randomUUID());
        row.setFulfilledDealId(UUID.randomUUID());
        row.setIntentId(UUID.randomUUID());
        row.setUserId(UUID.randomUUID());
        row.setOperation("START");
        row.setBusinessId(UUID.randomUUID());
        row.setAmount(new BigDecimal("10.00"));
        row.setCurrency("sgd");
        row.setQuoteValidUntil(now.plusSeconds(3600));
        row.setBusinessDeadline(now.plusSeconds(7200));
        row.setRequestKey("key-1");
        row.setCreateTime(now);
        row.setSessionExpiresAt(now.plusSeconds(1800));
        row.setSuccessUrl("https://shop.example/success");
        row.setCancelUrl("https://shop.example/cancel");
        row.setCheckoutSessionId("cs_test_fixture");
        row.setCheckoutUrl("https://checkout.stripe.com/c/pay/test");
        row.setProviderPaymentIntentId("pi_fixture");
        row.setStatus("created");
        row.setVersion(0);
        return row;
    }

    @Test
    void receiptReadsThePaymentRowOnce() {
        BizPaymentDO row = row();
        row.setFulfilmentResult("FULFILLED");
        when(mapper.receipt(row.getId())).thenReturn(row);

        var receipt = repository.receipt(row.getId()).orElseThrow();

        assertThat(receipt.userId()).isEqualTo(row.getUserId());
        assertThat(receipt.status()).isEqualTo(CheckoutPayment.Status.CREATED);
        assertThat(receipt.amount()).isEqualByComparingTo(row.getAmount());
        assertThat(receipt.currency()).isEqualTo(row.getCurrency());
        assertThat(receipt.outcome()).isEqualTo("FULFILLED");
        assertThat(receipt.dealId()).isEqualTo(row.getFulfilledDealId());
        assertThat(receipt.orderId()).isEqualTo(row.getOrderId());
        verify(mapper).receipt(row.getId());
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void missingReceiptCannotBeLocked() {
        UUID id = UUID.randomUUID();
        assertThat(repository.receipt(id)).isEmpty();
        assertThat(repository.findById(id)).isEmpty();
        assertThatThrownBy(() -> repository.lock(id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void presentRowsResolveReceiptPaymentAndLock() {
        BizPaymentDO row = row();
        UUID id = row.getId();
        UUID user = row.getUserId();
        when(mapper.find(id)).thenReturn(row);
        when(mapper.request(user, "key-1")).thenReturn(row);
        when(mapper.blocking(row.getIntentId())).thenReturn(row);
        when(mapper.lock(id)).thenReturn(row);
        Optional<CheckoutPayment> found = repository.findById(id);
        assertThat(found).hasValueSatisfying(payment -> assertThat(payment.id()).isEqualTo(id));
        assertThat(repository.findRequest(user, "key-1")).isPresent();
        assertThat(repository.findBlockingAttempt(row.getIntentId())).isPresent();
        assertThat(repository.lock(id).id()).isEqualTo(id);
    }

    @Test
    void writesRequireExactlyOneChangedRow() {
        BizPaymentDO row = row();
        CheckoutPayment payment = PaymentConverter.fromRow(row);
        when(mapper.insert(any())).thenReturn(1);
        when(mapper.save(any())).thenReturn(1);
        when(mapper.finishEvent(any(), any(), any())).thenReturn(1);
        repository.insert(payment);
        repository.save(payment);
        repository.finishEvent("evt_fixture", payment.id(), "DONE");
        when(mapper.insert(any())).thenReturn(0);
        assertThatThrownBy(() -> repository.insert(payment))
                .isInstanceOf(IllegalStateException.class);
        when(mapper.save(any())).thenReturn(2);
        assertThatThrownBy(() -> repository.save(payment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eventClaimReadsBothOutcomes() {
        assertThat(repository.claimEvent("evt_fixture", "webhook")).isFalse();
        when(mapper.claimEvent("evt_fixture", "webhook")).thenReturn(1);
        assertThat(repository.claimEvent("evt_fixture", "webhook")).isTrue();
    }

    @Test
    void reviewLifecycleDelegatesToMapper() {
        UUID id = UUID.randomUUID();
        UUID deal = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        Instant check = Instant.now();
        when(mapper.review(any(), any(), any())).thenReturn(1);
        when(mapper.openCase(any(), any())).thenReturn(1);
        when(mapper.assignCase(any(), any(), any())).thenReturn(1);
        when(mapper.recordRecovery(any(), any(), any(), anyBoolean())).thenReturn(1);
        when(mapper.outcome(any(), any(), any(), any())).thenReturn(1);
        when(mapper.refund(any(), any(), any())).thenReturn(1);
        repository.review(id, "REASON", "yorick");
        repository.assignReview(id, "yorick", check);
        repository.recordRecovery(id, "yorick", "FULFILLED", "summary", true);
        repository.recordOutcome(id, deal, order, "FULFILLED");
        repository.requireRefund(PaymentConverter.fromRow(row()), "BUSINESS_EXPIRED", "evt_1");
        verify(mapper).review(id, "REASON", "yorick");
        verify(mapper).openCase(id, "REASON");
        verify(mapper).assignCase(id, "yorick", check);
        verify(mapper).review(id, "CASE_ASSIGNED", "yorick");
        verify(mapper).recordRecovery(id, "yorick", "summary", true);
        verify(mapper).outcome(id, deal, order, "FULFILLED");
        verify(mapper).refund(any(), eq("BUSINESS_EXPIRED"), eq("evt_1"));
    }

    @Test
    void outcomeReadsDelegateToMapper() {
        UUID id = UUID.randomUUID();
        when(mapper.getOutcome(id)).thenReturn("FULFILLED");
        when(mapper.refundRecorded(id)).thenReturn(true);
        assertThat(repository.outcome(id)).isEqualTo("FULFILLED");
        assertThat(repository.refundRecorded(id)).isTrue();
        assertThat(repository.now()).isNull();
    }

    @Test
    void memberAndPendingScansMapRowsToPayments() {
        Instant threshold = Instant.now();
        when(mapper.pending(any(), anyInt())).thenReturn(List.of(row()));
        when(mapper.reviewCases(any(), anyInt())).thenReturn(List.of());
        assertThat(repository.pendingBefore(threshold, 10)).hasSize(1);
        assertThat(repository.reviewCases(null, 10)).isEmpty();
    }

    @Test
    void expiredTeamRefundRequiresEveryEligiblePaymentToBeRecorded() {
        UUID dealId = UUID.randomUUID();
        when(mapper.lockRefundableMemberIds(dealId))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
        when(mapper.refundExpiredTeam(dealId, "TEAM_EXPIRED", "event")).thenReturn(1);

        assertThatThrownBy(
                        () ->
                                repository.requireRefundsForExpiredTeam(
                                        dealId, "TEAM_EXPIRED", "event"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void scanAndReviewPageBoundsAreCheckedBeforeDatabaseAccess() {
        for (int limit : new int[] {0, 101}) {
            var exceptionInput1 = Instant.now();
            assertThatThrownBy(() -> repository.pendingBefore(exceptionInput1, limit))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> repository.reviewCases(null, limit))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void zeroUpdatedRowsCauseRetryableTransactionFailure() {
        var exceptionInput2 = UUID.randomUUID();
        assertThatThrownBy(() -> repository.finishEvent("evt_fixture", exceptionInput2, "DONE"))
                .isInstanceOf(IllegalStateException.class);
    }
}
