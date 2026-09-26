package com.blocbuy.groupbuy.persistence;

import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.MemberRole;
import com.blocbuy.groupbuy.model.TeamMembership;

/**
 * Hand-written BizGroupDealDO ↔ GroupDeal converter (no MapStruct — locked decision). Enum↔varchar
 * mapping lives here so the aggregate never sees storage codes.
 */
final class GroupDealConverter {

    private GroupDealConverter() {}

    static GroupDeal toDomain(BizGroupDealDO data) {
        if (data == null) {
            return null;
        }
        return GroupDeal.reconstitute(
                data.getId(),
                data.getDealNo(),
                data.getProductId(),
                data.getLeaderUserId(),
                data.getDealPrice(),
                data.getCurrency(),
                data.getTargetSize() == null ? 0 : data.getTargetSize(),
                data.getCurrentSize() == null ? 0 : data.getCurrentSize(),
                DealStatus.fromCode(data.getStatus()),
                data.getStartTime(),
                data.getExpireTime(),
                data.getSuccessTime());
    }

    /** Unknown status or role codes fail fast, like the aggregate mapping above. */
    static TeamMembership toMembership(TeamMembershipDO data) {
        return new TeamMembership(
                data.getDealId(),
                data.getProductId(),
                data.getDealPrice(),
                data.getCurrency(),
                data.getTargetSize() == null ? 0 : data.getTargetSize(),
                data.getCurrentSize() == null ? 0 : data.getCurrentSize(),
                DealStatus.fromCode(data.getStatus()),
                data.getExpireTime(),
                data.getSuccessTime(),
                MemberRole.fromCode(data.getMemberRole()),
                data.getJoinTime(),
                data.getOrderId());
    }
}
