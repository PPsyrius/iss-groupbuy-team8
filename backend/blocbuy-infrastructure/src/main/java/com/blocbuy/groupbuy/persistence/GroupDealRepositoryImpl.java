package com.blocbuy.groupbuy.persistence;

import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.repository.GroupDealRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-backed {@link GroupDealRepository}. Batch reads only — per-product deal lookups in a loop
 * are forbidden (N+1).
 */
@Repository
@RequiredArgsConstructor
public class GroupDealRepositoryImpl implements GroupDealRepository {

    private final BizGroupDealMapper bizGroupDealMapper;

    @Override
    public Optional<GroupDeal> findById(UUID id) {
        return Optional.ofNullable(
                GroupDealConverter.toDomain(bizGroupDealMapper.selectActiveById(id)));
    }

    @Override
    public List<GroupDeal> findOpenDealsByProductIds(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return bizGroupDealMapper.selectOpenByProductIds(productIds).stream()
                .map(GroupDealConverter::toDomain)
                .toList();
    }
}
