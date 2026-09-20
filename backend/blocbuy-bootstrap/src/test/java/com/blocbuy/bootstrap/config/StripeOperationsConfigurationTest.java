package com.blocbuy.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.PaymentReviewCase;
import com.blocbuy.payment.service.StripeCheckoutService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.support.GenericApplicationContext;

class StripeOperationsConfigurationTest {
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private final StripeCheckoutService service = mock(StripeCheckoutService.class);

    private void run(String... args) throws Exception {
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            new StripeOperationsConfiguration()
                    .stripeOperations(service, context)
                    .run(new DefaultApplicationArguments(args));
        }
    }

    @Test
    void scanReportsPendingPayments() throws Exception {
        var payment = mock(CheckoutPayment.class);
        when(payment.id()).thenReturn(ID);
        when(payment.status()).thenReturn(CheckoutPayment.Status.CREATED);
        when(service.scan()).thenReturn(List.of(payment));
        run("--payment-command=scan");
        verify(service).scan();
    }

    @Test
    void casesSupportsEmptyPageCursorAndAllAlertReasons() throws Exception {
        run("--payment-command=cases");
        verify(service).reviewCases(null);
        Instant future = Instant.now().plusSeconds(3600);
        when(service.reviewCases(ID))
                .thenReturn(
                        List.of(
                                review(null, "WAIT", future),
                                        review("yorick", "WAIT", Instant.EPOCH),
                                review("yorick", "RECOVERY_FAILED", future),
                                        review("yorick", "WAIT", future)));
        run("--payment-command=cases", "--after-case-id=" + ID);
        verify(service).reviewCases(ID);
    }

    private PaymentReviewCase review(String owner, String reason, Instant next) {
        return new PaymentReviewCase(ID, ID, reason, owner, "OPEN", null, next, null, null);
    }

    @Test
    void assignPassesExplicitOwnerAndDeadline() throws Exception {
        run(
                "--payment-command=assign",
                "--payment-id=" + ID,
                "--operator=yorick",
                "--next-check-at=2026-09-21T00:00:00Z");
        verify(service).assignReview(ID, "yorick", Instant.parse("2026-09-21T00:00:00Z"));
    }

    @Test
    void inspectIsReadOnlyAndAcceptsOptionalSessionHint() throws Exception {
        when(service.preview(any(), any()))
                .thenReturn(
                        new StripeCheckoutService.RecoveryPreview(
                                ID, "CREATED", "cs_test", "open", "unpaid", "OBSERVE"));
        run("--payment-command=inspect", "--payment-id=" + ID);
        run("--payment-command=inspect", "--payment-id=" + ID, "--session-id=cs_test");
        verify(service).preview(ID, null);
        verify(service).preview(ID, "cs_test");
        verify(service, never()).recover(any(), any(), anyBoolean(), any());
    }

    @Test
    void recoveryAndExpiryRequireExplicitConfirmation() throws Exception {
        run(
                "--payment-command=recover",
                "--payment-id=" + ID,
                "--operator=yorick",
                "--confirm-recovery=false");
        verify(service).recover(ID, "yorick", false, null);
        run(
                "--payment-command=recover",
                "--payment-id=" + ID,
                "--operator=yorick",
                "--confirm-recovery=true",
                "--session-id=cs_test");
        verify(service).recover(ID, "yorick", true, "cs_test");
        assertThatThrownBy(
                        () ->
                                run(
                                        "--payment-command=expire-open-sessions",
                                        "--confirm-expiry=false"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(service, never()).expireDueSessions();
        run("--payment-command=expire-open-sessions", "--confirm-expiry=true");
        verify(service).expireDueSessions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"--payment-command=", "--payment-command=unknown", "--other=value"})
    void invalidCommandsCannotPerformOperations(String option) {
        assertThatThrownBy(() -> run(option)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }

    @Test
    void duplicateOptionsAreRejected() {
        assertThatThrownBy(() -> run("--payment-command=scan", "--payment-command=cases"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }
}
