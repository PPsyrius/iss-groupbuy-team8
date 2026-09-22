package com.blocbuy.payment.gateway;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.model.RefundWork;
import com.stripe.StripeClient;
import com.stripe.exception.ApiException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.RefundCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.RefundListParams;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Provider refund gateway: key guards, creation and retrieval. */
class StripeRefundGatewayTest {
    private final StripeRefundGateway gateway =
            new StripeRefundGateway(
                    new StripeProperties("sk_test_fixture_not_real", "test"),
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
                                                new StripeProperties("sk_live_test", "x"),
                                                new StripeClientProvider(
                                                        new StripeProperties("sk_live_test", "x")))
                                        .createRefund(work))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(
                        () ->
                                new StripeRefundGateway(
                                                new StripeProperties("", "x"),
                                                new StripeClientProvider(
                                                        new StripeProperties("", "x")))
                                        .retrieveRefund("re_test"))
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
}
