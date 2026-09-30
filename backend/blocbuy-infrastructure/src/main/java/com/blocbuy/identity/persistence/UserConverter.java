package com.blocbuy.identity.persistence;

import com.blocbuy.identity.model.RegisterSource;
import com.blocbuy.identity.model.User;
import com.blocbuy.identity.model.UserProfile;
import com.blocbuy.identity.model.UserStatus;

/**
 * Hand-written SysUserDO ↔ User converter (no MapStruct — locked decision). Enum↔varchar mapping
 * lives here so the aggregate never sees storage codes.
 */
final class UserConverter {

    private UserConverter() {}

    static User toDomain(SysUserDO data) {
        if (data == null) {
            return null;
        }
        return User.reconstitute(
                data.getId(),
                data.getUsername(),
                data.getEmail(),
                RegisterSource.fromCode(data.getRegisterSource()),
                UserStatus.fromCode(data.getStatus()),
                new UserProfile(data.getNickname(), data.getAvatarUrl()),
                data.getLastLoginTime());
    }

    static SysUserDO toDO(User user) {
        SysUserDO data = new SysUserDO();
        data.setId(user.id());
        data.setUsername(user.username());
        data.setEmail(user.email());
        data.setNickname(user.nickname());
        data.setAvatarUrl(user.avatarUrl());
        data.setRegisterSource(user.registerSource().code());
        data.setStatus(user.status().code());
        data.setLastLoginTime(user.lastLoginTime());
        return data;
    }
}
