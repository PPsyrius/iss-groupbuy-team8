package com.blocbuy.payment.service;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.payment.model.PaymentAttention;
import com.blocbuy.payment.repository.PaymentAttentionQuery;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only visibility independent of the Stripe feature switch and refund execution. */
@Service
@RequiredArgsConstructor
public class AdminPaymentAppService {
    private final PaymentAttentionQuery attention;
    private final UserAppService users;

    /** Count, page and enrichment share a snapshot even while refunds finish concurrently. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<PaymentAttention> pageAttention(PageQuery page, UUID actorId) {
        users.requireActive(actorId);
        return attention.page(page);
    }
}
