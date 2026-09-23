package com.blocbuy.order.service;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.order.query.OrderSummaryView;
import java.util.UUID;

/**
 * Use-case service for Order — transaction boundary sits on public command methods
 * ({@code @Transactional}, queries {@code readOnly = true}). Orchestrates aggregates, domain
 * services, repository ports, and gateways; carries no rules that belong to a single aggregate. The
 * order-creating command lands with the paid-participation transaction (9/22, groupbuy seam); this
 * sprint publishes the buyer read side.
 */
public interface OrderAppService {

    /**
     * Buyer My Orders page: one user's orders, newest first, with the frozen product snapshot.
     * Owner-scoped by the authenticated user id — callers never supply a buyer id from the wire.
     */
    PageResult<OrderSummaryView> pageMyOrders(PageQuery page, UUID userId);
}
