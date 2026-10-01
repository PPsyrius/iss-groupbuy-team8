package com.blocbuy.groupbuy.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.gateway.RateLimitGateway;
import com.blocbuy.common.gateway.RateLimitGateway.RateLimit;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.ShareInvite;
import com.blocbuy.groupbuy.query.ResolvedInviteView;
import com.blocbuy.groupbuy.query.ShareInviteView;
import com.blocbuy.groupbuy.repository.GroupDealRepository;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import com.blocbuy.groupbuy.repository.ShareInviteRepository;
import com.blocbuy.identity.service.UserAppService;
import java.security.SecureRandom;
import java.time.Duration;
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
 *
 * <p>Anyone holding a link may also resolve its reference to the sharer's public nickname, for the
 * landing page shown before sign-in (#201).
 */
@Service
@RequiredArgsConstructor
public class ShareInviteAppService {

    private final GroupDealRepository deals;
    private final PaidGroupDealRepository participation;
    private final ShareInviteRepository invites;
    private final RateLimitGateway rateLimits;
    private final UserAppService users;
    private final RandomGenerator random = new SecureRandom();

    /**
     * Per client address. References are 128 random bits, so this is defence in depth against
     * enumeration, sized so a class behind one NAT can still open its links.
     */
    static final RateLimit RESOLVE_LIMIT =
            new RateLimit("invite-resolve", 60, Duration.ofMinutes(1));

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
     * Resolves an invite reference for its landing page. Any stored invite resolves, whatever its
     * team's status, so the page can also name the sharer of a full or ended team.
     *
     * <p>Not transactional: each read is one indexed lookup, and the result is display text that
     * needs no consistent snapshot. The rate limit is checked first, so a rejected request costs no
     * database read.
     *
     * @param ref the reference from the link
     * @param clientAddress the caller's network address, counted by the rate limit
     * @throws BizException {@code RATE_LIMITED} when the address is over its limit; {@code
     *     INVITE_NOT_FOUND} when the reference is malformed, unknown or deleted, or its team no
     *     longer exists
     */
    public ResolvedInviteView resolve(String ref, String clientAddress) {
        if (!rateLimits.tryAcquire(RESOLVE_LIMIT, clientAddress)) {
            throw new BizException(CommonErrorCode.RATE_LIMITED);
        }
        if (!ShareInvite.isWellFormedRef(ref)) {
            throw new BizException(GroupbuyErrorCode.INVITE_NOT_FOUND);
        }
        ShareInvite invite =
                invites.findByRef(ref)
                        .orElseThrow(() -> new BizException(GroupbuyErrorCode.INVITE_NOT_FOUND));
        GroupDeal deal =
                deals.findById(invite.dealId())
                        .orElseThrow(() -> new BizException(GroupbuyErrorCode.INVITE_NOT_FOUND));
        String nickname = users.publicNickname(invite.sharerUserId()).orElse(null);
        return new ResolvedInviteView(deal.id(), deal.productId(), nickname, deal.expireTime());
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
