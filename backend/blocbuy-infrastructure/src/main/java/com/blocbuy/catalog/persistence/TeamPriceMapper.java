package com.blocbuy.catalog.persistence;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Bounded policy and owner-filtered quote access; all write row counts are checked. */
@Mapper
public interface TeamPriceMapper {
    TeamPricePolicyDO selectLatestPolicy(@Param("productId") UUID productId);

    int insertPolicy(
            @Param("row") TeamPricePolicyDO row,
            @Param("actorId") UUID actorId,
            @Param("now") Instant now);

    int insertQuote(@Param("row") TeamPriceQuoteDO row);

    TeamPriceQuoteDO selectQuoteForOwner(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    Instant selectCurrentTime();
}
