package com.blocbuy.payment.service;

import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read-only payment facade; callers hold the intent lock before consulting the payment fact. */
@Service
@RequiredArgsConstructor
public class PaymentIntentExpirySupport {
    private final CheckoutPaymentRepository payments;

    public boolean hasSucceededIntent(UUID intentId) {
        return payments.hasSucceededIntent(intentId);
    }
}
