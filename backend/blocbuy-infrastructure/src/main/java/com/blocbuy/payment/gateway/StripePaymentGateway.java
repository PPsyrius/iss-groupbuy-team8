package com.blocbuy.payment.gateway;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Stripe implementation of the {@link PaymentGateway} port. This is the only class in the codebase
 * allowed to reference {@code com.stripe} types; amounts enter as {@code BigDecimal} major units
 * and are converted to minor units here.
 *
 * <p>Skeleton: intent creation and webhook verification are wired end to end; refund/capture
 * variants land with their use cases. Provider errors are logged once here (no PII, no card data)
 * and surface as {@link PaymentErrorCode#PAYMENT_PROVIDER_ERROR}.
 */
@Slf4j
@Component
@EnableConfigurationProperties(StripeProperties.class)
public class StripePaymentGateway implements PaymentGateway {

    private final StripeProperties properties;

    public StripePaymentGateway(StripeProperties properties) {
        this.properties = properties;
    }

    @Override
    public ProviderPaymentRef createPaymentIntent(CreatePaymentIntentCommand command) {
        if (!properties.configured()) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                        // Stripe charges in minor units (cents); exact conversion, never floating
                        // point
                        .setAmount(command.amount().movePointRight(2).longValueExact())
                        .setCurrency(command.currency())
                        .putMetadata("payment_no", command.paymentNo())
                        .putMetadata("order_id", command.orderId())
                        .setAutomaticPaymentMethods(
                                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                        .setEnabled(true)
                                        .build())
                        .build();
        // provider-level idempotency: retries with the same key never create a second intent
        RequestOptions options =
                RequestOptions.builder().setIdempotencyKey(command.idempotencyKey()).build();
        try {
            PaymentIntent intent = stripeClient().paymentIntents().create(params, options);
            return new ProviderPaymentRef(intent.getId(), intent.getClientSecret());
        } catch (StripeException ex) {
            log.warn(
                    "stripe payment intent creation failed: code={}, requestId={}",
                    ex.getCode(),
                    ex.getRequestId());
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    @Override
    public Optional<ProviderEvent> verifyWebhook(String payload, String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, properties.webhookSecret());
        } catch (SignatureVerificationException ex) {
            throw new BizException(PaymentErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
        }
        // TODO(TEAM8-payment): whitelist handled types (payment_intent.succeeded /
        // payment_intent.payment_failed / charge.refunded); ignore the rest.
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        return deserializer
                .getObject()
                .filter(PaymentIntent.class::isInstance)
                .map(PaymentIntent.class::cast)
                .map(intent -> new ProviderEvent(event.getId(), event.getType(), intent.getId()));
    }

    private StripeClient stripeClient() {
        // TODO(TEAM8-payment): hoist to a singleton bean once credentials are provisioned per env
        return new StripeClient(properties.apiKey());
    }
}
