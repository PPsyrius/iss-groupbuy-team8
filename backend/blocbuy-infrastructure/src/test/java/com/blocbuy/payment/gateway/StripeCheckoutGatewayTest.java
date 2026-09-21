package com.blocbuy.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.CheckoutQuote;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.exception.ApiException;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class StripeCheckoutGatewayTest {
    private static final String SECRET = "test-signing-secret-not-a-real-credential";
    private final StripeCheckoutGateway gateway =
            new StripeCheckoutGateway(
                    new StripeProperties("", SECRET),
                    new StripeClientProvider(new StripeProperties("", SECRET)));

    @Test
    void verifiesRealSignatureAndIgnoresUnknownType() throws Exception {
        String body = event("checkout.session.completed");
        var result =
                gateway.verifyCheckoutWebhook(body, sign(body, Instant.now().getEpochSecond()));
        assertThat(result.orElseThrow().sessionId()).isEqualTo("cs_test_example");
        String unknown = event("customer.created");
        assertThat(
                        gateway.verifyCheckoutWebhook(
                                unknown, sign(unknown, Instant.now().getEpochSecond())))
                .isEmpty();
    }

    @Test
    void rejectsTamperedAndStaleSignatures() throws Exception {
        String body = event("checkout.session.completed");
        String signature = sign(body, Instant.now().getEpochSecond());
        assertThatThrownBy(() -> gateway.verifyCheckoutWebhook(body + " ", signature))
                .isInstanceOf(BizException.class);
        String stale = sign(body, Instant.now().minusSeconds(600).getEpochSecond());
        assertThatThrownBy(() -> gateway.verifyCheckoutWebhook(body, stale))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> gateway.verifyCheckoutWebhook("x".repeat(262145), signature))
                .isInstanceOf(BizException.class);
    }

    @Test
    void versionMismatchDoesNotSilentlyAcknowledgeSupportedEvent() throws Exception {
        String body = event("checkout.session.completed").replace(Stripe.API_VERSION, "2010-01-01");
        String signature = sign(body, Instant.now().getEpochSecond());
        assertThatThrownBy(() -> gateway.verifyCheckoutWebhook(body, signature))
                .isInstanceOf(BizException.class);
    }

    @Test
    void mapsFrozenPriceExpiryAndMetadataWithoutCustomerCardFields() {
        Instant now = Instant.now();
        var quote =
                new CheckoutQuote(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "JOIN",
                        UUID.randomUUID(),
                        new BigDecimal("10.25"),
                        "sgd",
                        now.plusSeconds(3600),
                        now.plusSeconds(300));
        var payment =
                new CheckoutPayment(
                        UUID.randomUUID(),
                        quote,
                        "key",
                        now,
                        now.plusSeconds(1860),
                        "https://example.com/success",
                        "https://example.com/cancel",
                        null,
                        null,
                        null,
                        CheckoutPayment.Status.CREATED,
                        0);
        var params = StripeCheckoutGateway.checkoutParams(payment);
        assertThat(params.getLineItems().getFirst().getPriceData().getUnitAmount())
                .isEqualTo(1025L);
        assertThat(params.getClientReferenceId()).isEqualTo(payment.id().toString());
        assertThat(params.getExpiresAt()).isEqualTo(payment.sessionExpiresAt().getEpochSecond());
        assertThat(params.getAdaptivePricing().getEnabled()).isFalse();
        assertThatThrownBy(() -> gateway.createCheckoutSession(payment))
                .isInstanceOf(BizException.class);
    }

    @Test
    void checkoutAdapterCreatesRetrievesAndExpiresWithoutChangingPaymentIntentGateway()
            throws Exception {
        var client = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        var builder = mock(StripeClient.StripeClientBuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(client);
        var adapter =
                new StripeCheckoutGateway(
                        new StripeProperties("sk_test_fixture", SECRET),
                        new StripeClientProvider(new StripeProperties("sk_test_fixture", SECRET)));
        var payment = payment();
        var session = session(payment);
        try (var factory = mockStatic(StripeClient.class)) {
            factory.when(StripeClient::builder).thenReturn(builder);
            when(client.checkout()
                            .sessions()
                            .create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenAnswer(
                            call -> {
                                RequestOptions options = call.getArgument(1);
                                assertThat(options.getIdempotencyKey())
                                        .isEqualTo(payment.providerKey());
                                return session;
                            });
            when(client.checkout()
                            .sessions()
                            .retrieve(eq("cs_test_fixture"), any(SessionRetrieveParams.class)))
                    .thenAnswer(
                            call -> {
                                SessionRetrieveParams params = call.getArgument(1);
                                assertThat(params.getExpand()).contains("payment_intent");
                                return session;
                            });
            assertThat(adapter.createCheckoutSession(payment).amountMinor()).isEqualTo(1025L);
            assertThat(adapter.retrieveCheckoutSession("cs_test_fixture").paymentIntentStatus())
                    .isNull();
            var intent = new com.stripe.model.PaymentIntent();
            intent.setId("pi_test_fixture");
            intent.setStatus("succeeded");
            session.setPaymentIntentObject(intent);
            assertThat(adapter.expireCheckoutSession("cs_test_fixture").paymentIntentStatus())
                    .isEqualTo("succeeded");
            session.setMetadata(null);
            assertThatThrownBy(() -> adapter.createCheckoutSession(payment))
                    .isInstanceOf(BizException.class);
            session.setMetadata(Map.of("payment_id", "different-payment"));
            assertThatThrownBy(() -> adapter.createCheckoutSession(payment))
                    .isInstanceOf(BizException.class);
            session.setMetadata(Map.of("payment_id", payment.id().toString()));
            session.setMode("subscription");
            assertThatThrownBy(() -> adapter.createCheckoutSession(payment))
                    .isInstanceOf(BizException.class);
            session.setMode("payment");
            session.setAmountTotal(null);
            assertThatThrownBy(() -> adapter.createCheckoutSession(payment))
                    .isInstanceOf(BizException.class);
            session.setAmountTotal(1025L);
            session.setExpiresAt(null);
            assertThatThrownBy(() -> adapter.createCheckoutSession(payment))
                    .isInstanceOf(BizException.class);
        }
    }

    @Test
    void checkoutProviderErrorsAreSanitizedAndLiveKeysAreRejected() throws Exception {
        var client = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        var builder = mock(StripeClient.StripeClientBuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(client);
        var adapter =
                new StripeCheckoutGateway(
                        new StripeProperties("sk_test_fixture", SECRET),
                        new StripeClientProvider(new StripeProperties("sk_test_fixture", SECRET)));
        try (var factory = mockStatic(StripeClient.class)) {
            factory.when(StripeClient::builder).thenReturn(builder);
            var error = new ApiException("private provider data", "request", "error", 503, null);
            when(client.checkout()
                            .sessions()
                            .create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(error);
            when(client.checkout()
                            .sessions()
                            .retrieve(eq("cs_test_fixture"), any(SessionRetrieveParams.class)))
                    .thenThrow(error);
            when(client.checkout().sessions().expire("cs_test_fixture")).thenThrow(error);
            assertThatThrownBy(() -> adapter.createCheckoutSession(payment()))
                    .isInstanceOf(BizException.class)
                    .hasMessageNotContaining("private provider");
            assertThatThrownBy(() -> adapter.retrieveCheckoutSession("cs_test_fixture"))
                    .isInstanceOf(BizException.class)
                    .hasMessageNotContaining("private provider");
            assertThatThrownBy(() -> adapter.expireCheckoutSession("cs_test_fixture"))
                    .isInstanceOf(BizException.class)
                    .hasMessageNotContaining("private provider");
        }
        assertThatThrownBy(
                        () ->
                                new StripeCheckoutGateway(
                                                new StripeProperties("sk_live_fixture", SECRET),
                                                new StripeClientProvider(
                                                        new StripeProperties(
                                                                "sk_live_fixture", SECRET)))
                                        .createCheckoutSession(payment()))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(
                        () ->
                                new StripeCheckoutGateway(
                                                new StripeProperties("", null),
                                                new StripeClientProvider(
                                                        new StripeProperties("", null)))
                                        .verifyCheckoutWebhook("{}", "sig"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(
                        () ->
                                new StripeCheckoutGateway(
                                                new StripeProperties("", " "),
                                                new StripeClientProvider(
                                                        new StripeProperties("", " ")))
                                        .verifyCheckoutWebhook("{}", "sig"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> gateway.verifyCheckoutWebhook(null, "sig"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> gateway.verifyCheckoutWebhook("{}", null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> gateway.verifyCheckoutWebhook("{}", "x".repeat(2049)))
                .isInstanceOf(BizException.class);
    }

    private static CheckoutPayment payment() {
        Instant now = Instant.now();
        var quote =
                new CheckoutQuote(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "JOIN",
                        UUID.randomUUID(),
                        new BigDecimal("10.25"),
                        "sgd",
                        now.plusSeconds(3600),
                        now.plusSeconds(300));
        return new CheckoutPayment(
                UUID.randomUUID(),
                quote,
                "fixture",
                now,
                now.plusSeconds(1860),
                "https://example.com/success",
                "https://example.com/cancel",
                null,
                null,
                null,
                CheckoutPayment.Status.CREATED,
                0);
    }

    private static com.stripe.model.checkout.Session session(CheckoutPayment payment) {
        var session = new com.stripe.model.checkout.Session();
        session.setId("cs_test_fixture");
        session.setMetadata(
                Map.of(
                        "payment_id",
                        payment.id().toString(),
                        "intent_id",
                        payment.quote().intentId().toString()));
        session.setClientReferenceId(payment.id().toString());
        session.setMode("payment");
        session.setAmountTotal(1025L);
        session.setCurrency("sgd");
        session.setExpiresAt(payment.sessionExpiresAt().getEpochSecond());
        return session;
    }

    private static String event(String type) {
        return "{\"id\":\"evt_test\",\"object\":\"event\",\"api_version\":\""
                + Stripe.API_VERSION
                + "\",\"livemode\":false,\"type\":\""
                + type
                + "\",\"data\":{\"object\":{\"id\":\"cs_test_example\",\"object\":\"checkout.session\"}}}";
    }

    private static String sign(String body, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String digest =
                HexFormat.of()
                        .formatHex(
                                mac.doFinal(
                                        (timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + digest;
    }
}
