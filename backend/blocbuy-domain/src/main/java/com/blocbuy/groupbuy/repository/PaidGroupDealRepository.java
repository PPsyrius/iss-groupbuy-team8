package com.blocbuy.groupbuy.repository;

import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.PaidParticipation;
import com.blocbuy.groupbuy.model.ParticipationReceipt;
import com.blocbuy.groupbuy.model.ParticipationResult;
import java.time.Instant;
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
}
