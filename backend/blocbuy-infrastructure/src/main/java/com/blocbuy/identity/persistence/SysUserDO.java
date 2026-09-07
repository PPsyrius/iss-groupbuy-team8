package com.blocbuy.identity.persistence;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * Persistence object for sys_user — table shape only, no business rules. Never crosses the
 * infrastructure boundary; the converter maps it to the {@code User} aggregate.
 */
@Data
public class SysUserDO {

    private UUID id;
    private String username;
    private String email;
    private String phone;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String registerSource;
    private String status;
    private Instant lastLoginTime;
    private Instant createTime;
    private UUID createBy;
    private Instant updateTime;
    private UUID updateBy;
    private Boolean deleted;
}
