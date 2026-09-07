package com.blocbuy.identity.persistence;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for sys_user_oauth. The partial unique index on (provider, provider_user_id)
 * backstops double-register races; duplicate-key violations propagate to the application layer
 * (retry-then-login).
 */
@Mapper
public interface SysUserOAuthMapper {

    /** Active binding by provider code and provider-side subject. */
    SysUserOAuthDO selectActiveByProviderAndSubject(
            @Param("provider") String provider, @Param("providerUserId") String providerUserId);

    /** Inserts one binding row; the id is supplied by the domain (UUIDv7). */
    int insert(SysUserOAuthDO binding);

    /** Refreshes the post-login provider snapshot; returns affected rows. */
    int updateLoginSnapshot(
            @Param("id") UUID id,
            @Param("email") String email,
            @Param("displayName") String displayName,
            @Param("avatarUrl") String avatarUrl,
            @Param("rawProfile") String rawProfile,
            @Param("lastLoginTime") Instant lastLoginTime);
}
