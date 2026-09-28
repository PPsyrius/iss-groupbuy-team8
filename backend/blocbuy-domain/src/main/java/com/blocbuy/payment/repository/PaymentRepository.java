package com.blocbuy.payment.repository;

import com.blocbuy.payment.model.Payment;
import java.util.Optional;

/**
 * ID-only legacy lookup port, retained for compatibility callers. Checkout writes, provider-event
 * deduplication and refund obligations use {@link CheckoutPaymentRepository} instead; persistence
 * details never cross either domain port.
 */
public interface PaymentRepository {

    Optional<Payment> findById(String id);
}
