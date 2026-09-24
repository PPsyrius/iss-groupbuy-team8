package com.blocbuy.payment.gateway;

import com.blocbuy.payment.model.RefundWork;

/** Refunds always return money to the original payment method. */
public interface RefundGateway {
    Snapshot createRefund(RefundWork work);

    Snapshot retrieveRefund(String providerId);

    Snapshot findRefund(RefundWork work);

    record Snapshot(
            String id,
            String paymentIntentId,
            String obligationId,
            long amountMinor,
            String currency,
            String status) {}
}
