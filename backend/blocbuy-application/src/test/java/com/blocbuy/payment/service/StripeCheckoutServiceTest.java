package com.blocbuy.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.CheckoutQuote;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class StripeCheckoutServiceTest {
    private static final UUID BUYER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID INTENT = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private final CheckoutPaymentRepository repository = mock(CheckoutPaymentRepository.class);
    private final CheckoutGateway gateway = mock(CheckoutGateway.class);
    private final PaymentBusinessPort business = mock(PaymentBusinessPort.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final StripeCheckoutService service =
            service("https://shop.example/return?from=checkout#receipt");
    private CheckoutPayment saved;

    private StripeCheckoutService service(String url) {
        return new StripeCheckoutService(repository, gateway, business, transactions, url, url);
    }

    private CheckoutQuote quote(UUID buyer, UUID intent, int validity) {
        return new CheckoutQuote(
                intent,
                buyer,
                "START",
                BUYER,
                new BigDecimal("10"),
                "sgd",
                NOW.plusSeconds(validity),
                NOW.plusSeconds(7200));
    }

    @BeforeEach
    void setup() {
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(repository.now()).thenReturn(NOW);
        when(business.lockQuote(any(), any())).thenReturn(quote(BUYER, INTENT, 3600));
        doAnswer(
                        call -> {
                            saved = call.getArgument(0);
                            return null;
                        })
                .when(repository)
                .insert(any());
        doAnswer(
                        call -> {
                            saved = call.getArgument(0);
                            return null;
                        })
                .when(repository)
                .save(any());
        when(repository.findById(any())).thenAnswer(call -> Optional.ofNullable(saved));
        when(repository.lock(any())).thenAnswer(call -> saved);
        when(gateway.createCheckoutSession(any()))
                .thenAnswer(call -> session("open", "unpaid", null, null));
        when(gateway.retrieveCheckoutSession(any()))
                .thenAnswer(call -> session("open", "unpaid", null, null));
        when(repository.claimEvent(any(), any())).thenReturn(true);
        when(business.fulfil(any()))
                .thenReturn(new PaymentBusinessPort.Outcome(BUYER, INTENT, null));
        when(business.fulfilmentEvidence(any()))
                .thenReturn(PaymentBusinessPort.FulfilmentEvidence.NONE);
    }

    private CheckoutGateway.Session session(
            String state, String paid, String intent, String intentState) {
        return new CheckoutGateway.Session(
                "cs_test_fixture",
                saved.id().toString(),
                INTENT.toString(),
                intent,
                1000,
                "sgd",
                state,
                paid,
                false,
                "https://checkout.stripe.com/c/pay/test",
                saved.sessionExpiresAt(),
                intentState);
    }

    private UUID prepare() {
        return service.prepare(BUYER, INTENT, "key").paymentId();
    }

    @Test
    void returnKeepsExistingQueryAndFragmentAndRetryReusesSavedSession() {
        UUID id = prepare();
        assertThat(saved.successUrl())
                .isEqualTo(
                        "https://shop.example/return?from=checkout&paymentId=" + id + "#receipt");
        when(repository.findRequest(BUYER, "key")).thenReturn(Optional.of(saved));
        assertThat(service.prepare(BUYER, INTENT, "key").paymentId()).isEqualTo(id);
        verify(gateway, times(1)).createCheckoutSession(any());
    }

    @Test
    void quoteMustMatchBothAuthenticatedBuyerAndIntent() {
        when(business.lockQuote(any(), any())).thenReturn(quote(INTENT, INTENT, 3600));
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                .isInstanceOf(BizException.class);
        when(business.lockQuote(any(), any())).thenReturn(quote(BUYER, BUYER, 3600));
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                .isInstanceOf(BizException.class);
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1800})
    void expiredOrTooShortQuoteCannotCreateCheckout(int validity) {
        when(business.lockQuote(any(), any())).thenReturn(quote(BUYER, INTENT, validity));
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                .isInstanceOf(BizException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void uncertainCreationAfterRetryWindowIsReviewedNotSentAgain() {
        doThrow(new IllegalStateException("timeout")).when(gateway).createCheckoutSession(any());
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                .isInstanceOf(IllegalStateException.class);
        when(repository.findRequest(BUYER, "key")).thenReturn(Optional.of(saved));
        when(repository.now()).thenReturn(NOW.plusSeconds(55));
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                .isInstanceOf(BizException.class);
        verify(repository).review(saved.id(), "CREATE_UNKNOWN", "system");
        verify(gateway, times(1)).createCheckoutSession(any());
    }

    @Test
    void differentRequestCannotBypassAnExistingAttempt() {
        prepare();
        when(repository.findBlockingAttempt(INTENT)).thenReturn(Optional.of(saved));
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "new-key"))
                .isInstanceOf(BizException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/relative",
                "https://user:pass@shop.example/return",
                "http://shop.example/return",
                "ftp://localhost/return"
            })
    void serverReturnUrlRejectsUnsafeSchemesAndCredentials(String url) {
        assertThatThrownBy(() -> service(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost/return", "http://127.0.0.1/return"})
    void localLoopbackReturnIsAllowed(String url) {
        assertThat(service(url)).isNotNull();
    }

    @Test
    void unknownPaymentCannotBeInspected() {
        assertThatThrownBy(() -> service.inspect(INTENT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void inspectRejectsNullAndDifferentProviderSession() {
        UUID id = prepare();
        when(gateway.retrieveCheckoutSession(any())).thenReturn(null);
        assertThatThrownBy(() -> service.inspect(id)).isInstanceOf(BizException.class);
        var wrong = changed("session");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(wrong);
        assertThatThrownBy(() -> service.inspect(id)).isInstanceOf(BizException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bad operator"})
    void invalidOperatorCannotRecoverOrAssign(String operator) {
        assertThatThrownBy(() -> service.recover(INTENT, operator, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.assignReview(INTENT, operator, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewDeadlineMustBePresentAndWithinNextDay() {
        Instant late = NOW.plusSeconds(86401);
        assertThatThrownBy(() -> service.assignReview(INTENT, "yorick", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.assignReview(INTENT, "yorick", late))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"live", "account"})
    void webhooksFromWrongEnvironmentOrConnectedAccountAreRejected(String kind) {
        when(gateway.verifyCheckoutWebhook(any(), any()))
                .thenReturn(
                        Optional.of(
                                new CheckoutGateway.Event(
                                        "evt_test",
                                        "completed",
                                        "cs_test_fixture",
                                        kind.equals("live"),
                                        kind.equals("account") ? "acct_other" : null)));
        assertThatThrownBy(() -> service.handleWebhook("body", "sig"))
                .isInstanceOf(BizException.class);
        verify(gateway, never()).retrieveCheckoutSession(any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "live",
                "payment",
                "intent",
                "amount",
                "currency",
                "expiry",
                "session",
                "provider"
            })
    void mismatchedProviderFactsCannotProduceAUsablePaymentLink(String field) {
        prepare();
        saved = saved.bind("cs_test_fixture", null, "pi_original");
        when(repository.findRequest(BUYER, "key")).thenReturn(Optional.of(saved));
        var mismatch = changed(field);
        when(gateway.retrieveCheckoutSession(any())).thenReturn(mismatch);
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void differentWebhookSessionIdCannotBeApplied() {
        prepare();
        when(gateway.verifyCheckoutWebhook(any(), any()))
                .thenReturn(
                        Optional.of(
                                new CheckoutGateway.Event(
                                        "evt_test", "completed", "cs_test_foreign", false, null)));
        assertThatThrownBy(() -> service.handleWebhook("body", "sig"))
                .isInstanceOf(BizException.class);
        verify(repository, never()).claimEvent(any(), any());
    }

    @Test
    void standaloneInspectUsesSavedProviderBinding() {
        UUID id = prepare();
        assertThat(service.inspect(id).id()).isEqualTo("cs_test_fixture");
    }

    @Test
    void recoveryRejectsUntrustedLookupHintsAndRecordsFailure() {
        UUID id = prepare();
        assertThatThrownBy(() -> service.recover(id, "yorick", true, "not-a-session"))
                .isInstanceOf(BizException.class);
        verify(repository)
                .recordRecovery(
                        eq(id),
                        eq("yorick"),
                        eq("UNVERIFIED"),
                        contains("none-or-invalid"),
                        eq(false));
    }

    @ParameterizedTest
    @CsvSource({
        "open,unpaid,,,false,OBSERVE_OR_REVIEW_CONFLICTING_FACTS",
        "expired,unpaid,pi_x,canceled,false,FAIL_VERIFIED_TERMINAL_UNPAID_ATTEMPT",
        "complete,paid,pi_x,succeeded,false,VERIFY_FULFILMENT_OR_RECORD_COMPENSATION",
        "expired,unpaid,,,false,CANCEL_VERIFIED_UNPAID_ATTEMPT",
        "expired,paid,,,false,OBSERVE_OR_REVIEW_CONFLICTING_FACTS",
        "expired,unpaid,pi_x,processing,false,OBSERVE_OR_REVIEW_CONFLICTING_FACTS",
        "expired,unpaid,,,true,OBSERVE_OR_REVIEW_CONFLICTING_FACTS"
    })
    void previewExplainsAllAuthoritativeStatesWithoutWrites(
            String state,
            String payment,
            String intent,
            String intentState,
            boolean localPaid,
            String action) {
        UUID id = prepare();
        if (localPaid) saved = saved.succeed();
        var snapshot = session(state, payment, intent, intentState);
        when(gateway.retrieveCheckoutSession(any())).thenReturn(snapshot);
        assertThat(service.preview(id).proposedAction()).isEqualTo(action);
        verify(repository, never()).claimEvent(any(), any());
    }

    @Test
    void previewShowsMismatchedAmountWithoutWritingIt() {
        UUID id = prepare();
        var snapshot = changed("amount");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(snapshot);
        assertThat(service.preview(id).proposedAction()).isEqualTo("REVIEW_PROVIDER_MISMATCH");
        verify(repository, never()).claimEvent(any(), any());
    }

    @Test
    void completedAttemptCannotBeReusedWithSameRequestKey() {
        prepare();
        saved = saved.succeed();
        when(repository.findRequest(BUYER, "key")).thenReturn(Optional.of(saved));
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                .isInstanceOf(BizException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"paid", "pending"})
    void alreadyClosedSessionsNeedNoSecondExpiryCall(String kind) {
        UUID id = prepare();
        var snapshot =
                session("expired", kind.equals("paid") ? "paid" : "unpaid", "pi_x", "processing");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(snapshot);
        service.expireRequestedSession(id);
        when(repository.pendingBefore(any(), anyInt())).thenReturn(List.of(saved));
        when(repository.now()).thenReturn(NOW.plusSeconds(8000));
        assertThat(service.expireDueSessions()).isEqualTo(1);
        verify(gateway, never()).expireCheckoutSession(any());
    }

    @Test
    void explicitExpiryRejectsMismatchedSession() {
        UUID id = prepare();
        var snapshot = changed("amount");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(snapshot);
        assertThatThrownBy(() -> service.expireRequestedSession(id))
                .isInstanceOf(BizException.class);
        verify(gateway, never()).expireCheckoutSession(any());
    }

    private CheckoutGateway.Session changed(String field) {
        return new CheckoutGateway.Session(
                field.equals("session") ? "cs_test_other" : "cs_test_fixture",
                field.equals("payment") ? "other" : saved.id().toString(),
                field.equals("intent") ? "other" : INTENT.toString(),
                field.equals("provider") ? "pi_other" : "pi_original",
                field.equals("amount") ? 1 : 1000,
                field.equals("currency") ? "usd" : "sgd",
                "open",
                "unpaid",
                field.equals("live"),
                "https://checkout.stripe.com/c/pay/test",
                field.equals("expiry") ? NOW : saved.sessionExpiresAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://checkout.stripe.com/test", "https://evil.example/test"})
    void checkoutUrlMustBeHostedByStripeOverHttps(String url) {
        doAnswer(
                        call ->
                                new CheckoutGateway.Session(
                                        "cs_test_fixture",
                                        saved.id().toString(),
                                        INTENT.toString(),
                                        null,
                                        1000,
                                        "sgd",
                                        "open",
                                        "unpaid",
                                        false,
                                        url,
                                        saved.sessionExpiresAt()))
                .when(gateway)
                .createCheckoutSession(any());
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                .isInstanceOf(BizException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"paid", "closed", "expired", "no-url"})
    void creationRaceNeverReturnsAUsableUrl(String race) {
        doAnswer(
                        call -> {
                            if (race.equals("paid")) saved = saved.succeed();
                            if (race.equals("expired"))
                                when(repository.now()).thenReturn(NOW.plusSeconds(8000));
                            return new CheckoutGateway.Session(
                                    "cs_test_fixture",
                                    saved.id().toString(),
                                    INTENT.toString(),
                                    null,
                                    1000,
                                    "sgd",
                                    race.equals("closed") ? "expired" : "open",
                                    "unpaid",
                                    false,
                                    race.equals("no-url")
                                            ? null
                                            : "https://checkout.stripe.com/test",
                                    saved.sessionExpiresAt());
                        })
                .when(gateway)
                .createCheckoutSession(any());
        assertThat(service.prepare(BUYER, INTENT, "key").checkoutUrl()).isNull();
    }

    @Test
    void expiryCleanupSkipsFreshAndUnboundAttempts() {
        doThrow(new IllegalStateException("timeout")).when(gateway).createCheckoutSession(any());
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                .isInstanceOf(IllegalStateException.class);
        when(repository.pendingBefore(any(), anyInt())).thenReturn(List.of(saved));
        assertThat(service.expireDueSessions()).isZero();
        when(repository.now()).thenReturn(NOW.plusSeconds(8000));
        assertThat(service.expireDueSessions()).isZero();
        UUID id = saved.id();
        assertThatThrownBy(() -> service.expireRequestedSession(id))
                .isInstanceOf(BizException.class);
    }

    @Test
    void overduePaidCheckoutRequiresCompensationNotFulfilment() {
        UUID id = prepare();
        var paid = session("complete", "paid", "pi_original", "succeeded");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(paid);
        when(repository.now()).thenReturn(NOW.plusSeconds(8000));
        when(repository.refundRecorded(id)).thenReturn(true);
        service.recover(id, "yorick", true);
        verify(business, never()).fulfil(any());
        verify(repository).requireRefund(any(), eq("BUSINESS_EXPIRED"), anyString());
        verify(repository)
                .recordRecovery(eq(id), eq("yorick"), eq("REFUND_REQUIRED"), anyString(), eq(true));
    }

    @Test
    void teamExpiryCannotTreatAnUnpaidMemberAsRefundable() {
        prepare();
        when(repository.paidMembers(INTENT)).thenReturn(List.of(saved));
        assertThatThrownBy(() -> service.recordExpiredTeam(INTENT))
                .isInstanceOf(BizException.class);
        verify(repository, never()).requireRefund(any(), any(), any());
    }
}
