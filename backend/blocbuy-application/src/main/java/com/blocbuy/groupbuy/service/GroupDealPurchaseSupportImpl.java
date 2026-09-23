package com.blocbuy.groupbuy.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Reads only groupbuy's own aggregate through its own port and hands the order module a validated
 * snapshot — no {@link GroupDeal} leaves this class.
 *
 * <p>The admission rules deliberately repeat {@link GroupDeal#joinPaid}: this runs before the buyer
 * pays, so it fails fast and cheaply, while {@code joinPaid} remains the authority once money has
 * moved. A team that passes here can still be rejected there if it filled up in between — that is
 * the intended order, because rejecting a quote costs nothing and rejecting a payment costs a
 * refund.
 */
@Service
@RequiredArgsConstructor
public class GroupDealPurchaseSupportImpl implements GroupDealPurchaseSupport {

    private final PaidGroupDealRepository deals;

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
        if (deal.status() != DealStatus.OPEN
                || now.isBefore(deal.startTime())
                || !now.isBefore(deal.expireTime())) {
            throw new BizException(GroupbuyErrorCode.DEAL_CLOSED);
        }
        if (deal.currentSize() >= deal.targetSize()) {
            throw new BizException(GroupbuyErrorCode.DEAL_FULL);
        }
        if (buyerId.equals(deal.leaderUserId()) || deals.hasActiveMember(dealId, buyerId)) {
            throw new BizException(GroupbuyErrorCode.ALREADY_MEMBER);
        }
        return new JoinTerms(
                deal.id(),
                deal.productId(),
                deal.dealPrice(),
                deal.currency(),
                deal.targetSize(),
                deal.expireTime());
    }
}
