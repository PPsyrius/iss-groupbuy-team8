package com.blocbuy.payment.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Operator work item; provider summary contains identifiers/statuses, never payload or card data.
 */
public record PaymentReviewCase(
        UUID caseId,
        UUID paymentId,
        String reason,
        String owner,
        String status,
        Instant lastCheckedAt,
        Instant nextCheckAt,
        Instant resolvedAt,
        String providerSummary) {}
