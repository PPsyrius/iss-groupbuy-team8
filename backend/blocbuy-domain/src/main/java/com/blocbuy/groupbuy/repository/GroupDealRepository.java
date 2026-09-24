package com.blocbuy.groupbuy.repository;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.TeamMembership;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for the GroupDeal aggregate (one repository per aggregate root). Implemented in
 * the infrastructure layer; DOs and MyBatis mappers never cross this boundary.
 */
public interface GroupDealRepository {

    /** Any historical product reference, including closed and soft-deleted deals. */
    boolean hasProductReferences(UUID productId);

    /** Active (non-deleted) deal by primary key. */
    Optional<GroupDeal> findById(UUID id);

    /**
     * All open, unexpired deals for the given products, in one batch query (never per product — N+1
     * is forbidden). Empty input returns an empty list.
     */
    List<GroupDeal> findOpenDealsByProductIds(Collection<UUID> productIds);

    /**
     * Owner-scoped My Teams page: every active team the buyer leads or joined, newest participation
     * first with a unique tie-breaker. Two bounded queries (page + count), independent of page
     * size.
     */
    PageResult<TeamMembership> pageMembershipsByUserId(PageQuery query, UUID userId);
}
