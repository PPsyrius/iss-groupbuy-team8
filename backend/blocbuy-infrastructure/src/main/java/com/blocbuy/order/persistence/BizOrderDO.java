package com.blocbuy.order.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * Persistence object for biz_order — table shape only, no business rules. Never crosses the
 * infrastructure boundary; the converter maps it to the {@code Order} aggregate. Receiver and
 * fulfilment columns ({@code receiver_*}, {@code shipping_address}, {@code finish_time}) land with
 * their checkout/fulfilment use cases and stay null for now.
 */
@Data
public class BizOrderDO {

    private UUID id;
    private String orderNo;
    private UUID paymentId;
    private UUID userId;
    private UUID dealId;
    private UUID productId;
    private String productTitle;
    private String productImage;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    private String receiverName;
    private String receiverPhone;
    private String shippingAddress;
    private Instant paidTime;
    private Instant cancelTime;
    private Instant finishTime;
    private Instant createTime;
    private UUID createBy;
    private Instant updateTime;
    private UUID updateBy;
    private Boolean deleted;
    private Integer version;
}
