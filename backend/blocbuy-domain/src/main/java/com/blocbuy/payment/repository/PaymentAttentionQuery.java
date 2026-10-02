package com.blocbuy.payment.repository;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.payment.model.PaymentAttention;

/** Read-only admin projection owned by payment; see backend ADR 0006 for source ownership. */
public interface PaymentAttentionQuery {
    PageResult<PaymentAttention> page(PageQuery page);
}
