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
    ParticipationReceipt reserveAndLock(UUID paymentId, String fingerprint, UUID actorId);

    Optional<GroupDeal> lockDeal(UUID dealId);

    /** Fresh wall clock, not transaction-start time; call only after required locks. */
    Instant currentTime();

    boolean hasActiveMember(UUID dealId, UUID userId);

    void insertStartedDeal(GroupDeal deal, PaidParticipation paid, Instant now);

    void insertJoinedMember(GroupDeal before, GroupDeal after, PaidParticipation paid, Instant now);

    void complete(UUID paymentId, ParticipationResult result, UUID actorId, Instant now);
}
