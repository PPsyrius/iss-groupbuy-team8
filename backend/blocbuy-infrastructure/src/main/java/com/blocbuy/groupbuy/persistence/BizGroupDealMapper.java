package com.blocbuy.groupbuy.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for biz_group_deal. All queries filter {@code deleted = false}; updates carry an
 * explicit WHERE and report affected rows. Parameters use {@code #{}} binding only.
 */
@Mapper
public interface BizGroupDealMapper {

    /** Active deal by primary key. */
    BizGroupDealDO selectActiveById(@Param("id") UUID id);

    /**
     * All open, unexpired deals for the given products in one batch. Callers must guard the empty
     * collection before invoking.
     */
    List<BizGroupDealDO> selectOpenByProductIds(@Param("productIds") Collection<UUID> productIds);
}
