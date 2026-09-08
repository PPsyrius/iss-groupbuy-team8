package com.blocbuy.catalog.persistence;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for biz_product. All queries filter {@code deleted = false}; updates carry an
 * explicit WHERE and report affected rows. Parameters use {@code #{}} binding only.
 */
@Mapper
public interface BizProductMapper {

    /** Active product by primary key, any status. */
    BizProductDO selectActiveById(@Param("id") UUID id);

    /** One page of on-sale products, newest first; {@code category} null means no filter. */
    List<BizProductDO> selectOnSalePage(
            @Param("category") String category,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /** Total on-sale rows matching {@code category} (null = all). */
    long countOnSale(@Param("category") String category);
}
