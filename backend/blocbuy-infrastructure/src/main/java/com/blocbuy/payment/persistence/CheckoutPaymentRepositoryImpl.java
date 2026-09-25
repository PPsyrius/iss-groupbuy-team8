package com.blocbuy.payment.persistence;

import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.PaymentReviewCase;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * MyBatis implementation for the opt-in Checkout schema. Provider calls never run here. Version
 * conflicts throw so the entire caller transaction can be retried safely.
 */
@Repository
@RequiredArgsConstructor
public class CheckoutPaymentRepositoryImpl implements CheckoutPaymentRepository {
    private final PaymentMapper mapper;

    @Override
    public Optional<ReceiptSnapshot> receipt(UUID id) {
        var row = mapper.receipt(id);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(
                new ReceiptSnapshot(
                        row.getUserId(),
                        CheckoutPayment.Status.valueOf(row.getStatus().toUpperCase(Locale.ROOT)),
                        row.getAmount(),
                        row.getCurrency(),
                        row.getFulfilmentResult(),
                        row.getFulfilledDealId(),
                        row.getOrderId()));
    }

    @Override
    public Optional<CheckoutPayment> findById(UUID id) {
        return optional(mapper.find(id));
    }

    @Override
    public Optional<CheckoutPayment> findRequest(UUID userId, String key) {
        return optional(mapper.request(userId, key));
    }

    @Override
    public Optional<CheckoutPayment> findBlockingAttempt(UUID intentId) {
        return optional(mapper.blocking(intentId));
    }

    @Override
    public CheckoutPayment lock(UUID id) {
        return optional(mapper.lock(id))
                .orElseThrow(() -> new IllegalStateException("Missing payment"));
    }

    @Override
    public void insert(CheckoutPayment payment) {
        changed(mapper.insert(PaymentConverter.toRow(payment)));
    }

    @Override
    public void save(CheckoutPayment payment) {
        changed(mapper.save(PaymentConverter.toRow(payment)));
    }

    @Override
    public Instant now() {
        return mapper.now();
    }

    @Override
    public boolean claimEvent(String id, String type) {
        return mapper.claimEvent(id, type) == 1;
    }

    @Override
    public void finishEvent(String eventId, UUID id, String result) {
        changed(mapper.finishEvent(eventId, id, result));
    }

    @Override
    public void review(UUID id, String reason, String operator) {
        changed(mapper.review(id, reason, operator));
        changed(mapper.openCase(id, reason));
    }

    @Override
    public List<PaymentReviewCase> reviewCases(UUID afterCaseId, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be 1..100");
        }
        return mapper.reviewCases(afterCaseId, limit);
    }

    @Override
    public void assignReview(UUID id, String operator, Instant nextCheck) {
        changed(mapper.assignCase(id, operator, nextCheck));
        changed(mapper.review(id, "CASE_ASSIGNED", operator));
    }

    @Override
    public void recordRecovery(
            UUID id, String operator, String result, String summary, boolean resolved) {
        changed(mapper.review(id, "RECOVERY_" + result, operator));
        changed(mapper.recordRecovery(id, operator, summary, resolved));
    }

    @Override
    public void recordOutcome(UUID id, UUID deal, UUID order, String result) {
        changed(mapper.outcome(id, deal, order, result));
    }

    @Override
    public String outcome(UUID id) {
        return mapper.getOutcome(id);
    }

    @Override
    public void requireRefund(CheckoutPayment payment, String reason, String eventId) {
        changed(mapper.refund(payment.id(), reason, eventId));
    }

    @Override
    public boolean refundRecorded(UUID id) {
        return mapper.refundRecorded(id);
    }

    @Override
    public void requireRefundsForExpiredTeam(UUID dealId, String reason, String eventId) {
        int expected = mapper.lockRefundableMemberIds(dealId).size();
        int changed = mapper.refundExpiredTeam(dealId, reason, eventId);
        if (changed != expected) {
            throw new IllegalStateException(
                    "Expired-team refund write conflict; retry transaction");
        }
    }

    @Override
    public List<CheckoutPayment> pendingBefore(Instant threshold, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be 1..100");
        }
        return mapper.pending(threshold, limit).stream().map(PaymentConverter::fromRow).toList();
    }

    private static Optional<CheckoutPayment> optional(BizPaymentDO row) {
        return Optional.ofNullable(PaymentConverter.fromRow(row));
    }

    private static void changed(int count) {
        if (count != 1) {
            throw new IllegalStateException("CheckoutPayment write conflict; retry transaction");
        }
    }
}
