package com.blocbuy.payment.gateway;

import com.blocbuy.payment.event.PaymentSucceeded;
import com.blocbuy.payment.model.CheckoutQuote;
import java.util.UUID;

/**
 * Integration contract owned jointly with groupbuy/order. No production stub is supplied. All
 * methods use the caller's database transaction and perform no remote I/O.
 */
public interface PaymentBusinessPort {
    /** Lock group (JOIN), then intent. Verify owner/eligibility and return the stored quote. */
    CheckoutQuote lockQuote(UUID authenticatedUser, UUID intentId);

    /** Lock group (JOIN), then intent, BEFORE the caller locks a payment. */
    void lockForSettlement(CheckoutQuote quote);

    /**
     * Idempotently create membership AND order, or return a business rejection. Enforce one
     * fulfilled payment per intent under its lock. Technical failures must throw, not reject.
     */
    Outcome fulfil(PaymentSucceeded event);

    /**
     * Verify the actual membership/order and their payment links; a payment result flag is
     * insufficient.
     */
    FulfilmentEvidence fulfilmentEvidence(PaymentSucceeded event);

    /** NONE means no effective fulfilment; partial or mismatched links are INCONSISTENT. */
    enum FulfilmentEvidence {
        NONE,
        COMPLETE,
        INCONSISTENT
    }

    /** Hold the same group row lock used by joining; verify the group really is EXPIRED. */
    void lockExpiredTeam(UUID dealId);

    record Outcome(UUID dealId, UUID orderId, String rejection) {
        public Outcome {
            if (rejection == null && (dealId == null || orderId == null)) {
                throw new IllegalArgumentException("Acceptance requires a real team and order");
            }
            if (rejection != null && !rejection.matches("[A-Z_]{1,64}")) {
                throw new IllegalArgumentException("Use a non-sensitive rejection code");
            }
        }

        public boolean accepted() {
            return rejection == null;
        }
    }
}
