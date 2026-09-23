package com.blocbuy.order.service;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.order.model.Order;
import com.blocbuy.order.query.OrderSummaryView;
import com.blocbuy.order.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-side orchestration only; placement/confirmation commands land with the payment seam. */
@Service
@RequiredArgsConstructor
public class OrderAppServiceImpl implements OrderAppService {

    private final OrderRepository orders;

    /** One page query plus one mapping pass; aggregates never leave this layer. */
    @Override
    @Transactional(readOnly = true)
    public PageResult<OrderSummaryView> pageMyOrders(PageQuery page, UUID userId) {
        PageResult<Order> result = orders.pageByUserId(page, userId);
        List<OrderSummaryView> views =
                result.items().stream().map(OrderAppServiceImpl::toView).toList();
        return PageResult.of(views, result.total(), page);
    }

    /** Ownership is a filter, not a separate failure: a foreign order simply is not found. */
    @Override
    @Transactional(readOnly = true)
    public Optional<OrderSummaryView> findMine(UUID orderId, UUID userId) {
        return orders.findById(orderId)
                .filter(order -> order.userId().equals(userId))
                .map(OrderAppServiceImpl::toView);
    }

    private static OrderSummaryView toView(Order order) {
        return new OrderSummaryView(
                order.id(),
                order.orderNo(),
                order.dealId(),
                order.productId(),
                order.productTitle(),
                order.productImage(),
                order.quantity(),
                order.unitPrice(),
                order.totalAmount(),
                order.currency(),
                order.status().code(),
                order.paidTime(),
                order.cancelTime(),
                order.createTime());
    }
}
