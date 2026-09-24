package com.blocbuy.groupbuy.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A group deal reached its deadline without filling up and was resolved as expired. Published
 * in-process by the groupbuy application layer inside the transaction that wrote the transition, so
 * a subscriber that fails rolls the expiry back with it — the money owed and the team's fate live
 * or die together.
 *
 * @param dealId the team that expired
 * @param productId the catalog product frozen into the team
 * @param at the instant the expiry was resolved
 */
public record TeamExpired(UUID dealId, UUID productId, Instant at) {

    /** Rejects a fact that cannot have happened; events carry no optional identity. */
    public TeamExpired {
        if (dealId == null || productId == null || at == null) {
            throw new IllegalArgumentException("dealId, productId and at must not be null");
        }
    }
}
