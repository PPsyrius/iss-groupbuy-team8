package com.blocbuy.groupbuy.service;

import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Reads and writes only groupbuy's own rows; no aggregate leaves this class. */
@Service
@RequiredArgsConstructor
public class GroupDealSettlementSupportImpl implements GroupDealSettlementSupport {

    private final PaidGroupDealRepository deals;

    @Override
    public void lockTeam(UUID dealId) {
        if (dealId == null) {
            throw new IllegalArgumentException("dealId must not be null");
        }
        // The lock is the side effect; an absent row means a START whose team is not created yet.
        deals.lockDeal(dealId);
    }

    @Override
    public boolean linkOrder(UUID dealId, UUID paymentId, UUID orderId) {
        return deals.linkOrder(dealId, paymentId, orderId);
    }

    @Override
    public Optional<SettledMember> settledMember(UUID paymentId) {
        return deals.findPaidMember(paymentId)
                .map(member ->
                        new SettledMember(
                                member.dealId(),
                                member.userId(),
                                member.orderId(),
                                member.status()));
    }

    @Override
    public void lockExpiredTeam(UUID dealId) {
        GroupDeal deal =
                deals.lockDeal(dealId)
                        .orElseThrow(
                                () -> new IllegalStateException("Expired team no longer exists"));
        if (deal.status() != DealStatus.EXPIRED) {
            throw new IllegalStateException("Team is not expired");
        }
    }
}
