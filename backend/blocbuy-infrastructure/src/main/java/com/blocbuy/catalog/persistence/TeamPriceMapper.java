package com.blocbuy.catalog.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Bounded policy and owner-filtered quote access; all write row counts are checked. */
@Mapper
public interface TeamPriceMapper {
    /** Existence across both immutable history tables; not limited to active/valid rows. */
    boolean existsProductReference(@Param("productId") UUID productId);

    TeamPricePolicyDO selectLatestPolicy(@Param("productId") UUID productId);

    /** One latest revision per product; caller guards empty input. */
    List<TeamPricePolicyDO> selectLatestPolicies(@Param("productIds") Collection<UUID> productIds);

    /** Latest revision only: no fallback to an older still-valid policy. */
    long countListingsWithActivePolicy();

    int insertPolicy(
            @Param("row") TeamPricePolicyDO row,
            @Param("actorId") UUID actorId,
            @Param("now") Instant now);

    int insertQuote(@Param("row") TeamPriceQuoteDO row);

    TeamPriceQuoteDO selectQuoteForOwner(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    Instant selectCurrentTime();
}
