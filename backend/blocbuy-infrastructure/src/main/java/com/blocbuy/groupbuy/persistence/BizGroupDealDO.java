package com.blocbuy.groupbuy.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * Persistence object for biz_group_deal — table shape only, no business rules. Never crosses the
 * infrastructure boundary; the converter maps it to the {@code GroupDeal} aggregate.
 */
@Data
public class BizGroupDealDO {

    private UUID id;
    private String dealNo;
    private UUID productId;
    private UUID leaderUserId;
    private BigDecimal dealPrice;
    private String currency;
    private Integer targetSize;
    private Integer currentSize;
    private String status;
    private Instant startTime;
    private Instant expireTime;
    private Instant successTime;
    private Instant createTime;
    private UUID createBy;
    private Instant updateTime;
    private UUID updateBy;
    private Boolean deleted;
    private Integer version;
}
