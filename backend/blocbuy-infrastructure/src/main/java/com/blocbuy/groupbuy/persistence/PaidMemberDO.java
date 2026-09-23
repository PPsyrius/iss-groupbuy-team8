package com.blocbuy.groupbuy.persistence;

import java.util.UUID;
import lombok.Data;

/** Infrastructure-only projection of one member row, keyed by the payment that created it. */
@Data
public class PaidMemberDO {
    private UUID dealId;
    private UUID userId;
    private UUID orderId;
    private String status;
}
