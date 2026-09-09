package com.blocbuy.groupbuy.persistence;

import com.blocbuy.groupbuy.model.GroupDeal;
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

    @Override
    public Optional<GroupDeal> lockDeal(UUID dealId) {
        return Optional.ofNullable(GroupDealConverter.toDomain(mapper.lockDeal(dealId)));
    }

    @Override
    public Instant currentTime() {
        return mapper.currentTime();
    }

    @Override
    public boolean hasActiveMember(UUID dealId, UUID userId) {
        return mapper.hasActiveMember(dealId, userId);
    }

    @Override
    public void insertStartedDeal(GroupDeal deal, PaidParticipation paid, Instant now) {
        requireOne(mapper.insertDeal(toRow(deal, paid.userId(), now)));
        requireOne(mapper.insertMember(deal.id(), paid.paymentId(), paid.userId(), "leader", now));
    }

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

    private static void requireOne(int affected) {
        if (affected != 1) {
            throw new IllegalStateException(
                    "Groupbuy write protocol affected unexpected row count");
        }
    }
}
