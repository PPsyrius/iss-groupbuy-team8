package com.blocbuy.catalog.persistence;

import java.util.Collection;
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

    /** Transaction-scoped lock shared by all listing creators; callers must hold a transaction. */
    int lockProductNumberAllocation();

    /** Allocate the first unused sequence candidate, checking both live and deleted history. */
    String nextProductNumber();

    /** Insert returns zero only for a duplicate live business product number. */
    int insertDraft(@Param("row") BizProductDO row, @Param("actorId") UUID actorId);

    /** Compare-and-set revision; excludes soft-deleted rows. */
    int updateListing(
            @Param("row") BizProductDO row,
            @Param("expectedVersion") int expectedVersion,
            @Param("actorId") UUID actorId);

    /** Compare-and-set of the two team terms columns; excludes soft-deleted rows. */
    int updateTeamTerms(
            @Param("row") BizProductDO row,
            @Param("expectedVersion") int expectedVersion,
            @Param("actorId") UUID actorId);

    /** Append-only audit row for one team terms write. */
    int insertTeamTermsLog(@Param("row") LogProductTeamTermsDO row);

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

    /** Aggregate all administrator status totals without loading listing rows. */
    BizListingCountsDO countListingStatuses();

    BizProductDO selectActiveByIdForUpdate(@Param("id") UUID id);

    /** Active product by primary key, any status. */
    BizProductDO selectActiveById(@Param("id") UUID id);

    /**
     * Display columns of active products by primary key, any status. Callers must guard the empty
     * collection.
     */
    List<BizProductDisplayDO> selectDisplaysByIds(@Param("ids") Collection<UUID> ids);

    /** Team terms only; the listing snapshot is not needed to decide whether a team can start. */
    BizProductTeamTermsDO selectTeamTerms(@Param("id") UUID id);

    /**
     * Conditional stock take: one statement, guarded on {@code stock > 0} and the on-sale status,
     * so concurrent settlements can never oversell. Never paired with a preceding read.
     */
    int consumeOneUnit(@Param("id") UUID id);

    /** One page of on-sale products, newest first; {@code category} null means no filter. */
    List<BizProductDO> selectOnSalePage(
            @Param("category") String category,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /** Total on-sale rows matching {@code category} (null = all). */
    long countOnSale(@Param("category") String category);

    /**
     * One page of on-sale products matching keyword patterns plus category, newest first. {@code
     * patterns} null/empty means no keyword filter; each pattern is an escaped {@code LIKE} pattern
     * bound with {@code #{}} (never interpolated).
     */
    List<BizProductDO> selectVisibleSearchPage(
            @Param("category") String category,
            @Param("patterns") List<String> patterns,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /** Total on-sale rows matching the same keyword/category filter as the search page. */
    long countVisibleSearch(
            @Param("category") String category, @Param("patterns") List<String> patterns);
}
