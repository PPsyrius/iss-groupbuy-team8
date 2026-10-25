package com.blocbuy.identity.model;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.identity.exception.IdentityErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * User aggregate root (sys_user). Local account record; OAuth identities bind in sys_user_oauth and
 * never leak provider tokens into this aggregate.
 *
 * <p>Rules: the aggregate root is the only entry point for mutating its state; state transitions go
 * through behavioral methods that validate the current state (no public {@code setStatus}); no
 * Lombok {@code @Data}. OAuth-only users have a null password hash and (initially) a null username;
 * email is the canonical OAuth account-resolution identifier.
 */
public final class User {

    private final UUID id;
    private final String username;
    private final String email;
    private final RegisterSource registerSource;
    private UserStatus status;
    private final String nickname;
    private final String avatarUrl;
    private Instant lastLoginTime;

    private User(
            UUID id,
            String username,
            String email,
            RegisterSource registerSource,
            UserStatus status,
            UserProfile profile,
            Instant lastLoginTime) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (registerSource == null) {
            throw new IllegalArgumentException("registerSource must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.id = id;
        this.username = username;
        this.email = email;
        this.registerSource = registerSource;
        this.status = status;
        UserProfile display = profile == null ? UserProfile.EMPTY : profile;
        this.nickname = display.nickname();
        this.avatarUrl = display.avatarUrl();
        this.lastLoginTime = lastLoginTime;
    }

    /**
     * Factory for a brand-new OAuth registration: active from birth, no username yet, nickname
     * falling back to the email local part when the provider gave no display name.
     */
    public static User registerViaOAuth(
            RegisterSource source, String email, String displayName, String avatarUrl) {
        if (source == RegisterSource.LOCAL) {
            throw new IllegalArgumentException("OAuth registration requires an OAuth source");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("OAuth registration requires a non-blank email");
        }
        String nickname =
                displayName != null && !displayName.isBlank() ? displayName : email.split("@")[0];
        return new User(
                UuidV7.next(),
                null,
                email,
                source,
                UserStatus.ACTIVE,
                new UserProfile(nickname, avatarUrl),
                null);
    }

    /**
     * Reconstitution factory for the infrastructure converter; all fields come from the row. A null
     * profile means neither a nickname nor an avatar.
     */
    public static User reconstitute(
            UUID id,
            String username,
            String email,
            RegisterSource registerSource,
            UserStatus status,
            UserProfile profile,
            Instant lastLoginTime) {
        return new User(id, username, email, registerSource, status, profile, lastLoginTime);
    }

    /** Stamps a successful login (any channel). */
    public void touchLogin(Instant at) {
        if (at == null) {
            throw new IllegalArgumentException("login instant must not be null");
        }
        this.lastLoginTime = at;
    }

    /** Login guard: disabled accounts must not open sessions. */
    public void ensureActive() {
        if (status != UserStatus.ACTIVE) {
            throw new BizException(IdentityErrorCode.ACCOUNT_DISABLED);
        }
    }

    /**
     * The nickname other people may see: on an invite landing page that anyone with the link can
     * open, and on the admin payments-needing-attention page. Empty for an account that is not
     * active, for a nickname that looks like an email address, and for a nickname that is only the
     * email local part ignoring case and surrounding whitespace (the OAuth fallback when the
     * provider gives no display name), so no display ever reveals contact information. A showable
     * nickname is returned exactly as stored.
     */
    public Optional<String> publicNickname() {
        if (status != UserStatus.ACTIVE || nickname == null || nickname.isBlank()) {
            return Optional.empty();
        }
        if (nickname.indexOf('@') >= 0) {
            return Optional.empty();
        }
        if (email != null && nickname.strip().equalsIgnoreCase(emailLocalPart(email))) {
            return Optional.empty();
        }
        return Optional.of(nickname);
    }

    private static String emailLocalPart(String email) {
        int at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }

    /** Administrative disable; idempotent on an already-disabled account. */
    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    public UUID id() {
        return id;
    }

    /** Null until the user picks a login name; unique among active rows when present. */
    public String username() {
        return username;
    }

    /** Canonical contact email; primary identifier for OAuth account resolution. */
    public String email() {
        return email;
    }

    public RegisterSource registerSource() {
        return registerSource;
    }

    public UserStatus status() {
        return status;
    }

    public String nickname() {
        return nickname;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public Instant lastLoginTime() {
        return lastLoginTime;
    }
}
