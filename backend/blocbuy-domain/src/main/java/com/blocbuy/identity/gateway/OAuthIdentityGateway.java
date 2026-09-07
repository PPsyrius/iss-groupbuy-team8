package com.blocbuy.identity.gateway;

import com.blocbuy.identity.model.OAuthProvider;

/**
 * Outbound port to the OIDC identity provider (Google first). Defined on the domain side and
 * implemented in the infrastructure layer — signatures carry only provider-free types, no {@code
 * com.google} classes.
 *
 * <p>Contract: the implementation verifies the provider id_token signature plus iss/aud/exp and
 * rejects unverified emails; provider access/refresh tokens are never stored or returned. Failures
 * surface as {@link com.blocbuy.common.exception.BizException} built from {@link
 * com.blocbuy.identity.exception.IdentityErrorCode}; raw provider errors never leak upward.
 */
public interface OAuthIdentityGateway {

    /**
     * Builds the provider authorization URL for the backend-redirect flow.
     *
     * @param state one-time CSRF token previously issued by {@link OAuthStateStore}
     * @return absolute URL the client is redirected to
     * @throws com.blocbuy.common.exception.BizException with {@code IDENTITY_502_001} when the
     *     provider is not configured
     */
    String buildAuthorizeUrl(String state);

    /**
     * Exchanges an authorization code and verifies the resulting id_token.
     *
     * @param code authorization code from the provider callback
     * @return the verified provider profile reduced to identity-relevant facts
     * @throws com.blocbuy.common.exception.BizException with {@code IDENTITY_401_002} when token
     *     verification fails, {@code IDENTITY_502_001} on provider exchange failure
     */
    OAuthProfile exchangeCodeForProfile(String code);

    /**
     * A verified provider profile. {@code rawProfileJson} is PII for audit/repair only — never
     * returned to clients, never logged.
     *
     * @param provider which provider issued the identity
     * @param subject provider-side stable user id (Google {@code sub})
     * @param email verified email reported by the provider
     * @param displayName profile display name, may be null
     * @param avatarUrl profile picture URL, may be null
     * @param rawProfileJson full verified payload as JSON
     */
    record OAuthProfile(
            OAuthProvider provider,
            String subject,
            String email,
            String displayName,
            String avatarUrl,
            String rawProfileJson) {}
}
