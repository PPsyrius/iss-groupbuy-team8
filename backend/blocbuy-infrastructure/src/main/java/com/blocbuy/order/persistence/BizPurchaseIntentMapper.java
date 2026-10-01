package com.blocbuy.order.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for biz_purchase_intent. All queries filter {@code deleted = false}; updates carry
 * an explicit WHERE and report affected rows. Parameters use {@code #{}} binding only.
 */
@Mapper
public interface BizPurchaseIntentMapper {

    /** {@code clock_timestamp()}, deliberately not {@code now()}: statement time, not tx start. */
    Instant currentTime();

    int insert(@Param("row") BizPurchaseIntentDO row);

    BizPurchaseIntentDO selectById(@Param("id") UUID id);

    /**
     * {@code SELECT ... FOR UPDATE}. Callers must already hold the group deal lock; see {@code
     * PurchaseIntentRepository} for the fixed lock order.
     */
    BizPurchaseIntentDO lockById(@Param("id") UUID id);

    BizPurchaseIntentDO tryLockForExpiry(@Param("id") UUID id);

    List<UUID> dueForExpiry(@Param("now") Instant now, @Param("limit") int limit);

    int markExpired(
            @Param("id") UUID id,
            @Param("now") Instant now,
            @Param("expectedVersion") int expectedVersion);

    int excludePaidFromExpiry(@Param("id") UUID id);

    int deferExpiryRetry(@Param("id") UUID id, @Param("retryDelaySeconds") long seconds);

    /**
     * Claims an unfulfilled intent for one payment. Guarded on {@code fulfilled_payment_id IS NULL}
     * and on {@code version}, so a concurrent claim affects zero rows instead of overwriting.
     */
    int markFulfilled(
            @Param("id") UUID id,
            @Param("paymentId") UUID paymentId,
            @Param("expectedVersion") int expectedVersion);
}
