package com.blocbuy.payment.service;

/**
 * Webhook entry port retained for the original controller. Under {@code stripe-sprint2},
 * {@link StripePaymentAppService} delegates to the transactional {@link StripeCheckoutService} for
 * signature verification, event deduplication and settlement. Checkout commands have their own
 * application service; this interface does not need one method per payment use case.
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
