package com.blocbuy.identity.model;

/**
 * OAuth provider codes (sys_user_oauth.provider). Provider-neutral table on purpose; one enum
 * constant per supported provider. Persisted codes are stable lowercase strings.
 */
public enum OAuthProvider {
    GOOGLE("google");

    private final String code;

    OAuthProvider(String code) {
        this.code = code;
    }

    /** Stored provider code, e.g. {@code "google"}. */
    public String code() {
        return code;
    }

    /** Reverse lookup; unknown codes fail fast instead of silently mapping. */
    public static OAuthProvider fromCode(String code) {
        for (OAuthProvider provider : values()) {
            if (provider.code.equals(code)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("unknown OAuth provider code: " + code);
    }
}
