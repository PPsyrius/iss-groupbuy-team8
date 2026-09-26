package com.blocbuy.groupbuy.persistence;

import java.util.UUID;
import lombok.Data;

/** Row mapping for biz_share_invite; never crosses the infrastructure boundary. */
@Data
public class BizShareInviteDO {
    private UUID id;
    private String inviteRef;
    private UUID dealId;
    private UUID sharerUserId;
}
