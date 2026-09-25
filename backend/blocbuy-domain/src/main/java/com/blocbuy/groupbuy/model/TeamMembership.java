package com.blocbuy.groupbuy.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of one buyer's place in one team: the team's committed state plus how the
 * buyer takes part. Not an aggregate — nothing is ever written through it — so the My Teams list
 * reads it without loading {@link GroupDeal}.
 *
 * @param dealId team identity
 * @param productId product the team buys; resolved to display fields by the application layer
 * @param dealPrice unit price every member pays, major units
 * @param currency ISO-4217 code
 * @param targetSize members needed to succeed
 * @param currentSize members so far
 * @param status committed team status; never derived from the clock on read
 * @param expireTime instant the team stops accepting members
 * @param successTime instant the team filled; null unless it succeeded
 * @param role whether this buyer started or joined the team
 * @param joinTime instant this buyer's participation was recorded, the list sort key
 * @param orderId order backing this participation; null for rows written before orders existed
 */
public record TeamMembership(
        UUID dealId,
        UUID productId,
        BigDecimal dealPrice,
        String currency,
        int targetSize,
        int currentSize,
        DealStatus status,
        Instant expireTime,
        Instant successTime,
        MemberRole role,
        Instant joinTime,
        UUID orderId) {}
