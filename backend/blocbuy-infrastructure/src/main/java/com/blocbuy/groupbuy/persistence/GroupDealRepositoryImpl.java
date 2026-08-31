package com.blocbuy.groupbuy.persistence;

import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.repository.GroupDealRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Repository implementation for GroupDeal. Placeholder until the GroupDealMapper + DO land with the
 * first groupbuy migration. Batch reads via {@code findByIds}; queries inside loops are forbidden.
 */
@Repository
public class GroupDealRepositoryImpl implements GroupDealRepository {

    @Override
    public Optional<GroupDeal> findById(String id) {
        // TODO(TEAM8-groupbuy): select via GroupDealMapper once the table exists
        return Optional.empty();
    }
}
