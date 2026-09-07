package com.blocbuy.identity.gateway;

/**
 * Store for one-time OAuth {@code state} parameters (CSRF protection on the redirect flow).
 * Implemented in the infrastructure layer (Redis, short TTL). States are single-use: a consumed or
 * expired state must never authenticate.
 */
public interface OAuthStateStore {

    /**
     * Issues a fresh random state value and stores it with a short TTL.
     *
     * @return the state value to embed in the authorization URL
     */
    String issueState();

    /**
     * Consumes a state exactly once.
     *
     * @param state value from the provider callback
     * @return true when the state existed and was removed; false when unknown or expired
     */
    boolean consume(String state);
}
