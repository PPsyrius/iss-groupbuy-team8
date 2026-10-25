package com.blocbuy.order.repository;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.order.model.Order;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for the Order aggregate. Implemented in the infrastructure layer; DOs and MyBatis
 * mappers never cross this boundary. No provider SDK types in signatures; monetary amounts are
 * BigDecimal major units.
 *
 * <p>Concurrency contract: one order per payment is enforced by {@code uq_biz_order_payment_id} and
 * one active order per user per deal by {@code uq_biz_order_deal_user} — replayed confirmations
 * collide at the database (US19). State writes use the {@code version} optimistic lock (US20);
 * deal-driven sweeps are status-guarded and idempotent under event replay.
 */
public interface OrderRepository {

    /** Inserts a new order; a duplicate payment/deal-user/order-no key surfaces as a failure. */
    Order save(Order order);

    /**
     * Compare-and-swap update on {@code version}.
     *
     * @throws com.blocbuy.common.exception.BizException {@code ORDER_VERSION_CONFLICT} when the row
     *     exists with a different version (stale write, no mutation), {@code ORDER_NOT_FOUND} when
     *     the row is gone
     */
    Order saveIfVersion(Order order);

    /** Active order by primary key. */
    Optional<Order> findById(UUID id);

    /** Active order owning the given payment; the paid-participation transaction backfills it. */
    Optional<Order> findByPaymentId(UUID paymentId);

    /** Owner-scoped My Orders page, newest first. */
    PageResult<Order> pageByUserId(PageQuery query, UUID userId);

    /**
     * Confirms every pending order of a deal (TeamSucceeded subscriber). Status-guarded: a replay
     * after all orders are confirmed affects zero rows.
     *
     * @return affected row count
     */
    int confirmByDealId(UUID dealId);

    /**
     * Cancels every pending order of a deal (TeamExpired sweeper), stamping the cancel instant.
     * Status-guarded: a replay after all orders are cancelled affects zero rows.
     *
     * @return affected row count
     */
    int cancelByDealId(UUID dealId, Instant canceledAt);
}
