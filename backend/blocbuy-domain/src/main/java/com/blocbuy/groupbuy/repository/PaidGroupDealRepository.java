package com.blocbuy.groupbuy.repository;

import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.PaidMember;
import com.blocbuy.groupbuy.model.PaidParticipation;
import com.blocbuy.groupbuy.model.ParticipationReceipt;
import com.blocbuy.groupbuy.model.ParticipationResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Groupbuy write protocol. Every operation requires the same application transaction; lock order is
 * receipt then deal. All other writers/deleters must also lock the deal before member changes.
 */
public interface PaidGroupDealRepository {
    /**
     * Reserves or locks a payment's receipt; existing fingerprint and outcome remain authoritative.
     */
    ParticipationReceipt reserveAndLock(UUID paymentId, String fingerprint, UUID actorId);

    /** Locks a non-deleted deal after receipt locking, returning empty when unavailable. */
    Optional<GroupDeal> lockDeal(UUID dealId);

    /** Fresh wall clock, not transaction-start time; call only after required locks. */
    Instant currentTime();

    /** Checks non-deleted membership under the deal lock to prevent duplicate buyer admission. */
    boolean hasActiveMember(UUID dealId, UUID userId);

    /** Persists the initial aggregate and its paid leader in the caller's transaction. */
    void insertStartedDeal(GroupDeal deal, PaidParticipation paid, Instant now);

    /**
     * Persists the validated transition and paid member atomically under the existing deal lock.
     */
    void insertJoinedMember(GroupDeal before, GroupDeal after, PaidParticipation paid, Instant now);

    /**
     * Freezes a pending receipt's terminal result in the same transaction as admission or
     * rejection.
     */
    void complete(UUID paymentId, ParticipationResult result, UUID actorId, Instant now);

    /**
     * Backfills the order backing a payment's membership, in the transaction that inserts it.
     * Re-linking the same order is allowed so a replay is harmless; a different order is not.
     *
     * @return false when no active member of this deal carries that payment
     */
    boolean linkOrder(UUID dealId, UUID paymentId, UUID orderId);

    /** The membership a payment produced, or empty when it admitted nobody. Reconciliation only. */
    Optional<PaidMember> findPaidMember(UUID paymentId);

    /**
     * Ids of open deals whose admission window has closed, oldest deadline first. One batch read
     * for a whole sweep — the caller then resolves each team in its own short transaction.
     *
     * @param limit hard upper bound on the returned ids, so one run cannot grow unbounded
     */
    List<UUID> dueForExpiry(Instant now, int limit);

    /**
     * Persists a validated expiry under the caller's deal lock. The UPDATE is status-guarded, so a
     * team another writer already resolved matches nothing.
     *
     * @throws com.blocbuy.common.exception.BizException {@code DEAL_CLOSED} when no open row
     *     matched, which the caller swallows as an idempotent replay
     */
    void expire(GroupDeal before, GroupDeal after, Instant now);
}
