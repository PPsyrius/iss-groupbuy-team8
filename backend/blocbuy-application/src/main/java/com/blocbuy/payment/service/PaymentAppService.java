package com.blocbuy.payment.service;

/**
 * Webhook entry port retained for the controller. Under {@code stripe-sprint2}, {@link
 * StripePaymentAppService} delegates to {@link StripeCheckoutService} for durable settlement;
 * otherwise {@link DisabledStripePaymentAppService} verifies but cannot settle Checkout events.
 * Checkout commands have their own application service.
 */
public interface PaymentAppService {

    /**
     * Verifies the signed callback. Checkout events are acknowledged only after durable handling;
     * irrelevant event types are ignored after verification. When Checkout is off, signed Checkout
     * settlement and other money events fail so the provider can retry.
     *
     * @param payload raw request body, exactly as received
     * @param signatureHeader value of the {@code Stripe-Signature} header
     * @throws com.blocbuy.common.exception.BizException with {@code PAYMENT_400_001} on bad
     *     signature or {@code PAYMENT_502_001} when processing is unavailable
     */
    void handleStripeWebhook(String payload, String signatureHeader);
}
