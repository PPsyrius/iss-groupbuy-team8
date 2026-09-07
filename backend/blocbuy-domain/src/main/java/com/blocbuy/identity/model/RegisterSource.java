package com.blocbuy.identity.model;

/**
 * Registration channel (sys_user.register_source). Allowed values are application-managed per the
 * identity DDL; persisted codes are stable lowercase strings.
 */
public enum RegisterSource {
    LOCAL("local"),
    GOOGLE("google");

    private final String code;

    RegisterSource(String code) {
        this.code = code;
    }

    /** Stored code, e.g. {@code "google"}. */
    public String code() {
        return code;
    }

    /** Reverse lookup; unknown codes fail fast instead of silently mapping. */
    public static RegisterSource fromCode(String code) {
        for (RegisterSource source : values()) {
            if (source.code.equals(code)) {
                return source;
            }
        }
        throw new IllegalArgumentException("unknown register source code: " + code);
    }
}
