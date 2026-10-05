package com.blocbuy.groupbuy.persistence;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** Row mapping for biz_share_invite; never crosses the infrastructure boundary. */
@Data
public class BizShareInviteDO {
    private UUID id;
    private String inviteRef;
    private UUID dealId;
    private UUID sharerUserId;

    /** Joined team deadline for the derived invite cache; not a column on biz_share_invite. */
    private Instant dealExpireTime;
}
