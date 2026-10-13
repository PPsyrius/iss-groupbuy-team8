package com.blocbuy.identity.exception;

/**
 * Identity could not answer: a timeout, a refused connection, a 5xx or any reply that is not one of
 * identity's own business answers (backend ADR 0008, #468). Deliberately not a {@code
 * BizException}: callers that turn identity's business refusals into outcomes (the Stripe webhook's
 * {@code BUYER_INACTIVE} branch) must not mistake an outage for one. It propagates to the global
 * handler as an unknown failure, so the request fails closed with 500 and its transaction rolls
 * back.
 */
public final class IdentityUnavailableException extends RuntimeException {

    @java.io.Serial private static final long serialVersionUID = 1L;

    /**
     * @param message what was asked and how it failed; never a response body
     * @param cause the transport failure, or null when identity answered unexpectedly
     */
    public IdentityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
