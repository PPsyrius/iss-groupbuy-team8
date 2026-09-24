package com.blocbuy.payment.gateway;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.model.RefundWork;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.RefundListParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Test keys only; never routes money to a caller-supplied destination. */
@Component
@RequiredArgsConstructor
public class StripeRefundGateway implements RefundGateway {
    private static final String OBLIGATION_ID = "obligation_id";
    private final StripeProperties properties;
    private final StripeClientProvider clients;

    private void requireSandbox() {
        if (!properties.configured() || !properties.apiKey().startsWith("sk_test_")) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    @Override
    public Snapshot createRefund(RefundWork work) {
        requireSandbox();
        try {
            StripeClient stripe = clients.client();
            var payment = stripe.paymentIntents().retrieve(work.providerIntentId());
            if (Boolean.TRUE.equals(payment.getLivemode())
                    || !"succeeded".equals(payment.getStatus())
                    || !work.currency().equals(payment.getCurrency())
                    || payment.getAmountReceived() == null
                    || payment.getAmountReceived()
                            != work.amount().movePointRight(2).longValueExact()) {
                throw new BizException(PaymentErrorCode.PAYMENT_STATE_INVALID);
            }
            return snapshot(
                    stripe.refunds()
                            .create(
                                    RefundCreateParams.builder()
                                            .setPaymentIntent(work.providerIntentId())
                                            .setAmount(
                                                    work.amount()
                                                            .movePointRight(2)
                                                            .longValueExact())
                                            .putMetadata(OBLIGATION_ID, work.id().toString())
                                            .putMetadata("payment_id", work.paymentId().toString())
                                            .build(),
                                    RequestOptions.builder()
                                            .setIdempotencyKey(work.providerKey())
                                            .build()));
        } catch (StripeException failure) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    @Override
    public Snapshot retrieveRefund(String providerId) {
        requireSandbox();
        try {
            return snapshot(clients.client().refunds().retrieve(providerId));
        } catch (StripeException failure) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    @Override
    public Snapshot findRefund(RefundWork work) {
        requireSandbox();
        try {
            var results =
                    clients.client()
                            .refunds()
                            .list(
                                    RefundListParams.builder()
                                            .setPaymentIntent(work.providerIntentId())
                                            .setLimit(100L)
                                            .build());
            if (Boolean.TRUE.equals(results.getHasMore())) return null;
            var matches =
                    results.getData().stream()
                            .filter(
                                    r ->
                                            work.id()
                                                    .toString()
                                                    .equals(r.getMetadata().get(OBLIGATION_ID)))
                            .toList();
            return matches.size() == 1 ? snapshot(matches.getFirst()) : null;
        } catch (StripeException failure) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    private static Snapshot snapshot(Refund refund) {
        return new Snapshot(
                refund.getId(),
                refund.getPaymentIntent(),
                refund.getMetadata().get(OBLIGATION_ID),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getStatus());
    }
}
