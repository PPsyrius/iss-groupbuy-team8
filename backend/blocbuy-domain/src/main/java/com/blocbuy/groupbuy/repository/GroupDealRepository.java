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
     * At most one open, unexpired, non-deleted deal per requested product, selected in one batch
     * query by lowest deal price and then ascending deal ID. Null or empty input returns an empty
     * list; products without an eligible deal are absent.
     */
    List<GroupDeal> findBestOpenDealsByProductIds(Collection<UUID> productIds);

    /**
     * Owner-scoped My Teams page: every active team the buyer leads or joined, newest participation
     * first with a unique tie-breaker. Two bounded queries (page + count), independent of page
     * size. They agree only when the caller runs them in one snapshot ({@code REPEATABLE READ});
     * under {@code READ COMMITTED} each statement sees its own.
     */
    PageResult<TeamMembership> pageMembershipsByUserId(PageQuery query, UUID userId);
}
