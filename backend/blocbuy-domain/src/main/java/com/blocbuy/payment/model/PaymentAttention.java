package com.blocbuy.payment.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment-owned read projection; no buyer contact information is selected. The buyer id exists only
 * for the application's single identity lookup per page and is never returned over HTTP.
 */
public record PaymentAttention(
        UUID paymentId,
        UUID buyerId,
        UUID dealId,
        String dealNo,
        String reasonCode,
        BigDecimal amount,
        String currency,
        String refundStatus,
        Instant occurredAt) {}
