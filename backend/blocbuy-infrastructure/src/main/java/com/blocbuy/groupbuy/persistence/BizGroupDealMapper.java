package com.blocbuy.groupbuy.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for biz_group_deal. Browse queries filter {@code deleted = false}; retention
 * checks deliberately include deleted history. Parameters use {@code #{}} binding only.
 */
@Mapper
public interface BizGroupDealMapper {

    /** Retention exception to active-only reads: include all historical references. */
    boolean existsProductReference(@Param("productId") UUID productId);

    /** Active deal by primary key. */
    BizGroupDealDO selectActiveById(@Param("id") UUID id);

    /**
     * All open, unexpired deals for the given products in one batch. Callers must guard the empty
     * collection before invoking.
     */
    List<BizGroupDealDO> selectOpenByProductIds(@Param("productIds") Collection<UUID> productIds);

    /**
     * Owner-scoped My Teams page: the buyer's active memberships joined to their active deals,
     * newest participation first, member id as the unique tie-breaker.
     */
    List<TeamMembershipDO> selectMembershipPageByUserId(
            @Param("userId") UUID userId, @Param("limit") int limit, @Param("offset") long offset);

    /** Total rows matching the My Teams owner filter; same predicates as the page query. */
    long countMembershipsByUserId(@Param("userId") UUID userId);
}
