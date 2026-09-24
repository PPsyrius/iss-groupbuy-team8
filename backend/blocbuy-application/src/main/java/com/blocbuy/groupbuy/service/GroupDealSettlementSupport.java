package com.blocbuy.groupbuy.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Groupbuy's public collaboration surface for a checkout whose money has already moved — the paid
 * twin of {@link GroupDealPurchaseSupport}. Admission itself stays in {@link PaidGroupDealService};
 * what lives here is the locking, backfilling and reconciliation the order module needs around it
 * but must not reach into {@code biz_group_deal} or {@code biz_group_member} to do.
 *
 * <p>Every method runs inside the caller's transaction and performs no remote I/O.
 */
public interface GroupDealSettlementSupport {

    /**
     * Takes the team's row lock without judging the team — the first lock in the fixed order group
     * deal → purchase intent → payment. Unlike {@link GroupDealPurchaseSupport#lockJoinTerms} this
     * never rejects: at settlement the team may legitimately be full, succeeded or expired, and
     * deciding that is {@link PaidGroupDealService}'s job once the payment is presented.
     *
     * <p>A START settles against a team that does not exist yet, so there is no row to lock. That
     * is not an error: the reserved id is unique to one intent, and the intent's own row lock
     * serializes every writer that can reach it.
     */
    void lockTeam(UUID dealId);

    /**
     * Records the order backing this payment's membership, in the same transaction that created
     * both. Re-linking the same order is a harmless replay.
     *
     * @return false when no active member of the deal carries that payment
     */
    boolean linkOrder(UUID dealId, UUID paymentId, UUID orderId);

    /** The membership a payment produced, for reconciliation; empty when it admitted nobody. */
    Optional<SettledMember> settledMember(UUID paymentId);

    /**
     * Takes the same team row lock joining uses and asserts the team really has expired.
     *
     * @throws IllegalStateException when the team is missing or is not EXPIRED — the caller is
     *     sweeping refunds for an expiry it believes already happened, so a disagreement is a
     *     technical fault rather than a business rejection
     */
    void lockExpiredTeam(UUID dealId);

    /** What groupbuy can attest about one payment's membership. */
    record SettledMember(UUID dealId, UUID userId, UUID orderId, String status) {}
}
