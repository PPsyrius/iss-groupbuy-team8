package com.blocbuy.identity.repository;

import com.blocbuy.identity.model.OAuthIdentity;
import com.blocbuy.identity.model.OAuthProvider;
import java.util.Optional;

/**
 * Repository port for OAuth identity bindings (sys_user_oauth). Implemented in the infrastructure
 * layer. The unique partial index on (provider, provider_user_id) is the backstop against
 * double-register races; implementations let the resulting duplicate-key violation propagate so the
 * application layer can retry-then-login.
 */
public interface UserOAuthRepository {

    /** Active binding by provider and provider-side subject (Google {@code sub}). */
    Optional<OAuthIdentity> findActiveByProviderAndSubject(OAuthProvider provider, String subject);

    /** Inserts a new binding. */
    void saveNew(OAuthIdentity identity);

    /** Persists the post-login provider snapshot (email/name/avatar/raw profile + login stamp). */
    void updateLoginSnapshot(OAuthIdentity identity);
}
