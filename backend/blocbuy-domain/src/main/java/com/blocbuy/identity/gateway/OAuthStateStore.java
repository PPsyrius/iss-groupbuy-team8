package com.blocbuy.identity.gateway;

/**
 * Store for one-time OAuth {@code state} parameters (CSRF protection on the redirect flow).
 * Implemented in the infrastructure layer (Redis, short TTL). States are single-use: a consumed or
 * expired state must never authenticate. Each issued state is additionally bound to the initiating
 * browser session via a random binding token (carried in an HttpOnly cookie), so a leaked state
 * alone cannot complete a login from another browser.
 */
public interface OAuthStateStore {

    /**
     * Issues a fresh random state value plus a random browser-session binding, stored together with
     * a short TTL.
     *
     * @return the state to embed in the authorization URL and the binding token for the cookie
     */
    IssuedState issueState();

    /**
     * Consumes a state exactly once, validating the browser-session binding atomically.
     *
     * @param state value from the provider callback
     * @param binding browser-session binding token from the cookie set at authorize time
     * @return true when the state existed, matched the binding, and was removed; false otherwise
     */
    boolean consume(String state, String binding);

    /**
     * A freshly issued state and its browser-session binding.
     *
     * @param state value to embed in the authorization URL
     * @param binding random token to set as an HttpOnly, SameSite=Lax cookie on the browser
     */
    record IssuedState(String state, String binding) {}
}
