package com.blocbuy.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.gateway.RefundGateway;
import com.blocbuy.payment.model.RefundWork;
import com.blocbuy.payment.repository.RefundRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Bounded refund jobs: claiming, provider reconciliation and operator review. */
class RefundServiceTest {
    @Test
    void manualRecoveryOnlyReadsAndRejectsInvalidReferences() {
        RefundWork work = work(null, 24);
        when(repository.claimReview(work.paymentId())).thenReturn(work);
        when(gateway.retrieveRefund("re_test")).thenReturn(snapshot(work, "succeeded"));
        assertThat(service.reconcile(work.paymentId(), "re_test")).isTrue();
        verify(gateway, never()).createRefund(any());
        verify(repository).finish(work, "re_test", "succeeded", "PROVIDER_SUCCEEDED");
        var exceptionInput1 = work.paymentId();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.reconcile(exceptionInput1, "not-refund"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.reconcile(UUID.randomUUID(), "re_test")).isFalse();
    }

    @Test
    void manualRecoveryFailureKeepsReviewOpen() {
        RefundWork work = work(null, 24);
        when(repository.claimReview(work.paymentId())).thenReturn(work);
        when(gateway.retrieveRefund("re_test"))
                .thenThrow(new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR));
        service.reconcile(work.paymentId(), "re_test");
        verify(repository).finish(work, null, "review", "OPERATOR_PROVIDER_RETRY");
    }

    private final RefundRepository repository = mock(RefundRepository.class);
    private final RefundGateway gateway = mock(RefundGateway.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final RefundService service = new RefundService(repository, gateway, transactions);

    private final Logger serviceLog = (Logger) LoggerFactory.getLogger(RefundService.class);
    private final ListAppender<ILoggingEvent> reviewWarnings = new ListAppender<>();

    RefundServiceTest() {
        when(transactions.getTransaction(any())).thenAnswer(a -> new SimpleTransactionStatus());
    }

    @BeforeEach
    void captureLogs() {
        reviewWarnings.start();
        serviceLog.addAppender(reviewWarnings);
    }

    @AfterEach
    void releaseLogs() {
        serviceLog.detachAppender(reviewWarnings);
        reviewWarnings.stop();
    }

    private RefundWork work(String ref, int ageHours) {
        Instant now = Instant.now();
        RefundWork work =
                new RefundWork(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("10.00"),
                        "sgd",
                        "pi_test",
                        "succeeded",
                        new BigDecimal("10.00"),
                        "sgd",
                        ref,
                        now.minusSeconds(ageHours * 3600L),
                        now,
                        1);
        when(repository.now()).thenReturn(now);
        when(repository.claimNext()).thenReturn(work);
        return work;
    }

    private RefundGateway.Snapshot snapshot(RefundWork work, String status) {
        return new RefundGateway.Snapshot(
                "re_test", "pi_test", work.id().toString(), 1000, "sgd", status);
    }

    @ParameterizedTest
    @CsvSource({
        "succeeded,succeeded",
        "pending,processing",
        "failed,failed",
        "canceled,failed",
        "requires_action,review"
    })
    void persistsOnlyVerifiedProviderState(String provider, String expected) {
        RefundWork work = work(null, 0);
        when(gateway.createRefund(work)).thenReturn(snapshot(work, provider));
        assertThat(service.processNext()).isTrue();
        verify(repository)
                .finish(
                        work,
                        "re_test",
                        expected,
                        "PROVIDER_" + expected.toUpperCase(java.util.Locale.ROOT));
        assertThat(work.providerKey()).isEqualTo("blocbuy-refund-" + work.id());
    }

    @Test
    void idleWorkerMakesNoProviderCall() {
        assertThat(service.processNext()).isFalse();
        verifyNoInteractions(gateway);
    }

    @Test
    void knownRefundUsesReadOnlyRecovery() {
        RefundWork work = work("re_test", 72);
        when(gateway.retrieveRefund("re_test")).thenReturn(snapshot(work, "succeeded"));
        service.processNext();
        verify(gateway, never()).createRefund(any());
        verify(repository).finish(work, "re_test", "succeeded", "PROVIDER_SUCCEEDED");
    }

    @Test
    void lostResponseRetriesSameDurableKey() {
        RefundWork work = work(null, 1);
        when(gateway.createRefund(work))
                .thenThrow(new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR))
                .thenReturn(snapshot(work, "succeeded"));
        service.processNext();
        verify(repository).finish(work, null, "processing", "PROVIDER_RETRY");
        service.processNext();
        verify(gateway, times(2)).createRefund(work);
        verify(repository).finish(work, "re_test", "succeeded", "PROVIDER_SUCCEEDED");
    }

    @Test
    void oldUnknownSearchesWithoutCreatingAgain() {
        RefundWork work = work(null, 24);
        service.processNext();
        verify(gateway).findRefund(work);
        verify(gateway, never()).createRefund(any());
        verify(repository).finish(work, null, "review", "UNKNOWN_AFTER_RETRY_WINDOW");
    }

    @Test
    void retryCutoffUsesFreshDatabaseTimeRatherThanClaimTimestamp() {
        RefundWork work = work(null, 22);
        when(repository.now()).thenReturn(work.firstAttemptAt().plus(Duration.ofHours(24)));

        service.processNext();

        verify(gateway).findRefund(work);
        verify(gateway, never()).createRefund(any());
    }

    @Test
    void oldUnknownCanRecoverByMetadata() {
        RefundWork work = work(null, 24);
        when(gateway.findRefund(work)).thenReturn(snapshot(work, "succeeded"));
        service.processNext();
        verify(repository).finish(work, "re_test", "succeeded", "PROVIDER_SUCCEEDED");
    }

    @Test
    void mismatchedAmountIsNotRefundSuccess() {
        RefundWork work = work(null, 0);
        when(gateway.createRefund(work))
                .thenReturn(
                        new RefundGateway.Snapshot(
                                "re_test", "pi_test", work.id().toString(), 1, "sgd", "succeeded"));
        service.processNext();
        verify(repository).finish(work, null, "review", "PROVIDER_MISMATCH");
    }

    @Test
    void invalidPaymentNeverCallsStripe() {
        RefundWork work = work(null, 0);
        RefundWork unpaid =
                new RefundWork(
                        work.id(),
                        work.paymentId(),
                        work.amount(),
                        work.currency(),
                        "pi_test",
                        "created",
                        work.paidAmount(),
                        work.paidCurrency(),
                        null,
                        work.firstAttemptAt(),
                        work.now(),
                        1);
        when(repository.claimNext()).thenReturn(unpaid);
        service.processNext();
        verifyNoInteractions(gateway);
        verify(repository).finish(unpaid, null, "review", "PAYMENT_MISMATCH");
        when(repository.claimReview(work.paymentId())).thenReturn(unpaid);
        assertThat(service.reconcile(work.paymentId(), "re_test")).isTrue();
        verifyNoInteractions(gateway);
    }

    /**
     * A PaymentIntent that is not refundable does not become refundable by asking again. Parking it
     * for review is what stops the worker re-asking every 30 seconds for the next 23 hours (#132).
     */
    @Test
    void permanentProviderStateParksForReviewAndStopsCallingStripe() {
        RefundWork work = work(null, 0);
        when(gateway.createRefund(work))
                .thenThrow(new BizException(PaymentErrorCode.PAYMENT_STATE_INVALID));

        assertThat(service.processNext()).isTrue();

        verify(repository).finish(work, null, "review", "PROVIDER_STATE_MISMATCH");
        verify(gateway, times(1)).createRefund(work);
        verify(gateway, never()).retrieveRefund(any());
        verify(gateway, never()).findRefund(any());
    }

    /** The same split under operator recovery keeps the row in review, only naming the reason. */
    @Test
    void permanentProviderStateUnderRecoveryIsNamedAsSuch() {
        RefundWork work = work(null, 24);
        when(repository.claimReview(work.paymentId())).thenReturn(work);
        when(gateway.retrieveRefund("re_test"))
                .thenThrow(new BizException(PaymentErrorCode.PAYMENT_STATE_INVALID));

        assertThat(service.reconcile(work.paymentId(), "re_test")).isTrue();

        verify(repository).finish(work, null, "review", "OPERATOR_PROVIDER_STATE_MISMATCH");
    }

    /**
     * A permanent provider refusal parks for an operator with a reason distinct from a
     * PaymentIntent state mismatch (#539). Credential failures remain retryable.
     */
    @Test
    void permanentProviderRejectionParksForReviewAndStopsCallingStripe() {
        RefundWork work = work(null, 0);
        when(gateway.createRefund(work))
                .thenThrow(new BizException(PaymentErrorCode.PAYMENT_PROVIDER_REJECTED));

        assertThat(service.processNext()).isTrue();

        verify(repository).finish(work, null, "review", "PROVIDER_REJECTED");
        verify(repository, never()).finish(any(), any(), eq("processing"), any());
        verify(gateway, times(1)).createRefund(work);
        verify(gateway, never()).retrieveRefund(any());
        verify(gateway, never()).findRefund(any());
        assertThat(reviewWarnings.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "Refund needs operator review: paymentId="
                                + work.paymentId()
                                + ", state=review, result=PROVIDER_REJECTED");
    }

    /** A rejection while re-reading a known refund keeps that refund id on the parked row. */
    @Test
    void permanentRejectionOnKnownRefundParks() {
        RefundWork work = work("re_test", 72);
        when(gateway.retrieveRefund("re_test"))
                .thenThrow(new BizException(PaymentErrorCode.PAYMENT_PROVIDER_REJECTED));

        assertThat(service.processNext()).isTrue();

        verify(repository).finish(work, "re_test", "review", "PROVIDER_REJECTED");
        verify(gateway, never()).createRefund(any());
        verify(gateway, never()).findRefund(any());
    }

    /** Past the creation cutoff, a rejected metadata search parks the row too, never creating. */
    @Test
    void permanentRejectionAfterCutoffParksWithoutCreating() {
        RefundWork work = work(null, 24);
        when(gateway.findRefund(work))
                .thenThrow(new BizException(PaymentErrorCode.PAYMENT_PROVIDER_REJECTED));

        assertThat(service.processNext()).isTrue();

        verify(repository).finish(work, null, "review", "PROVIDER_REJECTED");
        verify(gateway, never()).createRefund(any());
    }

    /** Under operator recovery the row stays in review; only the recorded reason changes. */
    @Test
    void permanentProviderRejectionUnderRecoveryIsNamedAsSuch() {
        RefundWork work = work(null, 24);
        when(repository.claimReview(work.paymentId())).thenReturn(work);
        when(gateway.retrieveRefund("re_test"))
                .thenThrow(new BizException(PaymentErrorCode.PAYMENT_PROVIDER_REJECTED));

        assertThat(service.reconcile(work.paymentId(), "re_test")).isTrue();

        verify(repository).finish(work, null, "review", "OPERATOR_PROVIDER_REJECTED");
    }

    @Test
    void missingReferenceCannotStartRecovery() {
        UUID id = UUID.randomUUID();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.reconcile(id, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, gateway);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"null", "id", "status", "binding", "intent", "obligation", "currency"})
    void everyMismatchedRefundFactRequiresReview(String field) {
        RefundWork work = work("re_test", 0);
        var snapshot =
                field.equals("null")
                        ? null
                        : new RefundGateway.Snapshot(
                                field.equals("id")
                                        ? null
                                        : field.equals("binding") ? "re_other" : "re_test",
                                field.equals("intent") ? "pi_other" : "pi_test",
                                field.equals("obligation") ? "other" : work.id().toString(),
                                1000,
                                field.equals("currency") ? "usd" : "sgd",
                                field.equals("status") ? null : "succeeded");
        when(gateway.retrieveRefund("re_test")).thenReturn(snapshot);
        assertThat(service.processNext()).isTrue();
        verify(repository).finish(work, "re_test", "review", "PROVIDER_MISMATCH");
    }
}
