package com.blocbuy.groupbuy.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * Row of the My Teams join (biz_group_member ⋈ biz_group_deal) — query shape only, no business
 * rules. Never crosses the infrastructure boundary; the converter maps it to {@code
 * TeamMembership}.
 */
@Data
public class TeamMembershipDO {

    private UUID memberId;
    private UUID dealId;
    private UUID productId;
    private BigDecimal dealPrice;
    private String currency;
    private Integer targetSize;
    private Integer currentSize;
    private String status;
    private Instant expireTime;
    private Instant successTime;
    private String memberRole;
    private Instant joinTime;
    private UUID orderId;
}
