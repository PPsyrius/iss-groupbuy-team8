package com.blocbuy.groupbuy.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A group deal reached its target size and closed successfully. Published in-process by the
 * groupbuy application layer inside the transaction that wrote the transition; subscribers in other
 * domains react to this immutable fact rather than being called inline.
 *
 * @param dealId the team that succeeded
 * @param productId the catalog product frozen into the team
 * @param size the paid participant count at success, equal to the team's target size
 * @param at the instant the final member was admitted
 */
public record TeamSucceeded(UUID dealId, UUID productId, int size, Instant at) {

    /** Rejects a fact that cannot have happened; events carry no optional identity. */
    public TeamSucceeded {
        if (dealId == null || productId == null || at == null) {
            throw new IllegalArgumentException("dealId, productId and at must not be null");
        }
        if (size < 2) {
            throw new IllegalArgumentException("a succeeded team has at least two members");
        }
    }
}
