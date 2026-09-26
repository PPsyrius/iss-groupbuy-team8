package com.blocbuy.groupbuy.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Groupbuy's public collaboration surface for a checkout that has not been paid yet. The order
 * module prices a JOIN from the team's own frozen terms, but must not read {@code biz_group_deal}
 * or the {@link com.blocbuy.groupbuy.model.GroupDeal} aggregate itself: cross-domain access to
 * another module's mappers, DOs and aggregates is forbidden, so everything a checkout needs to know
 * about a team arrives through this facade.
 *
 * <p>This is the unpaid twin of {@link PaidGroupDealService}, which admits an already-confirmed
 * payment. Nothing here writes; it locks and answers.
 */
public interface GroupDealPurchaseSupport {

    /**
     * Takes the deal row lock and validates that this buyer may still pay into this team, then
     * returns the terms a quote must be cut from. The lock is the first one in the fixed order
     * group deal → purchase intent → payment and is held until the caller's transaction ends, so a
     * concurrent JOIN cannot overfill the team between this check and the intent write.
     *
     * @throws com.blocbuy.common.exception.BizException {@code GROUPBUY_404_001} when the deal is
     *     gone, {@code DEAL_CLOSED} when it is not open or its window has passed, {@code DEAL_FULL}
     *     when it already has every member it needs, {@code ALREADY_MEMBER} when this buyer leads
     *     or has already joined it, {@code INVALID_PARTICIPATION} when an identifier is missing
     */
    JoinTerms lockJoinTerms(UUID dealId, UUID buyerId);

    /**
     * Resolves a UC7 invite reference presented with a JOIN into the sharer to freeze on the
     * intent. Never fails the purchase: a missing, unknown, other-team or self-referring reference
     * resolves to empty, and the join simply goes unattributed. Call after {@link #lockJoinTerms},
     * which has already refused a closed team.
     */
    Optional<UUID> resolveSharer(String inviteRef, UUID dealId, UUID buyerId);

    /**
     * What groupbuy contributes to a JOIN quote. The amount is the team's frozen per-buyer price —
     * never a fresh catalog policy price, which may have moved since the team opened.
     */
    record JoinTerms(
            UUID dealId,
            UUID productId,
            BigDecimal amount,
            String currency,
            int targetSize,
            Instant expireTime) {}
}
