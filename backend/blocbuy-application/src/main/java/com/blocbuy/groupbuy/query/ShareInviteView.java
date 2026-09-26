package com.blocbuy.groupbuy.query;

import java.time.Instant;
import java.util.UUID;

/**
 * A member's invite into a team (UC7). The frontend owns its routes and origin, so it builds the
 * link itself from these fields; {@code ref} is opaque to it.
 *
 * @param ref the opaque invite reference to send back on a JOIN intent
 * @param dealId the team the invite is for
 * @param productId the team's product, for the landing page
 * @param expiresAt the team's own deadline — an invite stops attributing when the team closes
 */
public record ShareInviteView(String ref, UUID dealId, UUID productId, Instant expiresAt) {}
