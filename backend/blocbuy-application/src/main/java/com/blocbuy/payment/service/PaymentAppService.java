package com.blocbuy.payment.service;

/**
 * Use-case service for Payment — transaction boundary sits on public command methods
 * ({@code @Transactional}, queries {@code readOnly = true}). Orchestrates the Payment aggregate,
 * {@code PaymentRepository}, and {@code PaymentGateway}; carries no rules that belong to the
 * aggregate itself.
 *
 * <p>Flow outline (first use case): create payment → persist row + create provider intent in one
 * use case; webhook handler → verify signature via gateway, deduplicate by provider event id,
 * transition the persisted payment inside a local transaction. Redis never holds payment truth.
 * TODO(TEAM8-payment): add one public method per use case; write paths require Idempotency-Key.
 */
public interface PaymentAppService {

    /**
     * Handles a verified-or-rejected Stripe webhook callback. Verifies the signature via the
     * gateway, deduplicates by provider event id, and transitions the persisted payment inside a
     * local transaction. Unknown/irrelevant event types are acknowledged and ignored.
     *
     * @param payload raw request body, exactly as received
     * @param signatureHeader value of the {@code Stripe-Signature} header
     * @throws com.blocbuy.common.exception.BizException with {@code PAYMENT_400_001} on bad
     *     signature
     */
    void handleStripeWebhook(String payload, String signatureHeader);
}
