package com.blocbuy.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.gateway.PaymentGateway.CreatePaymentIntentCommand;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.service.PaymentIntentService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

/** Stripe adapter: request mapping, provider errors, and signed webhook decoding. */
class StripePaymentGatewayTest {

    private static final String API_KEY = "sk_test_123";
    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    private final StripePaymentGateway gateway =
            new StripePaymentGateway(new StripeProperties(API_KEY, WEBHOOK_SECRET));

    @Test
    void unconfiguredCreateFailsFast() {
        StripePaymentGateway unconfigured =
                new StripePaymentGateway(new StripeProperties(" ", WEBHOOK_SECRET));
        CreatePaymentIntentCommand command = command();

        assertThatThrownBy(() -> unconfigured.createPaymentIntent(command))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
    }

    @Test
    void createMapsCommandAndReturnsProviderReference() throws Exception {
        PaymentIntentService paymentIntents = mock(PaymentIntentService.class);
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_123");
        intent.setClientSecret("pi_123_secret_456");
        when(paymentIntents.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                .thenReturn(intent);

        try (MockedConstruction<StripeClient> clients = stripeClients(paymentIntents)) {
            var result = gateway.createPaymentIntent(command());

            assertThat(result.providerPaymentIntentId()).isEqualTo("pi_123");
            assertThat(result.clientSecret()).isEqualTo("pi_123_secret_456");
            assertThat(clients.constructed()).hasSize(1);

            ArgumentCaptor<PaymentIntentCreateParams> params =
                    ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
            ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
            verify(paymentIntents).create(params.capture(), options.capture());
            assertThat(params.getValue().getAmount()).isEqualTo(1234L);
            assertThat(params.getValue().getCurrency()).isEqualTo("sgd");
            assertThat(params.getValue().getMetadata())
                    .containsEntry("payment_no", "PAY-123")
                    .containsEntry("order_id", "order-123");
            assertThat(params.getValue().getAutomaticPaymentMethods().getEnabled()).isTrue();
            assertThat(options.getValue().getIdempotencyKey()).isEqualTo("idem-123");
        }
    }

    @Test
    void stripeExceptionMapsToProviderError() throws Exception {
        PaymentIntentService paymentIntents = mock(PaymentIntentService.class);
        StripeException providerFailure = mock(StripeException.class);
        when(providerFailure.getCode()).thenReturn("api_error");
        when(providerFailure.getRequestId()).thenReturn("req_123");
        when(paymentIntents.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                .thenThrow(providerFailure);
        CreatePaymentIntentCommand command = command();

        try (MockedConstruction<StripeClient> ignored = stripeClients(paymentIntents)) {
            assertThatThrownBy(() -> gateway.createPaymentIntent(command))
                    .isInstanceOf(BizException.class)
                    .extracting(ex -> ((BizException) ex).errorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    @Test
    void validSignedPaymentIntentWebhookIsDecoded() throws Exception {
        String payload =
                eventPayload("evt_123", "payment_intent.succeeded", "payment_intent", "pi_123");

        var result = gateway.verifyWebhook(payload, signature(payload));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().eventId()).isEqualTo("evt_123");
        assertThat(result.orElseThrow().eventType()).isEqualTo("payment_intent.succeeded");
        assertThat(result.orElseThrow().providerPaymentIntentId()).isEqualTo("pi_123");
    }

    @Test
    void validSignedIrrelevantWebhookIsIgnored() throws Exception {
        String payload = eventPayload("evt_456", "customer.created", "customer", "cus_123");

        assertThat(gateway.verifyWebhook(payload, signature(payload))).isEmpty();
    }

    @Test
    void invalidWebhookSignatureMapsToSignatureError() {
        String payload =
                eventPayload("evt_123", "payment_intent.succeeded", "payment_intent", "pi_123");

        assertThatThrownBy(() -> gateway.verifyWebhook(payload, "t=1,v1=invalid"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
    }

    private static MockedConstruction<StripeClient> stripeClients(
            PaymentIntentService paymentIntents) {
        return mockConstruction(
                StripeClient.class,
                (client, context) -> when(client.paymentIntents()).thenReturn(paymentIntents));
    }

    private static CreatePaymentIntentCommand command() {
        return new CreatePaymentIntentCommand(
                "PAY-123", "order-123", new BigDecimal("12.34"), "sgd", "idem-123");
    }

    @SuppressWarnings("deprecation")
    private static String eventPayload(
            String eventId, String type, String object, String objectId) {
        return """
                {"id":"%s","object":"event","api_version":"%s","created":1234567890,
                "data":{"object":{"id":"%s","object":"%s"}},"livemode":false,
                "pending_webhooks":1,"request":null,"type":"%s"}
                """
                .formatted(eventId, Stripe.API_VERSION, objectId, object, type)
                .replace("\n", "");
    }

    private static String signature(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
