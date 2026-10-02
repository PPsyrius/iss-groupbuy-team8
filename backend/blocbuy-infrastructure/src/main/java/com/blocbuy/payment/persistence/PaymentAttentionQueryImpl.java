package com.blocbuy.payment.persistence;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.payment.model.PaymentAttention;
import com.blocbuy.payment.repository.PaymentAttentionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Two bounded-result reads in the caller's repeatable-read snapshot; no per-row enrichment. */
@Repository
@RequiredArgsConstructor
public class PaymentAttentionQueryImpl implements PaymentAttentionQuery {
    private final PaymentAttentionMapper mapper;

    @Override
    public PageResult<PaymentAttention> page(PageQuery page) {
        long total = mapper.countAttention();
        return PageResult.of(mapper.pageAttention(page.size(), page.offset()), total, page);
    }
}
