package com.blocbuy.payment.gateway;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.model.CheckoutPayment;
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

/** Checkout adapter; the disabled webhook also classifies other signed money events. */
@Component
@EnableConfigurationProperties(StripeProperties.class)
public class StripeCheckoutGateway implements CheckoutGateway {
    private static final Set<String> CHECKOUT_EVENTS =
            Set.of(
                    "checkout.session.completed",
                    "checkout.session.async_payment_succeeded",
                    "checkout.session.async_payment_failed",
                    "checkout.session.expired");
    private final StripeProperties properties;
    private final StripeClientProvider clients;

    public StripeCheckoutGateway(StripeProperties properties, StripeClientProvider clients) {
        this.properties = properties;
        this.clients = clients;
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
                    clients.client()
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
                    clients.client()
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
            clients.client().checkout().sessions().expire(sessionId);
            return retrieveCheckoutSession(sessionId);
        } catch (StripeException ex) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    @Override
    public Optional<CheckoutGateway.Event> verifyCheckoutWebhook(String rawBody, String signature) {
        com.stripe.model.Event event = verifiedEvent(rawBody, signature);
        if (!CHECKOUT_EVENTS.contains(event.getType())) {
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

    @Override
    public boolean verifyMoneyWebhook(String rawBody, String signature) {
        String type = verifiedEvent(rawBody, signature).getType();
        return CHECKOUT_EVENTS.contains(type)
                || type.startsWith("payment_intent.")
                || type.startsWith("charge.")
                || type.startsWith("refund.");
    }

    private com.stripe.model.Event verifiedEvent(String rawBody, String signature) {
        if (properties.webhookSecret() == null || properties.webhookSecret().isBlank()) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        if (rawBody == null
                || rawBody.length() > 262144
                || signature == null
                || signature.length() > 2048) {
            throw new BizException(PaymentErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
        }
        try {
            com.stripe.model.Event event =
                    Webhook.constructEvent(rawBody, signature, properties.webhookSecret());
            if (event.getType() == null || event.getType().isBlank()) {
                throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
            }
            return event;
        } catch (SignatureVerificationException | IllegalArgumentException ex) {
            throw new BizException(PaymentErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
        }
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
