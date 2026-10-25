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
 * Read port for the GroupDeal aggregate. Paid-path writes use {@link PaidGroupDealRepository}
 * under its transaction and lock-order contract. Implemented in the infrastructure layer; DOs and
 * MyBatis mappers never cross this boundary.
 */
public interface GroupDealRepository {

    /** Non-deleted open deals with seats remaining and expiry strictly after database time. */
    long countOpenDeals();

    /** Any historical product reference, including closed and soft-deleted deals. */
    boolean hasProductReferences(UUID productId);

    /** All product references, including closed and soft-deleted deal history. */
    long countProductReferences(UUID productId);

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
