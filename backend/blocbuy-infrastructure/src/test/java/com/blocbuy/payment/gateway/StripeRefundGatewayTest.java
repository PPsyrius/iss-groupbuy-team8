package com.blocbuy.payment.gateway;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.ErrorCode;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.model.RefundWork;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.PermissionException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.RefundCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.RefundListParams;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

/** Provider refund gateway: key guards, creation and retrieval. */
class StripeRefundGatewayTest {
    private static final String PRIVATE = "private provider data";
    private final Logger classifierLog =
            (Logger) LoggerFactory.getLogger(StripeFailureClassifier.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Level previousClassifierLevel;

    @BeforeEach
    void captureLogs() {
        previousClassifierLevel = classifierLog.getLevel();
        logs.start();
        classifierLog.addAppender(logs);
        classifierLog.setLevel(Level.DEBUG);
    }

    @AfterEach
    void releaseLogs() {
        classifierLog.detachAppender(logs);
        classifierLog.setLevel(previousClassifierLevel);
        logs.stop();
    }

    @Test
    void logCaptureRestoresAnExistingLoggerLevel() {
        releaseLogs();
        Level initialLevel = classifierLog.getLevel();
        try {
            classifierLog.setLevel(Level.ERROR);
            captureLogs();
            releaseLogs();

            assertThat(classifierLog.getLevel()).isEqualTo(Level.ERROR);
        } finally {
            classifierLog.setLevel(initialLevel);
            // Leave a balanced capture scope for the normal AfterEach hook to release.
            captureLogs();
        }
    }

    private final StripeRefundGateway gateway =
            new StripeRefundGateway(
                    new StripeClientProvider(
                            new StripeProperties("sk_test_fixture_not_real", "test")));
    private final RefundWork work =
            new RefundWork(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("10.00"),
                    "sgd",
                    "pi_test",
                    "SUCCEEDED",
                    new BigDecimal("10.00"),
                    "sgd",
                    null,
                    Instant.now(),
                    Instant.now(),
                    1);

    private Refund refund() {
        Refund refund = new Refund();
        refund.setId("re_test");
        refund.setPaymentIntent("pi_test");
        refund.setAmount(1000L);
        refund.setCurrency("sgd");
        refund.setStatus("succeeded");
        refund.setMetadata(Map.of("obligation_id", work.id().toString()));
        return refund;
    }

    @Test
    void rejectsLiveAndAbsentKeysBeforeNetwork() {
        assertThatThrownBy(
                        () ->
                                new StripeRefundGateway(
                                                new StripeClientProvider(
                                                        new StripeProperties("sk_live_test", "x")))
                                        .createRefund(work))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(
                        () ->
                                new StripeRefundGateway(
                                                new StripeClientProvider(
                                                        new StripeProperties("", "x")))
                                        .retrieveRefund("re_test"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(
                        () ->
                                new StripeRefundGateway(
                                                new StripeClientProvider(
                                                        new StripeProperties("sk_live_test", "x")))
                                        .findRefund(work))
                .isInstanceOf(BizException.class);
    }

    @Test
    void mapsStableKeyFrozenAmountAndOriginalPaymentThenRetrievesAndFinds() throws Exception {
        StripeClient client = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        var builder = mock(StripeClient.StripeClientBuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(client);
        try (var factory = mockStatic(StripeClient.class)) {
            factory.when(StripeClient::builder).thenReturn(builder);
            PaymentIntent payment = new PaymentIntent();
            payment.setLivemode(false);
            payment.setStatus("succeeded");
            payment.setCurrency("sgd");
            payment.setAmountReceived(1000L);
            when(client.paymentIntents().retrieve("pi_test")).thenReturn(payment);
            when(client.refunds().create(any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenAnswer(
                            call -> {
                                RefundCreateParams params = call.getArgument(0);
                                RequestOptions options = call.getArgument(1);
                                assertThat(params.getAmount()).isEqualTo(1000L);
                                assertThat(params.getPaymentIntent()).isEqualTo("pi_test");
                                assertThat(params.getMetadata())
                                        .isEqualTo(
                                                Map.of(
                                                        "obligation_id",
                                                        work.id().toString(),
                                                        "payment_id",
                                                        work.paymentId().toString()));
                                assertThat(options.getIdempotencyKey())
                                        .isEqualTo(work.providerKey());
                                return refund();
                            });
            assertThat(gateway.createRefund(work).status()).isEqualTo("succeeded");
            when(client.refunds().retrieve("re_test")).thenReturn(refund());
            assertThat(gateway.retrieveRefund("re_test").amountMinor()).isEqualTo(1000);
            RefundCollection collection = new RefundCollection();
            collection.setData(List.of(refund()));
            collection.setHasMore(false);
            when(client.refunds().list(any(RefundListParams.class))).thenReturn(collection);
            assertThat(gateway.findRefund(work).id()).isEqualTo("re_test");
            collection.setHasMore(true);
            assertThat(gateway.findRefund(work)).isNull();
            collection.setHasMore(false);
            collection.setData(List.of());
            assertThat(gateway.findRefund(work)).isNull();
            payment.setLivemode(true);
            assertThatThrownBy(() -> gateway.createRefund(work)).isInstanceOf(BizException.class);
            payment.setLivemode(false);
            payment.setStatus("pending");
            assertThatThrownBy(() -> gateway.createRefund(work)).isInstanceOf(BizException.class);
            payment.setStatus("succeeded");
            payment.setCurrency("usd");
            assertThatThrownBy(() -> gateway.createRefund(work)).isInstanceOf(BizException.class);
            payment.setCurrency("sgd");
            payment.setAmountReceived(999L);
            assertThatThrownBy(() -> gateway.createRefund(work)).isInstanceOf(BizException.class);
            payment.setAmountReceived(null);
            assertThatThrownBy(() -> gateway.createRefund(work)).isInstanceOf(BizException.class);
        }
    }

    @Test
    void providerFailuresAreSanitized() throws Exception {
        StripeClient client = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        var builder = mock(StripeClient.StripeClientBuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(client);
        try (var factory = mockStatic(StripeClient.class)) {
            factory.when(StripeClient::builder).thenReturn(builder);
            var error = new ApiException("private provider data", "request", "error", 503, null);
            when(client.paymentIntents().retrieve("pi_test")).thenThrow(error);
            when(client.refunds().retrieve("re_test")).thenThrow(error);
            when(client.refunds().list(any(RefundListParams.class))).thenThrow(error);
            assertThatThrownBy(() -> gateway.createRefund(work))
                    .isInstanceOf(BizException.class)
                    .hasMessageNotContaining("private provider");
            assertThatThrownBy(() -> gateway.retrieveRefund("re_test"))
                    .isInstanceOf(BizException.class)
                    .hasMessageNotContaining("private provider");
            assertThatThrownBy(() -> gateway.findRefund(work))
                    .isInstanceOf(BizException.class)
                    .hasMessageNotContaining("private provider");
        }
    }

    /**
     * Real stripe-java 34 constructors, in the shapes {@code LiveStripeResponseGetter} builds them
     * from an HTTP status. A refusal that asking again cannot change becomes {@code
     * PAYMENT_PROVIDER_REJECTED} (the worker parks it for review); anything that can heal on its
     * own stays {@code PAYMENT_PROVIDER_ERROR} (the worker retries under the same durable key).
     */
    static Stream<Arguments> providerFailures() {
        var rejected = PaymentErrorCode.PAYMENT_PROVIDER_REJECTED;
        var retry = PaymentErrorCode.PAYMENT_PROVIDER_ERROR;
        return Stream.of(
                // 401/403 are rejected before Stripe executes anything, so nothing is saved under
                // the idempotency key; once the key is fixed the next tick creates one refund.
                Arguments.of(
                        new AuthenticationException(PRIVATE, "req_1", "api_key_expired", 401),
                        retry),
                Arguments.of(
                        new PermissionException(PRIVATE, "req_2", "secret_key_required", 403),
                        retry),
                Arguments.of(
                        new InvalidRequestException(
                                PRIVATE, "payment_intent", "req_3", "resource_missing", 404, null),
                        rejected),
                Arguments.of(
                        new InvalidRequestException(
                                PRIVATE, "charge", "req_4", "charge_already_refunded", 400, null),
                        rejected),
                Arguments.of(
                        new CardException(
                                PRIVATE, "req_5", "card_declined", null, null, null, 402, null),
                        rejected),
                Arguments.of(new IdempotencyException(PRIVATE, "req_6", null, 400), rejected),
                Arguments.of(
                        new RateLimitException(PRIVATE, null, "req_7", "rate_limit", 429, null),
                        retry),
                // No code: retried because of its type, not because of the code guard.
                Arguments.of(
                        new RateLimitException(PRIVATE, null, "req_7b", null, 429, null), retry),
                Arguments.of(new ApiConnectionException(PRIVATE), retry),
                Arguments.of(new ApiException(PRIVATE, "req_8", "api_error", 500, null), retry),
                Arguments.of(new ApiException(PRIVATE, "req_9", null, 503, null), retry),
                Arguments.of(
                        new ApiException(PRIVATE, "req_10", "idempotency_key_in_use", 409, null),
                        retry),
                Arguments.of(
                        new InvalidRequestException(
                                PRIVATE, null, "req_11", "lock_timeout", 400, null),
                        retry),
                // A StripeException subtype the classifier has no rule for defaults to retry.
                Arguments.of(new SignatureVerificationException(PRIVATE, "sig_header"), retry));
    }

    @ParameterizedTest
    @MethodSource("providerFailures")
    void providerFailuresMapToTheRightDisposition(
            StripeException failure, PaymentErrorCode expected) throws Exception {
        withFailingClient(
                failure,
                () -> {
                    assertThat(refusal(() -> gateway.createRefund(work))).isEqualTo(expected);
                    assertThat(refusal(() -> gateway.retrieveRefund("re_test")))
                            .isEqualTo(expected);
                    assertThat(refusal(() -> gateway.findRefund(work))).isEqualTo(expected);
                });

        String disposition =
                expected == PaymentErrorCode.PAYMENT_PROVIDER_REJECTED ? "review" : "retry";
        assertThat(logs.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        safeLine("createRefund", failure, disposition),
                        safeLine("retrieveRefund", failure, disposition),
                        safeLine("findRefund", failure, disposition));
        assertThat(logs.list).allSatisfy(StripeRefundGatewayTest::carriesNoProviderData);
    }

    /**
     * The line an operator reads names the failure; the provider's message and body never appear.
     */
    @Test
    void logsOnlySafeMetadata() throws Exception {
        withFailingClient(
                new InvalidRequestException(
                        PRIVATE, "payment_intent", "req_1", "resource_missing", 404, null),
                () -> refusal(() -> gateway.retrieveRefund("re_test")));
        // The provider caches its client, so the second failure needs a gateway of its own.
        var second =
                new StripeRefundGateway(
                        new StripeClientProvider(
                                new StripeProperties("sk_test_fixture_not_real", "test")));
        withFailingClient(
                new ApiConnectionException(PRIVATE), () -> refusal(() -> second.findRefund(work)));

        assertThat(logs.list).hasSize(2).allSatisfy(StripeRefundGatewayTest::carriesNoProviderData);
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logs.list.get(0).getFormattedMessage())
                .isEqualTo(
                        "Stripe call failed: operation=retrieveRefund, type=InvalidRequestException,"
                                + " status=404, code=resource_missing, requestId=req_1,"
                                + " disposition=review");
        // stripe-java 34 gives a transport failure status 0 and no code or request id.
        assertThat(logs.list.get(1).getFormattedMessage())
                .isEqualTo(
                        "Stripe call failed: operation=findRefund, type=ApiConnectionException,"
                                + " status=0, code=null, requestId=null, disposition=retry");
    }

    static Stream<Arguments> paymentLookupFailures() {
        return Stream.of(
                Arguments.of(
                        new InvalidRequestException(
                                PRIVATE, "intent", "req_pi", "resource_missing", 404, null),
                        PaymentErrorCode.PAYMENT_PROVIDER_REJECTED,
                        "review"),
                Arguments.of(
                        new ApiConnectionException(PRIVATE),
                        PaymentErrorCode.PAYMENT_PROVIDER_ERROR,
                        "retry"));
    }

    /**
     * The PaymentIntent lookup that guards creation is classified the same way, under the {@code
     * createRefund} operation, and a failed lookup never reaches the refund call.
     */
    @ParameterizedTest
    @MethodSource("paymentLookupFailures")
    void paymentLookupFailureInsideCreateIsClassified(
            StripeException failure, PaymentErrorCode expected, String disposition)
            throws Exception {
        StripeClient client = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        var builder = mock(StripeClient.StripeClientBuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(client);
        try (var factory = mockStatic(StripeClient.class)) {
            factory.when(StripeClient::builder).thenReturn(builder);
            when(client.paymentIntents().retrieve("pi_test")).thenThrow(failure);

            assertThat(refusal(() -> gateway.createRefund(work))).isEqualTo(expected);

            verify(client.refunds(), never())
                    .create(any(RefundCreateParams.class), any(RequestOptions.class));
        }
        assertThat(logs.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(safeLine("createRefund", failure, disposition));
        assertThat(logs.list).allSatisfy(StripeRefundGatewayTest::carriesNoProviderData);
    }

    /** No provider data reaches callers either: the business exception carries no cause or text. */
    @Test
    void refusalCarriesNoProviderCause() throws Exception {
        withFailingClient(
                new InvalidRequestException(
                        PRIVATE, "charge", "req_3", "resource_missing", 404, null),
                () ->
                        assertThatThrownBy(() -> gateway.retrieveRefund("re_test"))
                                .isInstanceOf(BizException.class)
                                .hasNoCause()
                                .hasMessageNotContaining(PRIVATE));
    }

    private static String safeLine(String operation, StripeException failure, String disposition) {
        return "Stripe call failed: operation="
                + operation
                + ", type="
                + failure.getClass().getSimpleName()
                + ", status="
                + failure.getStatusCode()
                + ", code="
                + failure.getCode()
                + ", requestId="
                + failure.getRequestId()
                + ", disposition="
                + disposition;
    }

    private static void carriesNoProviderData(ILoggingEvent event) {
        assertThat(event.getFormattedMessage()).doesNotContain(PRIVATE);
        assertThat(event.getMessage()).doesNotContain(PRIVATE);
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getArgumentArray())
                .noneMatch(Throwable.class::isInstance)
                .noneMatch(arg -> String.valueOf(arg).contains(PRIVATE));
        assertThat(Arrays.toString(event.getArgumentArray())).doesNotContain(PRIVATE);
    }

    private static ErrorCode refusal(Runnable call) {
        try {
            call.run();
        } catch (BizException refused) {
            return refused.errorCode();
        }
        throw new AssertionError("expected a BizException");
    }

    /** Every provider call the gateway makes fails with {@code failure}, except the PI lookup. */
    private void withFailingClient(StripeException failure, Runnable calls) throws Exception {
        StripeClient client = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        var builder = mock(StripeClient.StripeClientBuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(client);
        try (var factory = mockStatic(StripeClient.class)) {
            factory.when(StripeClient::builder).thenReturn(builder);
            PaymentIntent payment = new PaymentIntent();
            payment.setLivemode(false);
            payment.setStatus("succeeded");
            payment.setCurrency("sgd");
            payment.setAmountReceived(1000L);
            when(client.paymentIntents().retrieve("pi_test")).thenReturn(payment);
            when(client.refunds().create(any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(failure);
            when(client.refunds().retrieve("re_test")).thenThrow(failure);
            when(client.refunds().list(any(RefundListParams.class))).thenThrow(failure);
            calls.run();
        }
    }
}
