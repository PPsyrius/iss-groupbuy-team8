package com.blocbuy.groupbuy.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Bounded SQL for the receipt-first groupbuy write protocol. */
@Mapper
public interface PaidGroupDealMapper {
    /**
     * Inserts a pending receipt unless its payment key exists; returns one for insertion or zero.
     */
    int reserveReceipt(
            @Param("paymentId") UUID paymentId,
            @Param("fingerprint") String fingerprint,
            @Param("actorId") UUID actorId);

    /**
     * Selects the payment receipt FOR UPDATE, bypassing caches to observe committed replay facts.
     */
    ParticipationReceiptDO lockReceipt(@Param("paymentId") UUID paymentId);

    /** Locks a non-deleted deal row FOR UPDATE after the caller has locked its receipt. */
    BizGroupDealDO lockDeal(@Param("dealId") UUID dealId);

    /**
     * Reads PostgreSQL wall-clock time rather than transaction-start time after lock acquisition.
     */
    Instant currentTime();

    /** Reads non-deleted membership without caching while the caller holds the deal lock. */
    boolean hasActiveMember(@Param("dealId") UUID dealId, @Param("userId") UUID userId);

    /**
     * Inserts the initial immutable deal snapshot and audit fields; returns the affected row count.
     */
    int insertDeal(BizGroupDealDO deal);

    /**
     * Inserts a paid leader or member with a globally unique payment binding and, for an attributed
     * join, the sharer it came through (null otherwise).
     */
    int insertMember(
            @Param("dealId") UUID dealId,
            @Param("paymentId") UUID paymentId,
            @Param("userId") UUID userId,
            @Param("role") String role,
            @Param("sharerUserId") UUID sharerUserId,
            @Param("now") Instant now);

    /** Updates progress only if stored size and status match the locked pre-transition snapshot. */
    int updateDeal(
            @Param("deal") BizGroupDealDO deal,
            @Param("expectedSize") int expectedSize,
            @Param("expectedStatus") String expectedStatus);

    /**
     * Binds the order to the payment's member row, tolerating a re-link of the same order so a
     * replayed fulfilment does not look like a missing row.
     */
    int linkOrder(
            @Param("dealId") UUID dealId,
            @Param("paymentId") UUID paymentId,
            @Param("orderId") UUID orderId);

    /** Reads the membership a payment produced; the payment key is globally unique. */
    PaidMemberDO selectPaidMember(@Param("paymentId") UUID paymentId);

    /**
     * Reads the ids of open deals already past their deadline, oldest first, through the partial
     * index {@code idx_biz_group_deal_status_expire}. One query per sweep, never one per team.
     */
    List<UUID> selectDueForExpiry(@Param("now") Instant now, @Param("limit") int limit);

    /** Records or extends database-clock backoff for a failed team-expiry attempt. */
    int deferExpiryRetry(
            @Param("dealId") UUID dealId, @Param("retryDelaySeconds") double retryDelaySeconds);

    /**
     * Resolves one open deal as expired under the caller's row lock; a deal another writer already
     * resolved matches nothing and returns zero.
     */
    int expireDeal(@Param("deal") BizGroupDealDO deal);

    /**
     * Writes a terminal result only to a pending receipt, preserving completed replay snapshots.
     */
    int completeReceipt(
            @Param("paymentId") UUID paymentId,
            @Param("result") ParticipationReceiptDO result,
            @Param("actorId") UUID actorId,
            @Param("now") Instant now);
}
