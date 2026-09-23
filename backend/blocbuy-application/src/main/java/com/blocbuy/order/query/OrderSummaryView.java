package com.blocbuy.order.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order-side view of one buyer order, safe to hand across the application boundary. Carries the
 * frozen product snapshot and never exposes the backing payment id or receiver PII; the Order
 * aggregate itself never reaches the adapter.
 *
 * @param orderId order database identity
 * @param orderNo business-facing order number
 * @param dealId group deal this order belongs to
 * @param productId ordered product identity (snapshot source)
 * @param productTitle product title frozen at order time
 * @param productImage product image frozen at order time; may be null
 * @param quantity units purchased
 * @param unitPrice deal unit price at order time, major units
 * @param totalAmount {@code quantity * unitPrice}, major units
 * @param currency ISO-4217 code
 * @param status lifecycle code: pending, confirmed or cancelled
 * @param paidTime instant the backing payment succeeded
 * @param cancelTime instant the order was cancelled; null unless cancelled
 * @param createTime row creation instant, the list sort key
 */
public record OrderSummaryView(
        UUID orderId,
        String orderNo,
        UUID dealId,
        UUID productId,
        String productTitle,
        String productImage,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String currency,
        String status,
        Instant paidTime,
        Instant cancelTime,
        Instant createTime) {}
