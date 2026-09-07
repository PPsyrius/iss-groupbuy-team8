package com.blocbuy.identity.model;

/**
 * Account status (sys_user.status). Allowed values are application-managed per the identity DDL;
 * persisted codes are stable lowercase strings.
 */
public enum UserStatus {
    ACTIVE("active"),
    DISABLED("disabled");

    private final String code;

    UserStatus(String code) {
        this.code = code;
    }

    /** Stored code, e.g. {@code "active"}. */
    public String code() {
        return code;
    }

    /** Reverse lookup; unknown codes fail fast instead of silently mapping. */
    public static UserStatus fromCode(String code) {
        for (UserStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown user status code: " + code);
    }
}
