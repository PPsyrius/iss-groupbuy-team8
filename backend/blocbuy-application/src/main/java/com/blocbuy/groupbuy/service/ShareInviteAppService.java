package com.blocbuy.groupbuy.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.ShareInvite;
import com.blocbuy.groupbuy.query.ShareInviteView;
import com.blocbuy.groupbuy.repository.GroupDealRepository;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import com.blocbuy.groupbuy.repository.ShareInviteRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC7: a member of an open team mints — or gets back — their invite into it. Idempotent by design:
 * one invite per team and sharer, so "share again" returns the same reference and no {@code
 * Idempotency-Key} is needed.
 *
 * <p>No deal lock is taken. Minting only reads the team's status; a team that closes right after a
 * mint simply refuses the later JOIN, and the invite then attributes nothing.
 */
@Service
@RequiredArgsConstructor
public class ShareInviteAppService {

    private final GroupDealRepository deals;
    private final PaidGroupDealRepository participation;
    private final ShareInviteRepository invites;
    private final RandomGenerator random = new SecureRandom();

    /**
     * Returns the caller's invite into the team, minting it on first use.
     *
     * @throws BizException {@code GROUPBUY_404_001} when the team does not exist or the caller is
     *     not an active member of it — the two are indistinguishable on purpose, so a non-member
     *     learns nothing about the team; {@code DEAL_CLOSED} when the team no longer accepts
     *     members
     */
    @Transactional(timeout = 10)
    public ShareInviteView mint(UUID userId, UUID dealId) {
        if (userId == null || dealId == null) {
            throw new BizException(GroupbuyErrorCode.GROUPBUY_PLACEHOLDER);
        }
        GroupDeal deal =
                deals.findById(dealId)
                        .orElseThrow(
                                () -> new BizException(GroupbuyErrorCode.GROUPBUY_PLACEHOLDER));
        if (!participation.hasActiveMember(dealId, userId)) {
            throw new BizException(GroupbuyErrorCode.GROUPBUY_PLACEHOLDER);
        }
        Instant now = participation.currentTime();
        if (deal.status() != DealStatus.OPEN || !now.isBefore(deal.expireTime())) {
            throw new BizException(GroupbuyErrorCode.DEAL_CLOSED);
        }
        ShareInvite invite =
                invites.findByDealAndSharer(dealId, userId)
                        .orElseGet(() -> mintNew(dealId, userId));
        return new ShareInviteView(invite.ref(), deal.id(), deal.productId(), deal.expireTime());
    }

    /**
     * Inserts a fresh invite and reads back whichever row holds the pair. A concurrent mint by the
     * same sharer makes this insert a no-op, and both callers then read the same winner.
     */
    private ShareInvite mintNew(UUID dealId, UUID userId) {
        invites.insertIfAbsent(ShareInvite.mint(dealId, userId, random));
        return invites.findByDealAndSharer(dealId, userId)
                .orElseThrow(() -> new IllegalStateException("Invite insert left no row"));
    }
}
