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
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Checkout orchestration: preparation, webhooks, recovery, expiry and review. */
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
        verify(gateway).createCheckoutSession(any());
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
        verify(gateway).createCheckoutSession(any());
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

    /**
     * A developer machine and the shared server pointing at the same Stripe sandbox endpoint is the
     * normal case, so events for payments this database never held arrive constantly. They are
     * ignored events: recorded and acknowledged, never answered with a failure the provider would
     * retry for three days (#139).
     */
    @Test
    void aWebhookForAPaymentThisDatabaseDoesNotHoldIsRecordedAndAcknowledged() {
        String foreign = deliverForeignPayment("evt_foreign", UUID.randomUUID().toString());

        assertThat(foreign).isEqualTo("evt_foreign");
        verify(repository).finishEvent("evt_foreign", null, "UNKNOWN_PAYMENT");
        verify(business, never()).lockForSettlement(any());
        verify(repository, never()).lock(any());
        verify(repository, never()).save(any());
    }

    /** Provider metadata that is not a UUID at all never reaches the settlement transaction. */
    @Test
    void aWebhookWhosePaymentReferenceIsNotAUuidIsRecordedAndAcknowledged() {
        deliverForeignPayment("evt_garbled", "not-a-uuid");

        verify(repository).finishEvent("evt_garbled", null, "UNKNOWN_PAYMENT");
        verify(business, never()).lockForSettlement(any());
        verify(repository, never()).lock(any());
        verify(repository, never()).save(any());
    }

    /** A duplicate delivery of such an event is deduplicated by the claim, like any other. */
    @Test
    void aRepeatedUnknownPaymentEventIsRecordedOnlyOnce() {
        when(repository.claimEvent("evt_garbled", "completed")).thenReturn(false);

        deliverForeignPayment("evt_garbled", "not-a-uuid");

        verify(repository, never()).finishEvent(any(), any(), any());
    }

    /**
     * Delivers one verified webhook whose session names a payment reference of the test's choosing,
     * and forgets the preparation that set the fixture up so the assertions see only what the
     * webhook itself did.
     */
    private String deliverForeignPayment(String eventId, String paymentReference) {
        prepare();
        CheckoutPayment ours = saved;
        when(gateway.retrieveCheckoutSession(any()))
                .thenReturn(
                        new CheckoutGateway.Session(
                                "cs_test_fixture",
                                paymentReference,
                                INTENT.toString(),
                                null,
                                1000,
                                "sgd",
                                "open",
                                "unpaid",
                                false,
                                "https://checkout.stripe.com/c/pay/test",
                                ours.sessionExpiresAt(),
                                null));
        when(gateway.verifyCheckoutWebhook(any(), any()))
                .thenReturn(
                        Optional.of(
                                new CheckoutGateway.Event(
                                        eventId, "completed", "cs_test_fixture", false, null)));
        saved = null;
        clearInvocations(business, repository);

        service.handleWebhook("body", "sig");
        return eventId;
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
    void reusedAttemptWithDifferentQuoteCannotBypassOwnership() {
        prepare();
        when(business.lockQuote(any(), any())).thenReturn(quote(BUYER, INTENT, 3500));
        when(repository.findRequest(BUYER, "key")).thenReturn(Optional.of(saved));
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                .isInstanceOf(BizException.class);
    }

    private void stubVerifiedWebhook(String eventId, String sessionId) {
        when(gateway.verifyCheckoutWebhook(any(), any()))
                .thenReturn(
                        Optional.of(
                                new CheckoutGateway.Event(
                                        eventId, "completed", sessionId, false, null)));
    }

    @Test
    void webhookObservesUnpaidSessionWithoutFulfilment() {
        UUID id = prepare();
        stubVerifiedWebhook("evt_unpaid", "cs_test_fixture");
        service.handleWebhook("body", "sig");
        verify(repository).finishEvent("evt_unpaid", id, "OBSERVE");
        verify(business, never()).fulfil(any());
        verify(repository, never()).requireRefund(any(), any(), any());
    }

    @Test
    void webhookDuplicateClaimIsIgnored() {
        prepare();
        stubVerifiedWebhook("evt_seen", "cs_test_fixture");
        when(repository.claimEvent(any(), any())).thenReturn(false);
        clearInvocations(repository);
        service.handleWebhook("body", "sig");
        verify(repository).claimEvent("evt_seen", "completed");
        verify(repository, never()).lock(any());
        verify(repository, never()).save(any());
    }

    @Test
    void webhookMismatchIsReviewedWithoutRecovery() {
        UUID id = prepare();
        stubVerifiedWebhook("evt_mismatch", "cs_test_fixture");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(changed("amount"));
        service.handleWebhook("body", "sig");
        verify(repository).review(id, "PROVIDER_MISMATCH", "webhook");
        verify(repository).finishEvent("evt_mismatch", id, "REVIEW");
        verify(repository, never()).recordRecovery(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void latePaidAttemptRequiresOperatorRecovery() {
        UUID id = prepare();
        var paid = session("complete", "paid", "pi_original", "succeeded");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(paid);
        saved = saved.cancelUnpaid();
        stubVerifiedWebhook("evt_late", "cs_test_fixture");
        service.handleWebhook("body", "sig");
        saved = saved.failTerminalUnpaid();
        stubVerifiedWebhook("evt_late_failed", "cs_test_fixture");
        service.handleWebhook("body", "sig");
        verify(repository, times(2)).review(id, "LATE_PAID_REQUIRES_RECOVERY", "webhook");
        verify(repository, times(2)).finishEvent(any(), eq(id), eq("REVIEW"));
    }

    @Test
    void existingOutcomeIsReusedWithoutCallingBusiness() {
        UUID id = prepare();
        var paid = session("complete", "paid", "pi_original", "succeeded");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(paid);
        when(repository.outcome(id)).thenReturn("FULFILLED");
        stubVerifiedWebhook("evt_reuse", "cs_test_fixture");
        service.handleWebhook("body", "sig");
        verify(repository).finishEvent("evt_reuse", id, "FULFILLED");
        verify(business, never()).fulfil(any());
        verify(repository, never()).requireRefund(any(), any(), any());
        verify(repository, never()).recordOutcome(any(), any(), any(), any());
    }

    @Test
    void acceptedFulfilmentRecordsOutcomeWithoutRefund() {
        UUID id = prepare();
        var paid = session("complete", "paid", "pi_original", "succeeded");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(paid);
        stubVerifiedWebhook("evt_fulfil", "cs_test_fixture");
        service.handleWebhook("body", "sig");
        verify(business).fulfil(any());
        verify(repository).recordOutcome(id, BUYER, INTENT, "FULFILLED");
        verify(repository, never()).requireRefund(any(), any(), any());
        verify(repository).finishEvent("evt_fulfil", id, "FULFILLED");
    }

    @Test
    void terminalUnpaidSessionFailsPaymentWithoutFulfilment() {
        prepare();
        var terminal = session("expired", "unpaid", "pi_x", "canceled");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(terminal);
        stubVerifiedWebhook("evt_terminal", "cs_test_fixture");
        service.handleWebhook("body", "sig");
        assertThat(saved.status()).isEqualTo(CheckoutPayment.Status.FAILED);
        verify(repository).finishEvent("evt_terminal", saved.id(), "OBSERVE");
        verify(business, never()).fulfil(any());
    }

    @Test
    void expiredSessionWithoutIntentCancelsUnpaidAttempt() {
        prepare();
        var expired = session("expired", "unpaid", null, null);
        when(gateway.retrieveCheckoutSession(any())).thenReturn(expired);
        stubVerifiedWebhook("evt_expired", "cs_test_fixture");
        service.handleWebhook("body", "sig");
        assertThat(saved.status()).isEqualTo(CheckoutPayment.Status.CANCELED);
        verify(repository).finishEvent("evt_expired", saved.id(), "OBSERVE");
    }

    @Test
    void manualRecoveryOfMismatchedSessionRecordsMismatchEvidence() {
        UUID id = prepare();
        when(gateway.retrieveCheckoutSession(any())).thenReturn(changed("amount"));
        service.recover(id, "yorick", true);
        verify(repository)
                .recordRecovery(
                        eq(id),
                        eq("yorick"),
                        eq("MISMATCH"),
                        contains("savedSnapshotMatches=false"),
                        eq(false));
        verify(repository).finishEvent(any(), eq(id), eq("REVIEW"));
    }

    @Test
    void manualRecoveryWithVerifiedDownstreamKeepsFulfilled() {
        UUID id = prepare();
        var paid = session("complete", "paid", "pi_original", "succeeded");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(paid);
        when(business.fulfilmentEvidence(any()))
                .thenReturn(PaymentBusinessPort.FulfilmentEvidence.COMPLETE);
        service.recover(id, "yorick", true);
        verify(repository)
                .recordRecovery(eq(id), eq("yorick"), eq("FULFILLED"), anyString(), eq(true));
    }

    @Test
    void manualRecoveryWithUnverifiedDownstreamMarksMissing() {
        UUID id = prepare();
        var paid = session("complete", "paid", "pi_original", "succeeded");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(paid);
        service.recover(id, "yorick", true);
        verify(repository)
                .recordRecovery(
                        eq(id), eq("yorick"), eq("DOWNSTREAM_MISSING"), anyString(), eq(false));
    }

    @Test
    void manualRecoveryOfTerminalUnpaidVerifiesCompensation() {
        UUID id = prepare();
        saved = saved.failTerminalUnpaid();
        var terminal = session("expired", "unpaid", "pi_x", "canceled");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(terminal);
        service.recover(id, "yorick", true);
        verify(repository)
                .recordRecovery(eq(id), eq("yorick"), eq("OBSERVE"), anyString(), eq(true));
        verify(business, never()).fulfil(any());
        verify(repository, never()).requireRefund(any(), any(), any());
    }

    @Test
    void expiryCollectorExpiresOpenSessionsAndObservesClosedOnes() {
        UUID id = prepare();
        when(repository.now()).thenReturn(NOW.plusSeconds(8000));
        when(repository.pendingBefore(any(), anyInt())).thenReturn(List.of(saved));
        when(gateway.retrieveCheckoutSession(any()))
                .thenReturn(session("open", "unpaid", null, null));
        when(gateway.expireCheckoutSession(any()))
                .thenReturn(session("expired", "unpaid", null, null));
        assertThat(service.expireDueSessions()).isEqualTo(1);
        verify(gateway).expireCheckoutSession("cs_test_fixture");
        when(gateway.retrieveCheckoutSession(any()))
                .thenReturn(session("expired", "unpaid", null, null));
        assertThat(service.expireDueSessions()).isEqualTo(1);
        verify(gateway).expireCheckoutSession(any());
        assertThat(id).isNotNull();
    }

    @Test
    void scanAndReviewCasesDelegateToRepository() {
        when(repository.pendingBefore(any(), anyInt())).thenReturn(List.of());
        when(repository.reviewCases(any(), anyInt())).thenReturn(List.of());
        assertThat(service.scan()).isEmpty();
        assertThat(service.reviewCases(null)).isEmpty();
    }

    @Test
    void assignReviewPersistsOperatorDeadline() {
        UUID id = UUID.randomUUID();
        service.assignReview(id, "yorick", NOW.plusSeconds(3600));
        verify(repository).lock(id);
        verify(repository).review(id, "OPERATOR_REVIEW", "yorick");
        verify(repository).assignReview(id, "yorick", NOW.plusSeconds(3600));
    }

    @Test
    void recoverRequiresExplicitConfirmation() {
        assertThatThrownBy(() -> service.recover(INTENT, "yorick", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recoverRejectsPaymentIdMismatchAndRecordsUnverifiedLookup() {
        UUID id = prepare();
        when(gateway.retrieveCheckoutSession(any())).thenReturn(changed("payment"));
        assertThatThrownBy(() -> service.recover(id, "yorick", true, "cs_test_fixture"))
                .isInstanceOf(BizException.class);
        verify(repository).review(id, "RECOVERY_FAILED", "yorick");
        verify(repository)
                .recordRecovery(
                        eq(id),
                        eq("yorick"),
                        eq("UNVERIFIED"),
                        contains("lookupHint=cs_test_fixture"),
                        eq(false));
    }

    @Test
    void inspectRejectsUnboundAndMalformedSessions() {
        doThrow(new IllegalStateException("timeout")).when(gateway).createCheckoutSession(any());
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                .isInstanceOf(IllegalStateException.class);
        UUID savedId = saved.id();
        assertThatThrownBy(() -> service.inspect(savedId)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.inspect(savedId, "bad")).isInstanceOf(BizException.class);
    }

    @Test
    void returnUrlWithoutFragmentAppendsPaymentId() {
        StripeCheckoutService plain = service("https://shop.example/return");
        UUID id = plain.prepare(BUYER, INTENT, "key").paymentId();
        assertThat(saved.successUrl()).isEqualTo("https://shop.example/return?paymentId=" + id);
    }

    @Test
    void providerCallsAreRejectedInsideTransactions() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void manualRecoveryOfCanceledPaymentVerifiesCompensation() {
        UUID id = prepare();
        saved = saved.cancelUnpaid();
        var expired = session("expired", "unpaid", null, null);
        when(gateway.retrieveCheckoutSession(any())).thenReturn(expired);
        service.recover(id, "yorick", true);
        verify(repository)
                .recordRecovery(eq(id), eq("yorick"), eq("OBSERVE"), anyString(), eq(true));
        saved = saved.cancelUnpaid();
        when(gateway.retrieveCheckoutSession(any()))
                .thenReturn(session("open", "unpaid", null, null));
        service.recover(id, "yorick", true);
        verify(repository)
                .recordRecovery(eq(id), eq("yorick"), eq("OBSERVE"), anyString(), eq(false));
    }

    @Test
    void manualRecoveryWithExistingOutcomeSkipsOutcomeCheck() {
        UUID id = prepare();
        saved = saved.failTerminalUnpaid();
        var terminal = session("expired", "unpaid", "pi_x", "canceled");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(terminal);
        when(repository.outcome(id)).thenReturn("FULFILLED");
        service.recover(id, "yorick", true);
        verify(repository)
                .recordRecovery(eq(id), eq("yorick"), eq("OBSERVE"), anyString(), eq(false));
    }

    @Test
    void manualRecoveryOfFailedPaymentWithOpenSessionStaysUnresolved() {
        UUID id = prepare();
        saved = saved.failTerminalUnpaid();
        when(gateway.retrieveCheckoutSession(any()))
                .thenReturn(session("open", "unpaid", null, null));
        service.recover(id, "yorick", true);
        verify(repository)
                .recordRecovery(eq(id), eq("yorick"), eq("OBSERVE"), anyString(), eq(false));
        verify(business, never()).fulfil(any());
    }

    @Test
    void manualRecoveryWithDownstreamEvidenceConflictMarksMissing() {
        UUID id = prepare();
        var paid = session("complete", "paid", "pi_original", "succeeded");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(paid);
        when(repository.now()).thenReturn(NOW.plusSeconds(8000));
        when(repository.refundRecorded(id)).thenReturn(true);
        when(business.fulfilmentEvidence(any()))
                .thenReturn(PaymentBusinessPort.FulfilmentEvidence.COMPLETE);
        service.recover(id, "yorick", true);
        verify(repository).requireRefund(any(), eq("BUSINESS_EXPIRED"), anyString());
        verify(repository)
                .recordRecovery(
                        eq(id), eq("yorick"), eq("DOWNSTREAM_MISSING"), anyString(), eq(false));
    }

    @Test
    void manualRecoveryWithoutRecordedRefundMarksMissing() {
        UUID id = prepare();
        var paid = session("complete", "paid", "pi_original", "succeeded");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(paid);
        when(repository.now()).thenReturn(NOW.plusSeconds(8000));
        service.recover(id, "yorick", true);
        verify(repository).requireRefund(any(), eq("BUSINESS_EXPIRED"), anyString());
        verify(repository)
                .recordRecovery(
                        eq(id), eq("yorick"), eq("DOWNSTREAM_MISSING"), anyString(), eq(false));
    }

    @Test
    void manualRecoveryOfTerminalPaymentWithPaidSessionMarksDownstreamMissing() {
        UUID id = prepare();
        saved = saved.failTerminalUnpaid();
        var paid = session("complete", "paid", "pi_original", "succeeded");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(paid);
        service.recover(id, "yorick", true);
        verify(repository, never()).review(eq(id), eq("LATE_PAID_REQUIRES_RECOVERY"), any());
        verify(repository)
                .recordRecovery(
                        eq(id), eq("yorick"), eq("DOWNSTREAM_MISSING"), anyString(), eq(false));
    }

    @Test
    void explicitExpiryExpiresOpenSession() {
        UUID id = prepare();
        var expired = session("expired", "unpaid", null, null);
        when(gateway.retrieveCheckoutSession(any()))
                .thenReturn(session("open", "unpaid", null, null));
        when(gateway.expireCheckoutSession(any())).thenReturn(expired);
        service.expireRequestedSession(id);
        verify(gateway).expireCheckoutSession("cs_test_fixture");
        verify(repository).finishEvent(any(), eq(id), eq("OBSERVE"));
    }

    @Test
    void assignReviewRejectsPastDeadline() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.assignReview(id, "yorick", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unboundPaymentRejectsHintsAndNullHintRecoveryIsUnverified() {
        doThrow(new IllegalStateException("timeout")).when(gateway).createCheckoutSession(any());
        assertThatThrownBy(() -> service.prepare(BUYER, INTENT, "key"))
                .isInstanceOf(IllegalStateException.class);
        UUID id = saved.id();
        assertThatThrownBy(() -> service.inspect(id, "cs_test_aaaaaaaaaaaaaaaa"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.recover(id, "yorick", true))
                .isInstanceOf(BizException.class);
    }

    @Test
    void terminalUnpaidWithCompleteEvidenceStaysUnresolved() {
        UUID id = prepare();
        saved = saved.failTerminalUnpaid();
        var terminal = session("expired", "unpaid", "pi_x", "canceled");
        when(gateway.retrieveCheckoutSession(any())).thenReturn(terminal);
        when(business.fulfilmentEvidence(any()))
                .thenReturn(PaymentBusinessPort.FulfilmentEvidence.COMPLETE);
        service.recover(id, "yorick", true);
        verify(repository)
                .recordRecovery(eq(id), eq("yorick"), eq("OBSERVE"), anyString(), eq(false));
    }

    @Test
    void inspectAcceptsOwnBindingHintAndRejectsForeignOnes() {
        UUID id = prepare();
        assertThat(service.inspect(id, "cs_test_fixture").id()).isEqualTo("cs_test_fixture");
        assertThat(service.preview(id, "cs_test_fixture").sessionId()).isEqualTo("cs_test_fixture");
        assertThatThrownBy(() -> service.inspect(id, "cs_test_otherAAAAAAAAAAAAAAAA"))
                .isInstanceOf(BizException.class);
    }
}
