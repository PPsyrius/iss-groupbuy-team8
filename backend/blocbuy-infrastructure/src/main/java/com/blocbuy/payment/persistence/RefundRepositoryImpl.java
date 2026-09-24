package com.blocbuy.payment.persistence;

import com.blocbuy.payment.model.RefundWork;
import com.blocbuy.payment.repository.RefundRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefundRepositoryImpl implements RefundRepository {
    private final RefundMapper mapper;

    @Override
    public RefundWork claimReview(UUID paymentId) {
        RefundWork work = mapper.claimReview(paymentId);
        if (work != null) mapper.audit(work, "OPERATOR_RECONCILE");
        return work;
    }

    @Override
    public RefundWork claimNext() {
        RefundWork work = mapper.claimNext();
        if (work != null) mapper.audit(work, "CLAIMED");
        return work;
    }

    @Override
    public Instant now() {
        return mapper.now();
    }

    @Override
    public void finish(RefundWork work, String providerId, String status, String result) {
        if (mapper.finish(work, providerId, status, result) == 1) mapper.audit(work, result);
    }

    @Override
    public String status(UUID paymentId) {
        return mapper.status(paymentId);
    }
}
