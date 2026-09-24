package com.blocbuy.order.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for biz_order. All queries filter {@code deleted = false}; updates carry an
 * explicit WHERE and report affected rows. Parameters use {@code #{}} binding only.
 */
@Mapper
public interface BizOrderMapper {

    /**
     * Plain insert: a duplicate order number, payment or deal-user key surfaces as a database
     * failure the caller maps to its dedup semantics (US19).
     */
    int insert(@Param("row") BizOrderDO row);

    /** Compare-and-swap on {@code version}; excludes soft-deleted rows. */
    int updateIfVersion(
            @Param("row") BizOrderDO row, @Param("expectedVersion") int expectedVersion);

    BizOrderDO selectById(@Param("id") UUID id);

    BizOrderDO selectByPaymentId(@Param("paymentId") UUID paymentId);

    /** Owner-scoped My Orders page, newest first. */
    List<BizOrderDO> selectPageByUserId(
            @Param("userId") UUID userId, @Param("limit") int limit, @Param("offset") long offset);

    /** Total rows matching the My Orders owner filter. */
    long countByUserId(@Param("userId") UUID userId);

    /** Status-guarded bulk confirm for one deal's pending orders; idempotent under replay. */
    int confirmPendingByDealId(@Param("dealId") UUID dealId);

    /** Status-guarded bulk cancel for one deal's pending orders; idempotent under replay. */
    int cancelPendingByDealId(
            @Param("dealId") UUID dealId, @Param("canceledAt") Instant canceledAt);
}
