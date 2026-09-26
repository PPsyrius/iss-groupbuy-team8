package com.blocbuy.groupbuy.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.event.MemberJoined;
import com.blocbuy.groupbuy.event.TeamSucceeded;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.exception.ParticipationErrorCode;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.model.NewDealTerms;
import com.blocbuy.groupbuy.model.PaidGroupDealCreator;
import com.blocbuy.groupbuy.model.PaidParticipation;
import com.blocbuy.groupbuy.model.ParticipationReceipt;
import com.blocbuy.groupbuy.model.ParticipationResult;
import com.blocbuy.groupbuy.model.StandardPaidGroupDealCreator;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal trusted-payment collaborator, not an HTTP checkout or a payment verifier. Caller must
 * authenticate/persist confirmed payment, bind buyer/business intent, validate active buyer and
 * accepted catalog/inventory snapshots before entry. Cross-domain state must join this transaction
 * or have an explicit recoverable orchestration; no provider call belongs inside it.
 *
 * <p>Business rejection of confirmed money commits a compensation-required fact. Payment owner must
 * arrange compensation after commit; this service never refunds or treats that as success.
 * Technical failures propagate and roll back the receipt, membership and deal together.
 *
 * <p>Every accepted join publishes {@link MemberJoined}, and a join that fills the team then
 * publishes {@link TeamSucceeded}, both in the same transaction. The consequences — confirming
 * orders, crediting a sharer — belong to subscribers, not to inline calls from here.
 */
@Service
@RequiredArgsConstructor
public class PaidGroupDealService {
    private final PaidGroupDealRepository repository;
    private final ApplicationEventPublisher events;
    private final PaidGroupDealCreator creator = new StandardPaidGroupDealCreator();

    /**
     * Applies trusted, preverified payment to accepted initiation terms in a transaction. Identical
     * requests replay the original receipt; business rejection records compensation, while
     * technical failure rolls back all writes. New transactions have a ten-second timeout; an
     * existing caller transaction retains its own deadline. No payment verification occurs here.
     */
    @Transactional(timeout = 10)
    public ParticipationResult startConfirmed(NewDealTerms terms, PaidParticipation paid) {
        if (terms == null || paid == null) {
            throw new BizException(GroupbuyErrorCode.INVALID_PARTICIPATION);
        }
        String fingerprint =
                fingerprint(
                        "start-v1",
                        paid,
                        terms.id(),
                        terms.dealNo(),
                        terms.productId(),
                        terms.price().toPlainString(),
                        terms.currency(),
                        terms.targetSize(),
                        terms.expireTime());
        var receipt = reserve(paid, fingerprint);
        if (receipt.result() != null) {
            return receipt.result();
        }
        Instant now = repository.currentTime();
        GroupDeal deal;
        try {
            deal = creator.initiate(terms, paid, now);
        } catch (BizException rejection) {
            return compensate(terms.id(), paid, rejection, now);
        }
        repository.insertStartedDeal(deal, paid, now);
        var result = ParticipationResult.applied(deal);
        repository.complete(paid.paymentId(), result, paid.userId(), now);
        return result;
    }

    /**
     * Admits trusted, preverified payment under receipt-then-deal locking in a transaction. Replays
     * return the frozen receipt; rejected admissions record compensation without refunding.
     * Technical failure rolls back all writes. New transactions use a ten-second timeout; callers
     * supplying an existing transaction must provide a compatible bounded deadline.
     *
     * <p>The sharer is recorded on the membership and carried on {@link MemberJoined}. It is not
     * part of the receipt fingerprint: it comes from the caller's immutable intent, which a replay
     * of the same payment cannot change. Attribution never fails an admission — a self-referring
     * sharer is dropped rather than rolling back paid money.
     *
     * @param sharerUserId the member whose invite the buyer came through, or null
     */
    @Transactional(timeout = 10)
    public ParticipationResult joinConfirmed(
            UUID dealId, PaidParticipation paid, UUID sharerUserId) {
        if (dealId == null || paid == null) {
            throw new BizException(GroupbuyErrorCode.INVALID_PARTICIPATION);
        }
        var receipt = reserve(paid, fingerprint("join-v1", paid, dealId));
        if (receipt.result() != null) {
            return receipt.result();
        }
        var existing = repository.lockDeal(dealId);
        Instant now = repository.currentTime();
        if (existing.isEmpty()) {
            return compensate(
                    dealId, paid, new BizException(GroupbuyErrorCode.GROUPBUY_PLACEHOLDER), now);
        }
        var before = existing.orElseThrow();
        boolean member = repository.hasActiveMember(dealId, paid.userId());
        GroupDeal after;
        try {
            after = before.joinPaid(paid, member, now);
        } catch (BizException rejection) {
            return compensate(dealId, paid, rejection, now);
        }
        UUID sharer = paid.userId().equals(sharerUserId) ? null : sharerUserId;
        repository.insertJoinedMember(before, after, paid, sharer, now);
        var result = ParticipationResult.applied(after);
        repository.complete(paid.paymentId(), result, paid.userId(), now);
        // Published before TeamSucceeded, so subscribers see the join before the team's resolution.
        events.publishEvent(
                new MemberJoined(
                        after.id(),
                        after.productId(),
                        paid.userId(),
                        paid.paymentId(),
                        sharer,
                        now));
        if (after.status() == DealStatus.SUCCEEDED) {
            // Subscribers run at BEFORE_COMMIT, after this buyer's own order is saved by the
            // caller — a plain listener would confirm the team while that order is still pending.
            events.publishEvent(
                    new TeamSucceeded(after.id(), after.productId(), after.currentSize(), now));
        }
        return result;
    }

    /**
     * Locks the payment receipt and rejects reuse with a different canonical participation intent.
     */
    private ParticipationReceipt reserve(PaidParticipation paid, String fingerprint) {
        var receipt = repository.reserveAndLock(paid.paymentId(), fingerprint, paid.userId());
        if (!fingerprint.equals(receipt.fingerprint())) {
            throw new BizException(ParticipationErrorCode.PAYMENT_REUSED);
        }
        return receipt;
    }

    /**
     * Completes the receipt with a business rejection for post-commit compensation by its owner.
     */
    private ParticipationResult compensate(
            UUID dealId, PaidParticipation paid, BizException rejection, Instant now) {
        var result = ParticipationResult.compensation(dealId, rejection.errorCode().code());
        repository.complete(paid.paymentId(), result, paid.userId(), now);
        return result;
    }

    /**
     * Hashes versioned payment and intent fields so a receipt cannot be reused for changed terms.
     */
    private static String fingerprint(String operation, PaidParticipation paid, Object... terms) {
        // Length-prefix every field; delimiter characters in dealNo cannot alias other requests.
        var canonical = new StringBuilder();
        append(canonical, operation);
        append(canonical, paid.paymentId());
        append(canonical, paid.userId());
        append(canonical, paid.amount().toPlainString());
        append(canonical, paid.currency());
        for (Object term : terms) {
            append(canonical, term);
        }
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 unavailable", impossible);
        }
    }

    /** Length-prefixes a canonical field so delimiter characters cannot alias another request. */
    private static void append(StringBuilder target, Object value) {
        String text = value.toString();
        target.append(text.length()).append(':').append(text);
    }
}
