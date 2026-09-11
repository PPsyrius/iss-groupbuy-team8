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

    /** Insert returns zero only for a duplicate live business product number. */
    int insertDraft(@Param("row") BizProductDO row, @Param("actorId") UUID actorId);

    /** Compare-and-set revision; excludes soft-deleted rows. */
    int updateListing(
            @Param("row") BizProductDO row,
            @Param("expectedVersion") int expectedVersion,
            @Param("actorId") UUID actorId);

    /** Logical deletion with defensive draft and version predicates. */
    int deleteDraft(
            @Param("id") UUID id,
            @Param("expectedVersion") int expectedVersion,
            @Param("actorId") UUID actorId);

    /** Administrator page, including unpublished listings; stable newest-first order. */
    List<BizProductDO> selectListingPage(
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /** Same filter as the administrator page. */
    long countListings(@Param("status") String status);

    BizProductDO selectActiveByIdForUpdate(@Param("id") UUID id);

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
