package com.blocbuy.identity.persistence;

import com.blocbuy.identity.model.OAuthIdentity;
import com.blocbuy.identity.model.OAuthProvider;

/** Hand-written SysUserOAuthDO ↔ OAuthIdentity converter (no MapStruct — locked decision). */
final class UserOAuthConverter {

    private UserOAuthConverter() {}

    static OAuthIdentity toDomain(SysUserOAuthDO data) {
        if (data == null) {
            return null;
        }
        return OAuthIdentity.reconstitute(
                data.getId(),
                data.getUserId(),
                OAuthProvider.fromCode(data.getProvider()),
                data.getProviderUserId(),
                data.getEmail(),
                data.getDisplayName(),
                data.getAvatarUrl(),
                data.getRawProfile(),
                data.getLastLoginTime());
    }

    static SysUserOAuthDO toDO(OAuthIdentity identity) {
        SysUserOAuthDO data = new SysUserOAuthDO();
        data.setId(identity.id());
        data.setUserId(identity.userId());
        data.setProvider(identity.provider().code());
        data.setProviderUserId(identity.providerUserId());
        data.setEmail(identity.email());
        data.setDisplayName(identity.displayName());
        data.setAvatarUrl(identity.avatarUrl());
        data.setRawProfile(identity.rawProfile());
        data.setLastLoginTime(identity.lastLoginTime());
        return data;
    }
}
