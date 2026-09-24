package com.blocbuy.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.order.model.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DO ↔ aggregate mapping: snapshot fidelity both ways, defensive null handling on reads. */
class OrderConverterTest {

    private static final Instant PAID_AT = Instant.parse("2026-09-20T10:00:00Z");

    @Test
    void nullRowMapsToNullDomain() {
        assertThat(OrderConverter.toDomain(null)).isNull();
    }

    @Test
    void toRowAndBackPreservesEveryAggregateField() {
        Order order = Order.create(terms("12.00"), PAID_AT).cancel(PAID_AT.plusSeconds(60));

        Order restored = OrderConverter.toDomain(OrderConverter.toRow(order));

        assertThat(restored.id()).isEqualTo(order.id());
        assertThat(restored.orderNo()).isEqualTo(order.orderNo());
        assertThat(restored.paymentId()).isEqualTo(order.paymentId());
        assertThat(restored.dealId()).isEqualTo(order.dealId());
        assertThat(restored.userId()).isEqualTo(order.userId());
        assertThat(restored.productId()).isEqualTo(order.productId());
        assertThat(restored.productTitle()).isEqualTo(order.productTitle());
        assertThat(restored.productImage()).isEqualTo(order.productImage());
        assertThat(restored.quantity()).isEqualTo(order.quantity());
        assertThat(restored.unitPrice()).isEqualByComparingTo(order.unitPrice());
        assertThat(restored.totalAmount()).isEqualByComparingTo(order.totalAmount());
        assertThat(restored.currency()).isEqualTo(order.currency());
        assertThat(restored.status()).isEqualTo(order.status());
        assertThat(restored.paidTime()).isEqualTo(order.paidTime());
        assertThat(restored.cancelTime()).isEqualTo(order.cancelTime());
        assertThat(restored.createTime()).isEqualTo(order.createTime());
        assertThat(restored.version()).isEqualTo(order.version());
    }

    /** version is NOT NULL but the read path still fails soft to 0 like the catalog converter. */
    @Test
    void defensiveDefaultCoversNullVersion() {
        BizOrderDO row = OrderConverter.toRow(Order.create(terms("12.00"), PAID_AT));
        row.setVersion(null);

        Order restored = OrderConverter.toDomain(row);

        assertThat(restored.version()).isZero();
    }

    private static Order.NewOrderTerms terms(String unitPrice) {
        return new Order.NewOrderTerms(
                "ORDER-" + UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Mechanical keyboard",
                "https://img.example/keyboard.png",
                1,
                new BigDecimal(unitPrice),
                "sgd");
    }
}
