package com.blocbuy.payment.persistence;

import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.model.CheckoutQuote;
import java.util.Locale;

/**
 * Explicit row/aggregate conversion. Lifecycle status codes are stored lowercase (V2 convention).
 */
final class PaymentConverter {
    private PaymentConverter() {}

    static CheckoutPayment fromRow(BizPaymentDO row) {
        if (row == null) {
            return null;
        }
        var quote =
                new CheckoutQuote(
                        row.getIntentId(),
                        row.getUserId(),
                        row.getOperation(),
                        row.getBusinessId(),
                        row.getAmount(),
                        row.getCurrency(),
                        row.getQuoteValidUntil(),
                        row.getBusinessDeadline());
        return new CheckoutPayment(
                row.getId(),
                quote,
                row.getRequestKey(),
                row.getCreateTime(),
                row.getSessionExpiresAt(),
                row.getSuccessUrl(),
                row.getCancelUrl(),
                row.getCheckoutSessionId(),
                row.getCheckoutUrl(),
                row.getProviderPaymentIntentId(),
                CheckoutPayment.Status.valueOf(row.getStatus().toUpperCase(Locale.ROOT)),
                row.getVersion());
    }

    static BizPaymentDO toRow(CheckoutPayment payment) {
        var row = new BizPaymentDO();
        row.setId(payment.id());
        row.setIntentId(payment.quote().intentId());
        row.setUserId(payment.quote().userId());
        row.setOperation(payment.quote().operation());
        row.setBusinessId(payment.quote().businessId());
        row.setAmount(payment.quote().amount());
        row.setCurrency(payment.quote().currency());
        row.setQuoteValidUntil(payment.quote().quoteValidUntil());
        row.setBusinessDeadline(payment.quote().businessDeadline());
        row.setRequestKey(payment.requestKey());
        row.setCreateTime(payment.createdAt());
        row.setSessionExpiresAt(payment.sessionExpiresAt());
        row.setSuccessUrl(payment.successUrl());
        row.setCancelUrl(payment.cancelUrl());
        row.setCheckoutSessionId(payment.sessionId());
        row.setCheckoutUrl(payment.checkoutUrl());
        row.setProviderPaymentIntentId(payment.providerPaymentIntentId());
        row.setStatus(payment.status().name().toLowerCase(Locale.ROOT));
        row.setVersion(payment.version());
        return row;
    }
}
