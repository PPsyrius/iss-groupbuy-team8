package com.blocbuy.groupbuy.service;

import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.repository.GroupDealRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link GroupDealQueryService}. One batch repository call, then in-memory pick of the
 * lowest-priced open deal per product (ties keep the first row seen).
 */
@Service
@RequiredArgsConstructor
public class GroupDealQueryServiceImpl implements GroupDealQueryService {

    private final GroupDealRepository groupDealRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, OpenDealOffer> findBestOpenDealsByProductIds(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        var deals = groupDealRepository.findOpenDealsByProductIds(productIds);
        Map<UUID, OpenDealOffer> bestByProduct = new HashMap<>();
        for (GroupDeal deal : deals) {
            bestByProduct.merge(
                    deal.productId(),
                    toOffer(deal),
                    (current, candidate) ->
                            candidate.dealPrice().compareTo(current.dealPrice()) < 0
                                    ? candidate
                                    : current);
        }
        return bestByProduct;
    }

    private static OpenDealOffer toOffer(GroupDeal deal) {
        return new OpenDealOffer(
                deal.id(),
                deal.productId(),
                deal.dealPrice(),
                deal.currency(),
                deal.targetSize(),
                deal.currentSize(),
                deal.expireTime());
    }
}
