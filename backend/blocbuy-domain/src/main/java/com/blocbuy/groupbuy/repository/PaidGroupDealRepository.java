package com.blocbuy.groupbuy.repository;

import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.PaidMember;
import com.blocbuy.groupbuy.model.PaidParticipation;
import com.blocbuy.groupbuy.model.ParticipationReceipt;
import com.blocbuy.groupbuy.model.ParticipationResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Groupbuy write protocol. Every operation requires the same application transaction; lock order is
 * receipt then deal. All other writers/deleters must also lock the deal before member changes.
 * General aggregate reads use {@link GroupDealRepository}.
 */
public interface PaidGroupDealRepository {
    /**
     * Reserves or locks a payment's receipt; existing fingerprint and outcome remain authoritative.
     */
    ParticipationReceipt reserveAndLock(UUID paymentId, String fingerprint, UUID actorId);

    /** Locks a non-deleted deal after receipt locking, returning empty when unavailable. */
    Optional<GroupDeal> lockDeal(UUID dealId);

    /**
     * Fresh wall clock, not transaction-start time. Decisions must read it only after the required
     * locks; reading it earlier is fine only to pick candidates that are re-checked under the lock.
     */
    Instant currentTime();

    /** Checks non-deleted membership under the deal lock to prevent duplicate buyer admission. */
    boolean hasActiveMember(UUID dealId, UUID userId);

    /** Persists the initial aggregate and its paid leader in the caller's transaction. */
    void insertStartedDeal(GroupDeal deal, PaidParticipation paid, Instant now);

    /**
     * Persists the validated transition and paid member atomically under the existing deal lock.
     *
     * @param sharerUserId the member whose invite the buyer came through, or null when unattributed
     */
    void insertJoinedMember(
            GroupDeal before,
            GroupDeal after,
            PaidParticipation paid,
            UUID sharerUserId,
            Instant now);

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
     * Ids of open deals whose admission window has closed and whose retry delay has elapsed, oldest
     * deadline first. One batch read for a whole sweep — the caller then resolves each team in its
     * own short transaction.
     *
     * @param limit hard upper bound on the returned ids, so one run cannot grow unbounded
     */
    List<UUID> dueForExpiry(Instant now, int limit);

    /**
     * Defers one failed expiry on the database clock. This write runs after the team's transaction
     * rolled back, so a poisoned oldest row cannot consume a batch slot on every sweep.
     */
    void deferExpiryRetry(UUID dealId, Duration delay);

    /**
     * Persists a validated expiry under the caller's deal lock. The UPDATE is status-guarded, so a
     * team another writer already resolved matches nothing.
     *
     * @throws com.blocbuy.common.exception.BizException {@code DEAL_CLOSED} when no open row
     *     matched, which the caller swallows as an idempotent replay
     */
    void expire(GroupDeal before, GroupDeal after, Instant now);
}
