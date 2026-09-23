package com.blocbuy.order.persistence;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.order.exception.OrderErrorCode;
import com.blocbuy.order.model.Order;
import com.blocbuy.order.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Repository implementation for the Order aggregate over biz_order. Writes report affected rows and
 * carry explicit WHERE clauses; deal-driven sweeps are status-guarded so event replays are no-ops.
 * Batch reads use one page query plus one count — never a query per item.
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final BizOrderMapper orders;

    /** Returns the stored snapshot unchanged; duplicate keys surface as database failures. */
    @Override
    public Order save(Order order) {
        orders.insert(OrderConverter.toRow(order));
        return order;
    }

    /** Stale or missing rows never mutate; the caller sees 409/404 semantics via the codes. */
    @Override
    public Order saveIfVersion(Order order) {
        int rows = orders.updateIfVersion(OrderConverter.toRow(order), order.version());
        if (rows == 0) {
            if (orders.selectById(order.id()) != null) {
                throw new BizException(OrderErrorCode.ORDER_VERSION_CONFLICT);
            }
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return order.withNextVersion();
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(OrderConverter.toDomain(orders.selectById(id)));
    }

    @Override
    public Optional<Order> findByPaymentId(UUID paymentId) {
        return Optional.ofNullable(OrderConverter.toDomain(orders.selectByPaymentId(paymentId)));
    }

    @Override
    public PageResult<Order> pageByUserId(PageQuery query, UUID userId) {
        List<Order> items =
                orders.selectPageByUserId(userId, query.size(), query.offset()).stream()
                        .map(OrderConverter::toDomain)
                        .toList();
        return PageResult.of(items, orders.countByUserId(userId), query);
    }

    @Override
    public int confirmByDealId(UUID dealId) {
        return orders.confirmPendingByDealId(dealId);
    }

    @Override
    public int cancelByDealId(UUID dealId, Instant canceledAt) {
        if (canceledAt == null) {
            throw new IllegalArgumentException("canceledAt must not be null");
        }
        return orders.cancelPendingByDealId(dealId, canceledAt);
    }
}
