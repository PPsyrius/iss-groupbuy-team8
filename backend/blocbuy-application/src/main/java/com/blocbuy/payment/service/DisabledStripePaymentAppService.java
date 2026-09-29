package com.blocbuy.payment.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.gateway.CheckoutGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Verifies callbacks while Checkout is off; never acknowledges an event it cannot settle. */
@Service
@Profile("!stripe-sprint2")
@RequiredArgsConstructor
public class DisabledStripePaymentAppService implements PaymentAppService {
    private final CheckoutGateway gateway;

    @Override
    public void handleStripeWebhook(String payload, String signatureHeader) {
        // No money-related event can be settled while Checkout is disabled.
        if (gateway.verifyMoneyWebhook(payload, signatureHeader)) {
            throw new BizException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }
}
