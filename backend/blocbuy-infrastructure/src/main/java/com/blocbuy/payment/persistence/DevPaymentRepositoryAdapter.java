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

/**
 * Exposes Checkout rows through dev's unchanged String-ID, ID-only aggregate contract.
 *
 * <p>REMOVE BEFORE LIVE TRAFFIC: this demo adapter exists only for the {@code stripe-sprint2}
 * profile while the team's real {@code PaymentRepository} implementation is pending. It must be
 * deleted (and the profile retired) in the release cleanup that enables any production payment path
 * — tracked by the pre-live checklist in {@code
 * backend/docs/payment/stripe-release-cleanup-20260920.md}.
 */
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
