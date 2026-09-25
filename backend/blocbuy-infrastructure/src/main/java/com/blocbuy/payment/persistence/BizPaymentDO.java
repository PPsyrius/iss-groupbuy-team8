package com.blocbuy.payment.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** MyBatis row, kept within infrastructure. */
@Getter
@Setter
public class BizPaymentDO {
    private UUID id;
    private UUID orderId;
    private UUID fulfilledDealId;
    private String fulfilmentResult;
    private UUID intentId;
    private UUID userId;
    private String operation;
    private UUID businessId;
    private BigDecimal amount;
    private String currency;
    private Instant quoteValidUntil;
    private Instant businessDeadline;
    private String requestKey;
    private Instant createTime;
    private Instant sessionExpiresAt;
    private String successUrl;
    private String cancelUrl;
    private String checkoutSessionId;
    private String checkoutUrl;
    private String providerPaymentIntentId;
    private String status;
    private int version;
}
