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

/** Maps the repository's bounded batch of best open deals into catalog-facing offers. */
@Service
@RequiredArgsConstructor
public class GroupDealQueryServiceImpl implements GroupDealQueryService {

    private final GroupDealRepository groupDealRepository;

    @Override
    @Transactional(readOnly = true)
    public long countOpenDeals() {
        return groupDealRepository.countOpenDeals();
    }

    /** Exposes only reference existence across the catalog/groupbuy boundary. */
    @Override
    @Transactional(readOnly = true)
    public boolean hasProductReferences(UUID productId) {
        return groupDealRepository.hasProductReferences(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, OpenDealOffer> findBestOpenDealsByProductIds(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        var deals = groupDealRepository.findBestOpenDealsByProductIds(productIds);
        Map<UUID, OpenDealOffer> bestByProduct = new HashMap<>();
        for (GroupDeal deal : deals) {
            bestByProduct.put(deal.productId(), toOffer(deal));
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
