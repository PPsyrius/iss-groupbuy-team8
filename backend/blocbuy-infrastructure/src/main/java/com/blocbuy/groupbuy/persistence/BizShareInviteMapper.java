package com.blocbuy.groupbuy.persistence;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for biz_share_invite. Reads filter {@code deleted = false}; binding only. */
@Mapper
public interface BizShareInviteMapper {

    /**
     * Inserts unless an active row already holds the team-and-sharer pair or the reference; returns
     * one when inserted, zero otherwise.
     */
    int insertIfAbsent(@Param("row") BizShareInviteDO row);

    BizShareInviteDO selectByRef(@Param("ref") String ref);

    BizShareInviteDO selectByDealAndSharer(
            @Param("dealId") UUID dealId, @Param("sharerUserId") UUID sharerUserId);
}
