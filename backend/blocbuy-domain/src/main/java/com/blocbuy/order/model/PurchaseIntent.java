package com.blocbuy.order.model;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.order.exception.OrderErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Purchase intent aggregate root — the server's own answer to "what would this checkout cost, and
 * on what terms", issued before any provider call. A browser names a product (START) or a team
 * (JOIN) and nothing else: the amount, the team size and both deadlines are computed here under a
 * row lock and frozen, so no request can choose its own price.
 *
 * <p>An intent is consumed by at most one payment. {@link #fulfilBy(UUID)} is the only way to mark
 * that, and it refuses a second, different payment — the database's partial unique index on {@code
 * fulfilled_payment_id} is the same rule at the other end.
 *
 * <p>Rules follow the {@link Order} idiom: framework-free, immutable snapshots, transitions through
 * behavioral methods, no public setters. Instants are truncated to microseconds because that is
 * what PostgreSQL {@code timestamptz} stores; keeping the boundary here means a reconstituted
 * intent equals the one that was written.
 */
public final class PurchaseIntent {

    private final UUID id;
    private final Terms terms;
    private final Instant quoteValidUntil;
    private final Instant businessDeadline;
    private final UUID fulfilledPaymentId;
    private final Instant createTime;
    private final int version;

    private PurchaseIntent(
            UUID id,
            Terms terms,
            Instant quoteValidUntil,
            Instant businessDeadline,
            UUID fulfilledPaymentId,
            Instant createTime,
            int version) {
        requireTerms(terms);
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (quoteValidUntil == null || businessDeadline == null) {
            throw new IllegalArgumentException("both deadlines must be present");
        }
        Instant quote = quoteValidUntil.truncatedTo(ChronoUnit.MICROS);
        Instant deadline = businessDeadline.truncatedTo(ChronoUnit.MICROS);
        if (deadline.isBefore(quote)) {
            throw new BizException(OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.id = id;
        this.terms = terms;
        this.quoteValidUntil = quote;
        this.businessDeadline = deadline;
        this.fulfilledPaymentId = fulfilledPaymentId;
        this.createTime = createTime;
        this.version = version;
    }

    /**
     * Immutable commitment the server made. {@code dealId} is the team being joined, or for START
     * the id the team will be created with — reserved at quote time so the group row lock has a
     * target before the team exists.
     */
    public record Terms(
            UUID userId,
            PurchaseOperation operation,
            UUID dealId,
            UUID productId,
            String productTitle,
            String productImage,
            BigDecimal amount,
            String currency,
            int targetSize) {}

    /** Issues a fresh, unfulfilled intent. Callers hold the product (START) or team (JOIN) lock. */
    public static PurchaseIntent issue(
            Terms terms, Instant quoteValidUntil, Instant businessDeadline, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (quoteValidUntil == null || !now.isBefore(quoteValidUntil)) {
            throw new BizException(OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        }
        return new PurchaseIntent(
                UuidV7.next(), terms, quoteValidUntil, businessDeadline, null, now, 0);
    }

    /** Reconstitution factory for the infrastructure converter; all fields come from the row. */
    public static PurchaseIntent reconstitute(
            UUID id,
            Terms terms,
            Instant quoteValidUntil,
            Instant businessDeadline,
            UUID fulfilledPaymentId,
            Instant createTime,
            int version) {
        return new PurchaseIntent(
                id,
                terms,
                quoteValidUntil,
                businessDeadline,
                fulfilledPaymentId,
                createTime,
                version);
    }

    /**
     * Binds this intent to the payment that consumed it.
     *
     * @throws BizException {@code PURCHASE_INTENT_ALREADY_FULFILLED} when a different payment
     *     already consumed it. Re-fulfilling with the same payment is a replay and returns an
     *     unchanged snapshot, so a repeated webhook is harmless.
     */
    public PurchaseIntent fulfilBy(UUID paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId must not be null");
        }
        if (fulfilledPaymentId != null) {
            if (fulfilledPaymentId.equals(paymentId)) {
                return this;
            }
            throw new BizException(OrderErrorCode.PURCHASE_INTENT_ALREADY_FULFILLED);
        }
        return new PurchaseIntent(
                id, terms, quoteValidUntil, businessDeadline, paymentId, createTime, version);
    }

    /**
     * Rejects an intent that is not this buyer's. Ownership is never inferred from the request: the
     * caller passes the authenticated user and a mismatch is a 404-shaped miss, not a 403, so an
     * attacker learns nothing about another buyer's intent ids.
     */
    public void requireOwner(UUID authenticatedUser) {
        if (authenticatedUser == null || !authenticatedUser.equals(terms.userId())) {
            throw new BizException(OrderErrorCode.PURCHASE_INTENT_NOT_FOUND);
        }
    }

    /** True once either the quote or the team's own deadline has passed; both are exclusive. */
    public boolean expiredAt(Instant now) {
        return !now.isBefore(businessDeadline) || !now.isBefore(quoteValidUntil);
    }

    public boolean fulfilled() {
        return fulfilledPaymentId != null;
    }

    private static void requireTerms(Terms terms) {
        if (terms == null
                || terms.userId() == null
                || terms.operation() == null
                || terms.dealId() == null
                || terms.productId() == null
                || terms.productTitle() == null
                || terms.productTitle().isBlank()
                || terms.productTitle().length() > 256) {
            throw new BizException(OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        }
        if (terms.productImage() != null && terms.productImage().length() > 1024) {
            throw new BizException(OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        }
        if (terms.targetSize() < 2) {
            throw new BizException(OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        }
        requireAmount(terms.amount(), terms.currency());
    }

    private static void requireAmount(BigDecimal amount, String currency) {
        if (!"sgd".equals(currency)) {
            throw new BizException(OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        }
        if (amount == null
                || amount.signum() <= 0
                || amount.scale() > 2
                || amount.setScale(2, RoundingMode.UNNECESSARY).precision() > 18) {
            throw new BizException(OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        }
    }

    public UUID id() {
        return id;
    }

    public Terms terms() {
        return terms;
    }

    public UUID userId() {
        return terms.userId();
    }

    public PurchaseOperation operation() {
        return terms.operation();
    }

    public UUID dealId() {
        return terms.dealId();
    }

    public UUID productId() {
        return terms.productId();
    }

    public String productTitle() {
        return terms.productTitle();
    }

    public String productImage() {
        return terms.productImage();
    }

    public BigDecimal amount() {
        return terms.amount();
    }

    public String currency() {
        return terms.currency();
    }

    public int targetSize() {
        return terms.targetSize();
    }

    public Instant quoteValidUntil() {
        return quoteValidUntil;
    }

    public Instant businessDeadline() {
        return businessDeadline;
    }

    public UUID fulfilledPaymentId() {
        return fulfilledPaymentId;
    }

    public Instant createTime() {
        return createTime;
    }

    public int version() {
        return version;
    }
}
