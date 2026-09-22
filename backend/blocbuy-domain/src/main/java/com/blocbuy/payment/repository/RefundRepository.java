package com.blocbuy.payment.repository;

import com.blocbuy.payment.model.RefundWork;
import java.util.UUID;

public interface RefundRepository {
    RefundWork claimNext();

    RefundWork claimReview(UUID paymentId);

    void finish(RefundWork work, String providerId, String status, String result);

    String status(UUID paymentId);
}
