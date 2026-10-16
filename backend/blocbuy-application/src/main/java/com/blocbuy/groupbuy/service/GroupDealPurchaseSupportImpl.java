package com.blocbuy.groupbuy.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.ShareInvite;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import com.blocbuy.groupbuy.repository.ShareInviteRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reads only groupbuy's own aggregate through its own port and hands the order module a validated
 * snapshot — no {@link GroupDeal} leaves this class.
 *
 * <p>Shared domain guards run before the buyer pays and again in {@link GroupDeal#joinPaid}, which
 * remains the authority once money has moved. A team that passes here can still be rejected there
 * if it filled up in between — that is the intended order, because rejecting a quote costs nothing
 * and rejecting a payment costs a refund.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupDealPurchaseSupportImpl implements GroupDealPurchaseSupport {

    private final PaidGroupDealRepository deals;
    private final ShareInviteRepository invites;

    @Override
    public JoinTerms lockJoinTerms(UUID dealId, UUID buyerId) {
        if (dealId == null || buyerId == null) {
            throw new BizException(GroupbuyErrorCode.INVALID_PARTICIPATION);
        }
        GroupDeal deal =
                deals.lockDeal(dealId)
                        .orElseThrow(
                                () -> new BizException(GroupbuyErrorCode.GROUPBUY_PLACEHOLDER));
        // Read the clock after the lock, so the window check cannot use a time from before a
        // concurrent writer committed.
        Instant now = deals.currentTime();
        deal.requireJoinWindow(now);
        deal.requireAvailableSeat();
        deal.requireNewMember(
                buyerId,
                buyerId.equals(deal.leaderUserId()) || deals.hasActiveMember(dealId, buyerId));
        return new JoinTerms(
                deal.id(),
                deal.productId(),
                deal.dealPrice(),
                deal.currency(),
                deal.targetSize(),
                deal.expireTime());
    }

    /** Outcomes are logged at DEBUG by kind only; the reference itself is never logged. */
    @Override
    public Optional<UUID> resolveSharer(String inviteRef, UUID dealId, UUID buyerId) {
        if (inviteRef == null || inviteRef.isBlank()) {
            return Optional.empty();
        }
        Optional<ShareInvite> invite = invites.findByRef(inviteRef);
        if (invite.isEmpty()) {
            log.debug("Invite reference unknown for deal {}", dealId);
            return Optional.empty();
        }
        if (!invite.get().attributes(dealId, buyerId)) {
            log.debug("Invite reference for deal {} does not attribute this join", dealId);
            return Optional.empty();
        }
        return Optional.of(invite.get().sharerUserId());
    }
}
