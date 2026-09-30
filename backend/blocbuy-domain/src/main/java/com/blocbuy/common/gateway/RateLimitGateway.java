package com.blocbuy.common.gateway;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Port for request rate limits. Implemented in the infrastructure layer (Redis fixed window).
 * Counters are derived state only: losing them lets a few extra requests through and never changes
 * a business fact.
 */
public interface RateLimitGateway {

    /**
     * Counts one request against the subject's current window.
     *
     * @param limit which limit to apply
     * @param subject who is counted, for example a client address or a user id; the implementation
     *     must not store it in clear text
     * @return true when the request fits in the current window; false when it is over the limit. An
     *     implementation that cannot reach its store returns true (fail-open)
     */
    boolean tryAcquire(RateLimit limit, String subject);

    /**
     * One named limit: at most {@code maxRequests} per {@code window} for each subject.
     *
     * @param scope stable lowercase kebab-case name, part of the storage key
     * @param maxRequests requests allowed per window, at least 1
     * @param window window length in whole seconds, at least one second
     */
    record RateLimit(String scope, int maxRequests, Duration window) {

        // possessive quantifiers: no backtracking, so no deep recursion on long input
        private static final Pattern SCOPE_FORMAT = Pattern.compile("[a-z0-9]++(?:-[a-z0-9]++)*+");

        /** Rejects a limit that would build a malformed key or never allow a request. */
        public RateLimit {
            if (scope == null || !SCOPE_FORMAT.matcher(scope).matches()) {
                throw new IllegalArgumentException("scope must be lowercase kebab-case");
            }
            if (maxRequests < 1) {
                throw new IllegalArgumentException("maxRequests must be at least 1");
            }
            if (window == null || window.getSeconds() < 1 || window.getNano() != 0) {
                throw new IllegalArgumentException("window must be whole seconds, at least 1");
            }
        }
    }
}
