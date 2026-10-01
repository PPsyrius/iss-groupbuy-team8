package com.blocbuy.payment.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Webhook facade. The Checkout service owns transaction boundaries after provider verification.
 * Missing integration fails closed instead of acknowledging an unprocessed money notification.
 */
@RequiredArgsConstructor
public class StripePaymentAppService implements PaymentAppService {

    private final ObjectProvider<StripeCheckoutService> checkout;

    @Override
    public void handleStripeWebhook(String payload, String signatureHeader) {
        StripeCheckoutService service = checkout.getIfAvailable();
        if (service == null) {
            // Do not acknowledge and lose money-related notifications while integration is absent.
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        service.handleWebhook(payload, signatureHeader);
    }
}
