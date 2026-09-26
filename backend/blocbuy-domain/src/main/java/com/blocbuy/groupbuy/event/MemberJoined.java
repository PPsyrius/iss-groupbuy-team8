package com.blocbuy.groupbuy.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A paid buyer was admitted to an existing team. Published in-process by the groupbuy application
 * layer inside the admission transaction, once per accepted participation — never for a replayed
 * payment or a rejected one. Unlike {@link TeamSucceeded}, it names the individual participation,
 * so a subscriber can attribute it.
 *
 * @param dealId the team joined
 * @param productId the catalog product frozen into the team
 * @param memberUserId the buyer admitted
 * @param paymentId the local payment that admitted them; unique per participation
 * @param sharerUserId the member whose invite the buyer came through, or null when unattributed
 * @param at the instant the member was admitted
 */
public record MemberJoined(
        UUID dealId,
        UUID productId,
        UUID memberUserId,
        UUID paymentId,
        UUID sharerUserId,
        Instant at) {

    /** Rejects a fact that cannot have happened; only the sharer is optional. */
    public MemberJoined {
        if (dealId == null
                || productId == null
                || memberUserId == null
                || paymentId == null
                || at == null) {
            throw new IllegalArgumentException(
                    "dealId, productId, memberUserId, paymentId and at must not be null");
        }
        if (memberUserId.equals(sharerUserId)) {
            throw new IllegalArgumentException("a member cannot be their own sharer");
        }
    }

    /** True when the join came through another member's invite. */
    public boolean attributed() {
        return sharerUserId != null;
    }
}
