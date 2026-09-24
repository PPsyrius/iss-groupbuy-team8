package com.blocbuy.order.persistence;

import com.blocbuy.order.model.Order;
import com.blocbuy.order.model.OrderStatus;

/**
 * Hand-written BizOrderDO ↔ Order converter (no MapStruct — locked decision). Enum↔varchar mapping
 * lives here so the aggregate never sees storage codes. The reconstitute path revalidates the total
 * invariant, so a corrupted row fails fast instead of silently loading.
 */
final class OrderConverter {

    private OrderConverter() {}

    static Order toDomain(BizOrderDO row) {
        if (row == null) {
            return null;
        }
        return Order.reconstitute(
                new Order.NewOrderTerms(
                        row.getOrderNo(),
                        row.getPaymentId(),
                        row.getDealId(),
                        row.getUserId(),
                        row.getProductId(),
                        row.getProductTitle(),
                        row.getProductImage(),
                        row.getQuantity(),
                        row.getUnitPrice(),
                        row.getCurrency()),
                row.getId(),
                row.getTotalAmount(),
                new Order.Lifecycle(
                        OrderStatus.fromCode(row.getStatus()),
                        row.getPaidTime(),
                        row.getCancelTime(),
                        row.getCreateTime(),
                        row.getVersion() == null ? 0 : row.getVersion()));
    }

    /**
     * Maps editable aggregate state only; audit timestamps/actors remain infrastructure-managed.
     */
    static BizOrderDO toRow(Order order) {
        BizOrderDO row = new BizOrderDO();
        row.setId(order.id());
        row.setOrderNo(order.orderNo());
        row.setPaymentId(order.paymentId());
        row.setUserId(order.userId());
        row.setDealId(order.dealId());
        row.setProductId(order.productId());
        row.setProductTitle(order.productTitle());
        row.setProductImage(order.productImage());
        row.setQuantity(order.quantity());
        row.setUnitPrice(order.unitPrice());
        row.setTotalAmount(order.totalAmount());
        row.setCurrency(order.currency());
        row.setStatus(order.status().code());
        row.setPaidTime(order.paidTime());
        row.setCancelTime(order.cancelTime());
        row.setCreateTime(order.createTime());
        row.setVersion(order.version());
        return row;
    }
}
