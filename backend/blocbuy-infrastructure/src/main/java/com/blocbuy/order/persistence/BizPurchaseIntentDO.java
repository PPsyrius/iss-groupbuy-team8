package com.blocbuy.order.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** Row mapping for biz_purchase_intent; never crosses the infrastructure boundary. */
@Data
public class BizPurchaseIntentDO {

    private UUID id;
    private UUID userId;
    private String operation;
    private UUID dealId;
    private UUID productId;
    private String productTitle;
    private String productImage;
    private BigDecimal amount;
    private String currency;
    private Integer targetSize;
    private Instant quoteValidUntil;
    private Instant businessDeadline;
    private UUID fulfilledPaymentId;
    private UUID sharerUserId;
    private Instant createTime;
    private UUID createBy;
    private Instant updateTime;
    private UUID updateBy;
    private Boolean deleted;
    private Integer version;
}
