package com.blocbuy.groupbuy.persistence;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for biz_share_credit. Insert-only: credits are immutable. */
@Mapper
public interface BizShareCreditMapper {

    /** Inserts unless the participation already has a credit; returns one or zero. */
    int insertIfAbsent(
            @Param("dealId") UUID dealId,
            @Param("paymentId") UUID paymentId,
            @Param("sharerUserId") UUID sharerUserId,
            @Param("inviteeUserId") UUID inviteeUserId,
            @Param("creditTime") Instant creditTime);
}
