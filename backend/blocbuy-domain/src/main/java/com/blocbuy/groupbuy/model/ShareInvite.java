package com.blocbuy.groupbuy.model;

import com.blocbuy.common.util.UuidV7;
import java.util.Base64;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * One member's invite into one team (UC7). The reference is opaque to every client: 128 random
 * bits, base64url without padding, so it is URL-safe, unguessable, and names no user. It grants
 * nothing by itself — joining still requires a session and payment — it only lets the server
 * attribute a join to the member who shared it.
 *
 * <p>Immutable once minted: a team has at most one invite per sharer, and "share again" returns the
 * same one.
 */
public record ShareInvite(UUID id, String ref, UUID dealId, UUID sharerUserId) {

    /**
     * Format of every server-generated reference: 22 base64url characters. Public so the web
     * adapter validates a path variable against the same rule.
     */
    public static final String REF_PATTERN = "[A-Za-z0-9_-]{22}";

    private static final int REF_BYTES = 16;
    private static final Pattern REF_FORMAT = Pattern.compile(REF_PATTERN);

    /** Rejects a malformed invite; only server-generated references are ever stored. */
    public ShareInvite {
        if (id == null || dealId == null || sharerUserId == null) {
            throw new IllegalArgumentException("id, dealId and sharerUserId must not be null");
        }
        if (!isWellFormedRef(ref)) {
            throw new IllegalArgumentException("ref must be 22 base64url characters");
        }
    }

    /** Whether the value could be a server-generated reference; null-safe. */
    public static boolean isWellFormedRef(String ref) {
        return ref != null && REF_FORMAT.matcher(ref).matches();
    }

    /**
     * Mints a fresh invite. The caller has already checked that the sharer belongs to the team.
     *
     * @param random a cryptographically strong source in production ({@code SecureRandom})
     */
    public static ShareInvite mint(UUID dealId, UUID sharerUserId, RandomGenerator random) {
        byte[] bytes = new byte[REF_BYTES];
        random.nextBytes(bytes);
        String ref = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new ShareInvite(UuidV7.next(), ref, dealId, sharerUserId);
    }

    /**
     * Whether this invite may attribute a buyer's join: it must be for the same team, and nobody
     * earns credit for inviting themselves.
     */
    public boolean attributes(UUID joiningDealId, UUID buyerId) {
        return dealId.equals(joiningDealId) && !sharerUserId.equals(buyerId);
    }
}
