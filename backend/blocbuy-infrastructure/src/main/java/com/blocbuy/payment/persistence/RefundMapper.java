package com.blocbuy.payment.persistence;

import com.blocbuy.payment.model.RefundWork;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundMapper {
    RefundWork claimNext();

    RefundWork claimReview(@Param("paymentId") UUID paymentId);

    Instant now();

    int finish(
            @Param("work") RefundWork work,
            @Param("providerId") String providerId,
            @Param("status") String status,
            @Param("result") String result);

    int audit(@Param("work") RefundWork work, @Param("result") String result);

    String status(@Param("paymentId") UUID paymentId);
}
