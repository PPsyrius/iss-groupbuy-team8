package com.blocbuy.groupbuy.model;

import com.blocbuy.groupbuy.event.MemberJoined;
import java.time.Instant;
import java.util.UUID;

/**
 * One sharer credited for one attributed participation (UC7). Immutable: whether the credit is
 * pending, earned or void follows the team's own status at read time, so no lifecycle subscriber
 * can race the post-commit insert and leave it stale.
 */
public record ShareCredit(
        UUID dealId, UUID paymentId, UUID sharerUserId, UUID inviteeUserId, Instant creditTime) {

    /** Rejects an incomplete or self-referring credit. */
    public ShareCredit {
        if (dealId == null
                || paymentId == null
                || sharerUserId == null
                || inviteeUserId == null
                || creditTime == null) {
            throw new IllegalArgumentException("every share credit field is required");
        }
        if (sharerUserId.equals(inviteeUserId)) {
            throw new IllegalArgumentException("a sharer cannot be credited for their own join");
        }
    }

    /**
     * The credit an attributed join earns.
     *
     * @throws IllegalArgumentException when the join was not attributed
     */
    public static ShareCredit of(MemberJoined joined) {
        return new ShareCredit(
                joined.dealId(),
                joined.paymentId(),
                joined.sharerUserId(),
                joined.memberUserId(),
                joined.at());
    }
}
