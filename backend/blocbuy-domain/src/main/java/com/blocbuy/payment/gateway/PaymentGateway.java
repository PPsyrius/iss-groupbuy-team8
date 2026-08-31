package com.blocbuy.payment.gateway;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Outbound port to the payment provider (Stripe). Defined on the application side and implemented
 * in the infrastructure layer — the domain and application layers never reference Stripe types.
 *
 * <p>Contract: amounts are {@link BigDecimal} major units with an ISO-4217 currency; conversion to
 * provider minor units is the implementation's job. All operations must be idempotent — the
 * implementation sends the given idempotency key to the provider. Failures surface as
 * {@link com.blocbuy.common.exception.BizException} built from
 * {@link com.blocbuy.payment.exception.PaymentErrorCode}; raw provider errors never leak upward.
 */
public interface PaymentGateway {

  /**
   * Creates a payment intent at the provider for an order.
   *
   * @param command normalized creation parameters (provider-free)
   * @return the provider reference and client confirmation secret
   * @throws com.blocbuy.common.exception.BizException with {@code PAYMENT_502_001} on provider
   *     failure
   */
  ProviderPaymentRef createPaymentIntent(CreatePaymentIntentCommand command);

  /**
   * Verifies a webhook callback signature and extracts the event the provider sent.
   *
   * @param payload raw request body, exactly as received (never re-serialized)
   * @param signatureHeader value of the provider signature header ({@code Stripe-Signature})
   * @return the verified event, or empty if the event type is not relevant to payment processing
   * @throws com.blocbuy.common.exception.BizException with {@code PAYMENT_400_001} when signature
   *     verification fails
   */
  Optional<ProviderEvent> verifyWebhook(String payload, String signatureHeader);

  /**
   * Parameters for creating a provider payment intent.
   *
   * @param paymentNo internal business payment number (used for reconciliation metadata)
   * @param orderId owning order identifier
   * @param amount major units, e.g. {@code 12.34}
   * @param currency ISO-4217 code, e.g. {@code "sgd"}
   * @param idempotencyKey stable key so provider retries never double-charge
   */
  record CreatePaymentIntentCommand(
      String paymentNo, String orderId, BigDecimal amount, String currency, String idempotencyKey) {}

  /**
   * Reference returned by the provider after intent creation.
   *
   * @param providerPaymentIntentId provider-side intent id (e.g. Stripe {@code pi_...})
   * @param clientSecret secret the client uses to confirm the payment; store server-side only
   */
  record ProviderPaymentRef(String providerPaymentIntentId, String clientSecret) {}

  /**
   * A verified provider webhook event reduced to payment-relevant facts.
   *
   * @param eventId unique provider event id (deduplication: same id must never apply twice)
   * @param eventType provider event type, e.g. {@code payment_intent.succeeded}
   * @param providerPaymentIntentId intent the event refers to
   */
  record ProviderEvent(String eventId, String eventType, String providerPaymentIntentId) {}
}
