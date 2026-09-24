package com.blocbuy.groupbuy.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Groupbuy-owned My Teams row, safe to hand across the application boundary. Team state comes from
 * groupbuy; product display fields are resolved through {@link
 * com.blocbuy.groupbuy.gateway.ListingDisplayGateway} (catalog implements it), so this query model
 * is owned by groupbuy and never touches catalog persistence.
 *
 * @param dealId team identity
 * @param productId product the team buys
 * @param productTitle current catalog title; null when the product is no longer active
 * @param productImage current catalog cover image; null when absent or the product is gone
 * @param dealPrice unit price every member pays, major units
 * @param currency ISO-4217 code
 * @param targetSize members needed to succeed
 * @param currentSize members so far
 * @param status committed team status code: open, succeeded or expired (failed and canceled are
 *     reserved)
 * @param expireTime instant the team stops accepting members
 * @param successTime instant the team filled; null unless it succeeded
 * @param role participation code: leader or member
 * @param joinTime instant this buyer's participation was recorded, the list sort key
 * @param orderId order backing this participation; null for legacy rows
 */
public record MyTeamView(
        UUID dealId,
        UUID productId,
        String productTitle,
        String productImage,
        BigDecimal dealPrice,
        String currency,
        int targetSize,
        int currentSize,
        String status,
        Instant expireTime,
        Instant successTime,
        String role,
        Instant joinTime,
        UUID orderId) {}
