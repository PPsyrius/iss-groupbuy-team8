package com.blocbuy.payment.gateway;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.model.CheckoutPayment;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Checkout adapter; the original PaymentIntent contract is unchanged. */
@Component
@EnableConfigurationProperties(StripeProperties.class)
public class StripeCheckoutGateway implements CheckoutGateway {
    private final StripeProperties properties;

    public StripeCheckoutGateway(StripeProperties properties) {
        this.properties = properties;
    }

    private StripeClient stripeClient() {
        return StripeClient.builder()
                .setApiKey(properties.apiKey())
                .setConnectTimeout(3000)
                .setReadTimeout(10000)
                .setMaxNetworkRetries(1)
                .build();
    }

    /** Sandbox-only until the joint groupbuy/order integration is accepted. */
    private void requireSandbox() {
        if (!properties.configured() || !properties.apiKey().startsWith("sk_test_")) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    @Override
    public CheckoutGateway.Session createCheckoutSession(CheckoutPayment payment) {
        requireSandbox();
        try {
            var session =
                    stripeClient()
                            .checkout()
                            .sessions()
                            .create(
                                    checkoutParams(payment),
                                    RequestOptions.builder()
                                            .setIdempotencyKey(payment.providerKey())
                                            .build());
            return snapshot(session);
        } catch (StripeException ex) {
            // Do not log the exception body: it can contain customer details or request values.
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    static SessionCreateParams checkoutParams(CheckoutPayment payment) {
        var price =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(payment.quote().currency())
                        .setUnitAmount(payment.quote().amount().movePointRight(2).longValueExact())
                        .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("BlocBuy group purchase")
                                        .build())
                        .build();
        return SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setAdaptivePricing(
                        SessionCreateParams.AdaptivePricing.builder().setEnabled(false).build())
                .setClientReferenceId(payment.id().toString())
                .putMetadata("payment_id", payment.id().toString())
                .putMetadata("intent_id", payment.quote().intentId().toString())
                .setSuccessUrl(payment.successUrl())
                .setCancelUrl(payment.cancelUrl())
                .setExpiresAt(payment.sessionExpiresAt().getEpochSecond())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(price)
                                .build())
                .build();
    }

    @Override
    public CheckoutGateway.Session retrieveCheckoutSession(String sessionId) {
        requireSandbox();
        try {
            return snapshot(
                    stripeClient()
                            .checkout()
                            .sessions()
                            .retrieve(
                                    sessionId,
                                    com.stripe.param.checkout.SessionRetrieveParams.builder()
                                            .addExpand("payment_intent")
                                            .build()));
        } catch (StripeException ex) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    @Override
    public CheckoutGateway.Session expireCheckoutSession(String sessionId) {
        requireSandbox();
        try {
            stripeClient().checkout().sessions().expire(sessionId);
            return retrieveCheckoutSession(sessionId);
        } catch (StripeException ex) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    @Override
    public Optional<CheckoutGateway.Event> verifyCheckoutWebhook(String rawBody, String signature) {
        if (properties.webhookSecret() == null || properties.webhookSecret().isBlank()) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        if (rawBody == null
                || rawBody.length() > 262144
                || signature == null
                || signature.length() > 2048) {
            throw new BizException(PaymentErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
        }
        com.stripe.model.Event event;
        try {
            event = Webhook.constructEvent(rawBody, signature, properties.webhookSecret());
        } catch (SignatureVerificationException | IllegalArgumentException ex) {
            throw new BizException(PaymentErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
        }
        if (!Set.of(
                        "checkout.session.completed",
                        "checkout.session.async_payment_succeeded",
                        "checkout.session.async_payment_failed",
                        "checkout.session.expired")
                .contains(event.getType())) {
            return Optional.empty();
        }
        // API-version mismatch is retryable, never silently acknowledge a supported event.
        var object =
                event.getDataObjectDeserializer()
                        .getObject()
                        .filter(com.stripe.model.checkout.Session.class::isInstance)
                        .map(com.stripe.model.checkout.Session.class::cast)
                        .orElseThrow(
                                () -> new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR));
        return Optional.of(
                new CheckoutGateway.Event(
                        event.getId(),
                        event.getType(),
                        object.getId(),
                        Boolean.TRUE.equals(event.getLivemode()),
                        event.getAccount()));
    }

    private static CheckoutGateway.Session snapshot(com.stripe.model.checkout.Session session) {
        String metadataPayment =
                session.getMetadata() == null ? null : session.getMetadata().get("payment_id");
        if (metadataPayment == null
                || !metadataPayment.equals(session.getClientReferenceId())
                || !"payment".equals(session.getMode())
                || session.getAmountTotal() == null
                || session.getExpiresAt() == null) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        return new CheckoutGateway.Session(
                session.getId(),
                metadataPayment,
                session.getMetadata().get("intent_id"),
                session.getPaymentIntent(),
                session.getAmountTotal(),
                session.getCurrency(),
                session.getStatus(),
                session.getPaymentStatus(),
                Boolean.TRUE.equals(session.getLivemode()),
                session.getUrl(),
                Instant.ofEpochSecond(session.getExpiresAt()),
                session.getPaymentIntentObject() == null
                        ? null
                        : session.getPaymentIntentObject().getStatus());
    }
}
