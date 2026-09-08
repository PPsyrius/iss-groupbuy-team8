package com.blocbuy.groupbuy.repository;

import com.blocbuy.groupbuy.model.GroupDeal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for the GroupDeal aggregate (one repository per aggregate root). Implemented in
 * the infrastructure layer; DOs and MyBatis mappers never cross this boundary.
 */
public interface GroupDealRepository {

    /** Active (non-deleted) deal by primary key. */
    Optional<GroupDeal> findById(UUID id);

    /**
     * All open, unexpired deals for the given products, in one batch query (never per product — N+1
     * is forbidden). Empty input returns an empty list.
     */
    List<GroupDeal> findOpenDealsByProductIds(Collection<UUID> productIds);
}
