package com.blocbuy.groupbuy.query;

import java.time.Instant;
import java.util.UUID;

/**
 * What an invite landing page may show before sign-in (UC7): the team, its product, who shared it
 * and the team's deadline. Carries no user id or contact detail.
 *
 * @param dealId the team the invite is for
 * @param productId the team's product
 * @param sharerNickname the sharer's public nickname; null when there is none to show
 * @param expiresAt the team's own deadline
 */
public record ResolvedInviteView(
        UUID dealId, UUID productId, String sharerNickname, Instant expiresAt) {}
