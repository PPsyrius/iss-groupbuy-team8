package com.blocbuy.groupbuy.model;

/**
 * How a buyer takes part in a team (biz_group_member.member_role): the leader started it, a member
 * joined it. Persisted codes are stable lowercase strings.
 */
public enum MemberRole {
    LEADER("leader"),
    MEMBER("member");

    private final String code;

    MemberRole(String code) {
        this.code = code;
    }

    /** Stored code, e.g. {@code "leader"}. */
    public String code() {
        return code;
    }

    /** Reverse lookup; unknown codes fail fast instead of silently mapping. */
    public static MemberRole fromCode(String code) {
        for (MemberRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("unknown member role code: " + code);
    }
}
