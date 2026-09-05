package com.blocbuy.payment.persistence;

import com.blocbuy.payment.model.Payment;
import com.blocbuy.payment.repository.PaymentRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Repository implementation for Payment. Placeholder until PaymentMapper + DO land with the first
 * payment migration usage. Batch reads via {@code findByIds}; queries inside loops are forbidden.
 * Payment rows are written before any provider call so callbacks always find local state.
 */
@Repository
public class PaymentRepositoryImpl implements PaymentRepository {

    @Override
    public Optional<Payment> findById(String id) {
        // TODO(TEAM8-payment): select via PaymentMapper once the table exists
        return Optional.empty();
    }
}
