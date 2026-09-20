package com.blocbuy.order.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order-owned buyer read model. CheckoutPayment/refund and group facts are joined once, never per
 * row.
 */
public interface OrderHistory {
    List<Entry> page(UUID buyer, int offset, int limit);

    long count(UUID buyer);

    record Entry(
            UUID id,
            String orderNo,
            UUID productId,
            String productTitle,
            int quantity,
            String amount,
            String currency,
            String status,
            String paymentStatus,
            UUID paymentId,
            UUID dealId,
            String dealStatus,
            String refundStatus,
            Instant createdAt) {}
}
