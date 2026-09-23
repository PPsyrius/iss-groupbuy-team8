package com.blocbuy.groupbuy.persistence;

import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.PaidMember;
import com.blocbuy.groupbuy.model.PaidParticipation;
import com.blocbuy.groupbuy.model.ParticipationReceipt;
import com.blocbuy.groupbuy.model.ParticipationResult;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL protocol implementation; SQL failures are technical and never converted to success.
 */
@Repository
@RequiredArgsConstructor
public class PaidGroupDealRepositoryImpl implements PaidGroupDealRepository {
    private final PaidGroupDealMapper mapper;

    /**
     * Reserves then locks the receipt, rejecting a pre-existing committed row with no terminal
     * result.
     */
    @Override
    public ParticipationReceipt reserveAndLock(UUID paymentId, String fingerprint, UUID actorId) {
        int inserted = mapper.reserveReceipt(paymentId, fingerprint, actorId);
        if (inserted != 0 && inserted != 1) {
            throw new IllegalStateException("Unexpected receipt reservation count");
        }
        var row = mapper.lockReceipt(paymentId);
        if (row == null || (inserted == 0 && row.getOutcome() == null)) {
            throw new IllegalStateException("Incomplete committed participation receipt");
        }
        ParticipationResult result =
                row.getOutcome() == null
                        ? null
                        : new ParticipationResult(
                                row.getOutcome(),
                                row.getDealId(),
                                row.getDealStatus(),
                                row.getCurrentSize(),
                                row.getReasonCode());
        return new ParticipationReceipt(row.getRequestFingerprint(), result);
    }

    /** Converts the locked, non-deleted row into an immutable domain snapshot when present. */
    @Override
    public Optional<GroupDeal> lockDeal(UUID dealId) {
        return Optional.ofNullable(GroupDealConverter.toDomain(mapper.lockDeal(dealId)));
    }

    /** Obtains fresh database wall-clock time for decisions made after required locks. */
    @Override
    public Instant currentTime() {
        return mapper.currentTime();
    }

    /** Checks non-deleted membership through uncached SQL under the caller's deal lock. */
    @Override
    public boolean hasActiveMember(UUID dealId, UUID userId) {
        return mapper.hasActiveMember(dealId, userId);
    }

    /** Requires one deal and one paid-leader insert within the caller's shared transaction. */
    @Override
    public void insertStartedDeal(GroupDeal deal, PaidParticipation paid, Instant now) {
        requireOne(mapper.insertDeal(toRow(deal, paid.userId(), now)));
        requireOne(mapper.insertMember(deal.id(), paid.paymentId(), paid.userId(), "leader", now));
    }

    /** Checks the expected progress update and inserts one paid member in the same transaction. */
    @Override
    public void insertJoinedMember(
            GroupDeal before, GroupDeal after, PaidParticipation paid, Instant now) {
        requireOne(
                mapper.updateDeal(
                        toRow(after, paid.userId(), now),
                        before.currentSize(),
                        before.status().code()));
        requireOne(mapper.insertMember(after.id(), paid.paymentId(), paid.userId(), "member", now));
    }

    /**
     * Persists the frozen terminal result, requiring exactly one pending receipt to be completed.
     */
    @Override
    public void complete(UUID paymentId, ParticipationResult result, UUID actorId, Instant now) {
        var row = new ParticipationReceiptDO();
        row.setDealId(result.dealId());
        row.setOutcome(result.outcome());
        row.setDealStatus(result.dealStatus());
        row.setCurrentSize(result.currentSize());
        row.setReasonCode(result.reasonCode());
        requireOne(mapper.completeReceipt(paymentId, row, actorId, now));
    }

    /** Reports whether the payment's member row now carries the order, without judging why not. */
    @Override
    public boolean linkOrder(UUID dealId, UUID paymentId, UUID orderId) {
        return mapper.linkOrder(dealId, paymentId, orderId) == 1;
    }

    /** Projects the payment's member row; the payment key is unique, so there is at most one. */
    @Override
    public Optional<PaidMember> findPaidMember(UUID paymentId) {
        var row = mapper.selectPaidMember(paymentId);
        return row == null
                ? Optional.empty()
                : Optional.of(
                        new PaidMember(
                                row.getDealId(),
                                row.getUserId(),
                                row.getOrderId(),
                                row.getStatus()));
    }

    /** Maps immutable domain values and caller audit context without recalculating frozen terms. */
    private static BizGroupDealDO toRow(GroupDeal deal, UUID actorId, Instant now) {
        var row = new BizGroupDealDO();
        row.setId(deal.id());
        row.setDealNo(deal.dealNo());
        row.setProductId(deal.productId());
        row.setLeaderUserId(deal.leaderUserId());
        row.setDealPrice(deal.dealPrice());
        row.setCurrency(deal.currency());
        row.setTargetSize(deal.targetSize());
        row.setCurrentSize(deal.currentSize());
        row.setStatus(deal.status().code());
        row.setStartTime(deal.startTime());
        row.setExpireTime(deal.expireTime());
        row.setSuccessTime(deal.successTime());
        row.setCreateTime(now);
        row.setCreateBy(actorId);
        row.setUpdateTime(now);
        row.setUpdateBy(actorId);
        return row;
    }

    /**
     * Treats missing or excess writes as technical protocol failures so the transaction rolls back.
     */
    private static void requireOne(int affected) {
        if (affected != 1) {
            throw new IllegalStateException(
                    "Groupbuy write protocol affected unexpected row count");
        }
    }
}
