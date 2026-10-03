package com.blocbuy.groupbuy.service;

import com.blocbuy.groupbuy.gateway.OpenDealOfferCache;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.query.OpenDealOffer;
import com.blocbuy.groupbuy.repository.GroupDealRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serves cached display offers, loading misses in one bounded source batch. */
@Service
@RequiredArgsConstructor
public class GroupDealQueryServiceImpl implements GroupDealQueryService {

    private final GroupDealRepository groupDealRepository;
    private final OpenDealOfferCache cache;

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
    public long countProductReferences(UUID productId) {
        return groupDealRepository.countProductReferences(productId);
    }

    @Override
    public Map<UUID, OpenDealOffer> findBestOpenDealsByProductIds(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        var ids = productIds.stream().distinct().toList();
        var lookup = cache.findByProductIds(ids);
        Map<UUID, OpenDealOffer> bestByProduct = new HashMap<>();
        lookup.hits()
                .forEach((id, offer) -> offer.ifPresent(value -> bestByProduct.put(id, value)));
        var missing = ids.stream().filter(id -> !lookup.hits().containsKey(id)).toList();
        if (missing.isEmpty()) return bestByProduct;
        var deals = groupDealRepository.findBestOpenDealsByProductIds(missing);
        for (GroupDeal deal : deals) {
            OpenDealOffer offer = toOffer(deal);
            bestByProduct.put(deal.productId(), offer);
        }
        if (lookup.available()) {
            Map<UUID, Optional<OpenDealOffer>> populated = new HashMap<>();
            missing.forEach(id -> populated.put(id, Optional.ofNullable(bestByProduct.get(id))));
            cache.putAll(populated);
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
