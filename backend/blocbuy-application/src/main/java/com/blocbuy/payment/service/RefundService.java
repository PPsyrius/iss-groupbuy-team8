package com.blocbuy.payment.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.payment.exception.PaymentErrorCode;
import com.blocbuy.payment.gateway.RefundGateway;
import com.blocbuy.payment.model.RefundWork;
import com.blocbuy.payment.repository.RefundRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
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
    private static final String FAILED = "failed";
    private static final String SUCCEEDED = "succeeded";
    private static final String CANCELED = "canceled";
    private static final String PENDING = "pending";
    private static final String PROCESSING = "processing";
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
        Optional<RefundWork> claimed = claim(repository::claimNext);
        if (claimed.isEmpty()) return false;
        RefundWork work = claimed.orElseThrow();
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
        } catch (BizException refused) {
            boolean permanent = permanent(refused);
            finish(
                    work,
                    work.providerRefundId(),
                    permanent ? REVIEW : PROCESSING,
                    permanent ? "PROVIDER_STATE_MISMATCH" : "PROVIDER_RETRY");
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
        Optional<RefundWork> claimed = claim(() -> repository.claimReview(paymentId));
        if (claimed.isEmpty()) return false;
        RefundWork work = claimed.orElseThrow();
        if (!work.valid()) {
            finish(work, work.providerRefundId(), REVIEW, "PAYMENT_MISMATCH");
            return true;
        }
        RefundGateway.Snapshot snapshot;
        try {
            snapshot = gateway.retrieveRefund(providerId);
        } catch (BizException refused) {
            // The stored state stays review either way: this row is already in an operator's
            // hands, and putting it back into processing would hand it to the automatic worker
            // mid-investigation. Only the recorded reason distinguishes the two failures.
            finish(
                    work,
                    work.providerRefundId(),
                    REVIEW,
                    permanent(refused)
                            ? "OPERATOR_PROVIDER_STATE_MISMATCH"
                            : "OPERATOR_PROVIDER_RETRY");
            return true;
        }
        apply(work, snapshot);
        return true;
    }

    /**
     * Runs one claim in its own short transaction and says plainly whether anything was claimed.
     *
     * <p>"Nothing is due" is expressed by the returned {@code Optional}, built inside the
     * transaction, rather than by a null coming back out of the transaction template — the template
     * returns null only because its callback did, which makes an absent row and a framework-level
     * null indistinguishable at the call site.
     */
    private Optional<RefundWork> claim(Supplier<RefundWork> claim) {
        return tx.execute(status -> Optional.ofNullable(claim.get()));
    }

    /**
     * Splits provider refusals by kind, because they need opposite treatment.
     *
     * <p>{@code PAYMENT_STATE_INVALID} means the PaymentIntent is not in a refundable state, or its
     * amount, currency or mode does not match this obligation. Nothing about that changes by asking
     * again, so retrying it every 30 seconds would burn a provider call per cycle for as long as
     * the obligation lives and bury real failures in the log; it belongs to an operator instead.
     * {@code PAYMENT_PROVIDER_ERROR} is a transport or availability failure, which is exactly what
     * retrying is for — the idempotency key keeps a retry from sending money twice.
     */
    private static boolean permanent(BizException refused) {
        return refused.errorCode() == PaymentErrorCode.PAYMENT_STATE_INVALID;
    }

    private void apply(RefundWork work, RefundGateway.Snapshot snapshot) {
        if (!matches(work, snapshot)) {
            finish(work, work.providerRefundId(), REVIEW, "PROVIDER_MISMATCH");
            return;
        }
        String state =
                switch (snapshot.status()) {
                    case SUCCEEDED -> SUCCEEDED;
                    case FAILED, CANCELED -> FAILED;
                    case PENDING -> PROCESSING;
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
        if (REVIEW.equals(state) || FAILED.equals(state)) {
            log.warn(
                    "Refund needs operator review: paymentId={}, state={}, result={}",
                    work.paymentId(),
                    state,
                    result);
        }
    }
}
