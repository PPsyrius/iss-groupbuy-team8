package com.blocbuy.payment.persistence;

import com.blocbuy.payment.model.PaymentReviewCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL bindings are in mapper/payment/PaymentMapper.xml. */
@Mapper
public interface PaymentMapper {
    BizPaymentDO find(@Param("id") UUID id);

    BizPaymentDO receipt(@Param("id") UUID id);

    BizPaymentDO lock(@Param("id") UUID id);

    BizPaymentDO request(@Param("userId") UUID userId, @Param("key") String key);

    BizPaymentDO blocking(@Param("intentId") UUID intentId);

    int insert(BizPaymentDO row);

    int save(BizPaymentDO row);

    Instant now();

    int claimEvent(@Param("eventId") String eventId, @Param("type") String type);

    int finishEvent(
            @Param("eventId") String eventId,
            @Param("paymentId") UUID paymentId,
            @Param("result") String result);

    int review(
            @Param("id") UUID id,
            @Param("reason") String reason,
            @Param("operator") String operator);

    int openCase(@Param("id") UUID id, @Param("reason") String reason);

    List<PaymentReviewCase> reviewCases(@Param("after") UUID after, @Param("limit") int limit);

    int assignCase(
            @Param("id") UUID id,
            @Param("operator") String operator,
            @Param("nextCheck") Instant nextCheck);

    int recordRecovery(
            @Param("id") UUID id,
            @Param("operator") String operator,
            @Param("summary") String summary,
            @Param("resolved") boolean resolved);

    int outcome(
            @Param("id") UUID id,
            @Param("dealId") UUID dealId,
            @Param("orderId") UUID orderId,
            @Param("outcome") String outcome);

    String getOutcome(@Param("id") UUID id);

    int refund(
            @Param("id") UUID id, @Param("reason") String reason, @Param("eventId") String eventId);

    boolean refundRecorded(@Param("id") UUID id);

    List<UUID> lockRefundableMemberIds(@Param("dealId") UUID dealId);

    int refundExpiredTeam(
            @Param("dealId") UUID dealId,
            @Param("reason") String reason,
            @Param("eventId") String eventId);

    List<BizPaymentDO> pending(@Param("threshold") Instant threshold, @Param("limit") int limit);
}
