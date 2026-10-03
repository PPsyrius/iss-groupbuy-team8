package com.blocbuy.payment.repository;

import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.PaymentReviewCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for the CheckoutPayment aggregate (one repository per aggregate root).
 * Implemented in the infrastructure layer; DOs and MyBatis mappers never cross this boundary.
 * PostgreSQL is the source of truth for payment state. All mutations join the caller's short
 * transaction; a failed write must roll it back. Checkout reads exclude legacy rows without a
 * frozen business intent.
 */
public interface CheckoutPaymentRepository {

    /**
     * Read-only facts needed by one buyer receipt, without reconstructing the payment aggregate.
     */
    record ReceiptSnapshot(
            UUID userId,
            CheckoutPayment.Status status,
            BigDecimal amount,
            String currency,
            String outcome,
            UUID dealId,
            UUID orderId,
            UUID intentId) {}

    Optional<CheckoutPayment> findById(UUID id);

    Optional<ReceiptSnapshot> receipt(UUID id);

    Optional<CheckoutPayment> findRequest(UUID userId, String requestKey);

    Optional<CheckoutPayment> findBlockingAttempt(UUID intentId);

    /** Any succeeded attempt, even when an older review case shadows the blocking-attempt read. */
    boolean hasSucceededIntent(UUID intentId);

    CheckoutPayment lock(UUID id);

    void insert(CheckoutPayment payment);

    void save(CheckoutPayment payment);

    Instant now();

    boolean claimEvent(String eventId, String type);

    void finishEvent(String eventId, UUID paymentId, String result);

    void review(UUID paymentId, String reason, String operator);

    List<PaymentReviewCase> reviewCases(UUID afterCaseId, int limit);

    void assignReview(UUID paymentId, String operator, Instant nextCheck);

    void recordRecovery(
            UUID paymentId, String operator, String result, String summary, boolean resolved);

    void recordOutcome(UUID paymentId, UUID dealId, UUID orderId, String outcome);

    String outcome(UUID paymentId);

    void requireRefund(CheckoutPayment payment, String reason, String eventId);

    boolean refundRecorded(UUID paymentId);

    void requireRefundsForExpiredTeam(UUID dealId, String reason, String eventId);

    List<CheckoutPayment> pendingBefore(Instant threshold, int limit);
}
