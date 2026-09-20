package com.blocbuy.payment.persistence;

import com.blocbuy.payment.model.Payment;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.repository.PaymentRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Exposes Checkout rows through dev's unchanged String-ID, ID-only aggregate contract. */
@Repository
@Primary
@Profile("stripe-sprint2")
@RequiredArgsConstructor
public class DevPaymentRepositoryAdapter implements PaymentRepository {
    private final CheckoutPaymentRepository checkout;

    @Override
    public Optional<Payment> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        UUID paymentId;
        try {
            paymentId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return checkout.findById(paymentId).map(payment -> Payment.of(payment.id().toString()));
    }
}
