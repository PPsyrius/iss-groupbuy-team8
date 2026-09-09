package com.blocbuy.groupbuy.persistence;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Bounded SQL for the receipt-first groupbuy write protocol. */
@Mapper
public interface PaidGroupDealMapper {
    int reserveReceipt(
            @Param("paymentId") UUID paymentId,
            @Param("fingerprint") String fingerprint,
            @Param("actorId") UUID actorId);

    ParticipationReceiptDO lockReceipt(@Param("paymentId") UUID paymentId);

    BizGroupDealDO lockDeal(@Param("dealId") UUID dealId);

    Instant currentTime();

    boolean hasActiveMember(@Param("dealId") UUID dealId, @Param("userId") UUID userId);

    int insertDeal(BizGroupDealDO deal);

    int insertMember(
            @Param("dealId") UUID dealId,
            @Param("paymentId") UUID paymentId,
            @Param("userId") UUID userId,
            @Param("role") String role,
            @Param("now") Instant now);

    int updateDeal(
            @Param("deal") BizGroupDealDO deal,
            @Param("expectedSize") int expectedSize,
            @Param("expectedStatus") String expectedStatus);

    int completeReceipt(
            @Param("paymentId") UUID paymentId,
            @Param("result") ParticipationReceiptDO result,
            @Param("actorId") UUID actorId,
            @Param("now") Instant now);
}
