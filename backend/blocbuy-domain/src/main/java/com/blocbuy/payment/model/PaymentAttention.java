package com.blocbuy.payment.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Payment-owned read projection; no buyer identifier or contact information is selected. */
public record PaymentAttention(
        UUID paymentId,
        String buyerNickname,
        UUID dealId,
        String dealNo,
        String reasonCode,
        BigDecimal amount,
        String currency,
        String refundStatus,
        Instant occurredAt) {}
