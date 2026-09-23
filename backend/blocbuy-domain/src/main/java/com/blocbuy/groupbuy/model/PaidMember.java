package com.blocbuy.groupbuy.model;

import java.util.UUID;

/**
 * The membership one payment produced, read back for reconciliation rather than for admission.
 * {@code orderId} is null between the member insert and the order backfill, which happen in the
 * same transaction — a committed row with a null order is evidence of a half-applied fulfilment.
 */
public record PaidMember(UUID dealId, UUID userId, UUID orderId, String status) {}
