package com.blocbuy.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.order.model.Order;
import com.blocbuy.order.query.OrderSummaryView;
import com.blocbuy.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** My Orders mapping: one repository page, aggregate never leaking past the view. */
@ExtendWith(MockitoExtension.class)
class OrderAppServiceImplTest {

    private static final UUID BUYER = UUID.fromString("01990000-0000-7000-8000-000000000002");

    @Mock private OrderRepository orders;
    @InjectMocks private OrderAppServiceImpl service;

    @Test
    void pageMyOrdersMapsSnapshotAndStatusWithoutPaymentOrPii() {
        Order order = Order.create(terms("12.00"), Instant.parse("2026-09-20T10:00:00Z"));
        PageQuery query = PageQuery.of(1, 20);
        when(orders.pageByUserId(query, BUYER)).thenReturn(PageResult.of(List.of(order), 1, query));

        PageResult<OrderSummaryView> result = service.pageMyOrders(query, BUYER);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        OrderSummaryView view = result.items().getFirst();
        assertThat(view.orderId()).isEqualTo(order.id());
        assertThat(view.orderNo()).isEqualTo(order.orderNo());
        assertThat(view.dealId()).isEqualTo(order.dealId());
        assertThat(view.status()).isEqualTo("pending");
        assertThat(view.totalAmount()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(view.paidTime()).isEqualTo(order.paidTime());
        assertThat(view.cancelTime()).isNull();
    }

    @Test
    void pageMyOrdersKeepsEmptyPageShape() {
        PageQuery query = PageQuery.of(3, 10);
        when(orders.pageByUserId(query, BUYER)).thenReturn(PageResult.of(List.of(), 0, query));

        PageResult<OrderSummaryView> result = service.pageMyOrders(query, BUYER);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.hasNext()).isFalse();
    }

    private static Order.NewOrderTerms terms(String unitPrice) {
        return new Order.NewOrderTerms(
                "ORDER-" + UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                BUYER,
                UUID.randomUUID(),
                "Mechanical keyboard",
                "https://img.example/keyboard.png",
                1,
                new BigDecimal(unitPrice),
                "sgd");
    }
}
