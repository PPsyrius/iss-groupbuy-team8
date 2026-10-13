package com.blocbuy.common.constant;

import java.util.UUID;

/** Shared Sa-Token account-session keys; only identity writes the authorization snapshot. */
public final class SessionRbac {
    public static final String SNAPSHOT = "blocbuy.rbac";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";

    /** Separate account authorization from Sa-Token's concurrently updated terminal metadata. */
    public static String sessionId(String tokenName, String loginType, UUID userId) {
        return tokenName + ":rbac:" + loginType + ":{" + userId + "}";
    }

    private SessionRbac() {}
}
