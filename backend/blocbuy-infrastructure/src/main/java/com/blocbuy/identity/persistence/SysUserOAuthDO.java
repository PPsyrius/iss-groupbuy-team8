package com.blocbuy.identity.persistence;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * Persistence object for sys_user_oauth — table shape only, no business rules. {@code rawProfile}
 * is the PII audit snapshot (jsonb, handled as a String at the edges). Never crosses the
 * infrastructure boundary.
 */
@Data
public class SysUserOAuthDO {

    private UUID id;
    private UUID userId;
    private String provider;
    private String providerUserId;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String rawProfile;
    private Instant lastLoginTime;
    private Instant createTime;
    private UUID createBy;
    private Instant updateTime;
    private UUID updateBy;
    private Boolean deleted;
}
