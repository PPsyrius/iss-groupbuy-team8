package com.blocbuy.payment.repository;

import com.blocbuy.payment.model.Payment;
import java.util.Optional;

/**
 * Repository port for the Payment aggregate (one repository per aggregate root). Implemented in
 * the infrastructure layer; DOs and MyBatis mappers never cross this boundary. PostgreSQL is the
 * source of truth for payment state — provider callbacks only trigger state transitions against
 * persisted rows.
 * TODO(TEAM8-payment): add intent-named methods (e.g. save, findByPaymentNo,
 * findByProviderReference) per use case.
 */
public interface PaymentRepository {

  Optional<Payment> findById(String id);
}
