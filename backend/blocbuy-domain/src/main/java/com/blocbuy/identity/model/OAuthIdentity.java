package com.blocbuy.identity.model;

import com.blocbuy.common.util.UuidV7;
import java.time.Instant;
import java.util.UUID;

/**
 * External OAuth identity binding (sys_user_oauth): one row per (provider, provider user) per local
 * account. The provider-side stable subject (Google {@code sub}) is the lookup key; {@code
 * rawProfile} is a PII audit snapshot — never returned to clients, never logged.
 */
public final class OAuthIdentity {

    private final UUID id;
    private final UUID userId;
    private final OAuthProvider provider;
    private final String providerUserId;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String rawProfile;
    private Instant lastLoginTime;

    private OAuthIdentity(
            UUID id,
            UUID userId,
            OAuthProvider provider,
            String providerUserId,
            String email,
            String displayName,
            String avatarUrl,
            String rawProfile,
            Instant lastLoginTime) {
        if (id == null || userId == null) {
            throw new IllegalArgumentException("id and userId must not be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("providerUserId must not be blank");
        }
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.rawProfile = rawProfile;
        this.lastLoginTime = lastLoginTime;
    }

    /** Factory: binds a verified provider profile to a local account. */
    public static OAuthIdentity bind(
            UUID userId,
            OAuthProvider provider,
            String providerUserId,
            String email,
            String displayName,
            String avatarUrl,
            String rawProfile) {
        return new OAuthIdentity(
                UuidV7.next(),
                userId,
                provider,
                providerUserId,
                email,
                displayName,
                avatarUrl,
                rawProfile,
                null);
    }

    /** Reconstitution factory for the infrastructure converter. */
    public static OAuthIdentity reconstitute(
            UUID id,
            UUID userId,
            OAuthProvider provider,
            String providerUserId,
            String email,
            String displayName,
            String avatarUrl,
            String rawProfile,
            Instant lastLoginTime) {
        return new OAuthIdentity(
                id,
                userId,
                provider,
                providerUserId,
                email,
                displayName,
                avatarUrl,
                rawProfile,
                lastLoginTime);
    }

    /** Refreshes the provider snapshot on each successful authentication. */
    public void touchLogin(
            Instant at, String email, String displayName, String avatarUrl, String rawProfile) {
        if (at == null) {
            throw new IllegalArgumentException("login instant must not be null");
        }
        this.lastLoginTime = at;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.rawProfile = rawProfile;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public OAuthProvider provider() {
        return provider;
    }

    public String providerUserId() {
        return providerUserId;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    /** PII — audit/repair only; never serialize to clients or logs. */
    public String rawProfile() {
        return rawProfile;
    }

    public Instant lastLoginTime() {
        return lastLoginTime;
    }
}
