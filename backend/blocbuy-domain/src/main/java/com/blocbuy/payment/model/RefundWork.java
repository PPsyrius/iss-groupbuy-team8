package com.blocbuy.payment.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A claimed obligation and its authoritative payment facts. No customer input. */
public record RefundWork(
        UUID id,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        String providerIntentId,
        String paymentStatus,
        BigDecimal paidAmount,
        String paidCurrency,
        String providerRefundId,
        Instant firstAttemptAt,
        Instant now,
        int version) {
    public String providerKey() {
        return "blocbuy-refund-" + id;
    }

    public boolean valid() {
        // payment_status arrives from the database, where lifecycle codes are lowercase (V2).
        return "succeeded".equals(paymentStatus)
                && amount.signum() > 0
                && amount.compareTo(paidAmount) == 0
                && currency.equals(paidCurrency)
                && "sgd".equals(currency)
                && providerIntentId != null;
    }
}
