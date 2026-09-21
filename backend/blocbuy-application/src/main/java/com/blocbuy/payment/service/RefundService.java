package com.blocbuy.payment.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.gateway.RefundGateway;
import com.blocbuy.payment.model.RefundWork;
import com.blocbuy.payment.repository.RefundRepository;
import java.time.Duration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One bounded job per invocation. Stripe I/O is outside both short DB transactions. Refund
 * lifecycle states stored via {@code finish} are lowercase database codes (V2 convention); audit
 * result codes remain uppercase.
 */
@lombok.extern.slf4j.Slf4j
public final class RefundService {
    private static final String REVIEW = "review";
    private final RefundRepository repository;
    private final RefundGateway gateway;
    private final TransactionTemplate tx;

    public RefundService(
            RefundRepository repository,
            RefundGateway gateway,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.gateway = gateway;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setTimeout(5);
    }

    public boolean processNext() {
        RefundWork work = tx.execute(s -> repository.claimNext());
        if (work == null) return false;
        if (!work.valid()) {
            finish(work, work.providerRefundId(), REVIEW, "PAYMENT_MISMATCH");
            return true;
        }
        RefundGateway.Snapshot snapshot;
        try {
            if (work.providerRefundId() != null) {
                snapshot = gateway.retrieveRefund(work.providerRefundId());
            } else if (Duration.between(work.firstAttemptAt(), work.now()).toHours() >= 23) {
                // Stripe can prune idempotency keys after 24h. Never create again after our cutoff.
                snapshot = gateway.findRefund(work);
                if (snapshot == null) {
                    finish(work, null, REVIEW, "UNKNOWN_AFTER_RETRY_WINDOW");
                    return true;
                }
            } else {
                snapshot = gateway.createRefund(work);
            }
        } catch (BizException unavailable) {
            finish(work, work.providerRefundId(), "processing", "PROVIDER_RETRY");
            return true;
        }
        apply(work, snapshot);
        return true;
    }

    /** Operator recovery verifies an existing provider refund; it cannot send another refund. */
    public boolean reconcile(java.util.UUID paymentId, String providerId) {
        if (providerId == null || !providerId.matches("re_[A-Za-z0-9]{1,128}")) {
            throw new IllegalArgumentException("Invalid provider refund reference");
        }
        RefundWork work = tx.execute(s -> repository.claimReview(paymentId));
        if (work == null) return false;
        if (!work.valid()) {
            finish(work, work.providerRefundId(), REVIEW, "PAYMENT_MISMATCH");
            return true;
        }
        RefundGateway.Snapshot snapshot;
        try {
            snapshot = gateway.retrieveRefund(providerId);
        } catch (BizException unavailable) {
            finish(work, work.providerRefundId(), REVIEW, "OPERATOR_PROVIDER_RETRY");
            return true;
        }
        apply(work, snapshot);
        return true;
    }

    private void apply(RefundWork work, RefundGateway.Snapshot snapshot) {
        if (!matches(work, snapshot)) {
            finish(work, work.providerRefundId(), REVIEW, "PROVIDER_MISMATCH");
            return;
        }
        String state =
                switch (snapshot.status()) {
                    case "succeeded" -> "succeeded";
                    case "failed", "canceled" -> "failed";
                    case "pending" -> "processing";
                    default -> REVIEW;
                };
        finish(work, snapshot.id(), state, "PROVIDER_" + state.toUpperCase(java.util.Locale.ROOT));
    }

    private static boolean matches(RefundWork work, RefundGateway.Snapshot snapshot) {
        return snapshot != null
                && snapshot.id() != null
                && snapshot.status() != null
                && (work.providerRefundId() == null
                        || work.providerRefundId().equals(snapshot.id()))
                && work.providerIntentId().equals(snapshot.paymentIntentId())
                && work.id().toString().equals(snapshot.obligationId())
                && work.amount().movePointRight(2).longValueExact() == snapshot.amountMinor()
                && work.currency().equals(snapshot.currency());
    }

    private void finish(RefundWork work, String providerId, String state, String result) {
        tx.executeWithoutResult(s -> repository.finish(work, providerId, state, result));
        if (REVIEW.equals(state) || "failed".equals(state)) {
            log.warn(
                    "Refund needs operator review: paymentId={}, state={}, result={}",
                    work.paymentId(),
                    state,
                    result);
        }
    }
}
