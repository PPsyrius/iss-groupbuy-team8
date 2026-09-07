package com.blocbuy.identity.service;

import java.util.UUID;

/**
 * Google OAuth2 login/register use case (backend-redirect flow). The controller adapts HTTP;
 * credential verification and account resolution live here; the Sa-Token session is opened by the
 * adapter only after this use case returns a local user id.
 */
public interface OAuthLoginAppService {

    /**
     * Issues a one-time state bound to the initiating browser session and builds the Google
     * authorization URL the client is redirected to.
     */
    GoogleAuthorizeUrl createGoogleAuthorizeUrl();

    /**
     * Handles the Google callback: validates state against the browser-session binding, verifies
     * the code with the provider, resolves or registers the local account, and records the login.
     * Opening the Sa-Token session is the caller's job.
     *
     * @param stateBinding binding token from the cookie set when the flow started
     * @return the resolved local account facts for session creation
     */
    OAuthLoginResult loginWithGoogle(String code, String state, String stateBinding);

    /**
     * Authorization redirect target and its browser-session binding.
     *
     * @param url Google authorization URL with the one-time state embedded
     * @param stateBinding random binding token the caller sets as an HttpOnly, SameSite=Lax cookie
     */
    record GoogleAuthorizeUrl(String url, String stateBinding) {}

    /**
     * Result of a successful OAuth login/register.
     *
     * @param userId local user id (UUIDv7) — the Sa-Token login id
     * @param nickname display nickname, may be null
     * @param avatarUrl avatar URL, may be null
     */
    record OAuthLoginResult(UUID userId, String nickname, String avatarUrl) {}
}
