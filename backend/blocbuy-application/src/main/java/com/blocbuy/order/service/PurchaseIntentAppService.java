package com.blocbuy.order.service;

import com.blocbuy.catalog.service.CatalogPurchaseSupport;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PurchaseQuoteWindow;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.groupbuy.service.GroupDealPurchaseSupport;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.order.exception.OrderErrorCode;
import com.blocbuy.order.model.PurchaseIntent;
import com.blocbuy.order.model.PurchaseOperation;
import com.blocbuy.order.repository.PurchaseIntentRepository;
import com.blocbuy.payment.gateway.PaymentIntentPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The order module's answer to "what would this checkout cost?" — the production implementation of
 * the payment slice's {@link PaymentIntentPort}, which until now had none.
 *
 * <p>Only identifiers enter: the buyer comes from the Sa-Token session, the business id names a
 * product (START) or a team (JOIN), and a START may name the price quote the buyer was shown. Every
 * number that ends up on the payment — the amount, the currency, the team size and both deadlines —
 * is read here from the database under a lock, so no request can choose its own price. The result
 * is a {@link PurchaseIntent} row; the payment slice later turns it into a {@code CheckoutQuote}
 * and never re-prices it.
 *
 * <p>Collaborators are the three other domains' public application facades, never their mappers or
 * aggregates. Locks are taken in the fixed global order group deal → purchase intent → payment;
 * this service holds the first (JOIN) or the product row (START) and writes the second.
 */
@RequiredArgsConstructor
public class PurchaseIntentAppService implements PaymentIntentPort {

    private final CatalogPurchaseSupport products;
    private final GroupDealPurchaseSupport deals;
    private final UserAppService users;
    private final PurchaseIntentRepository intents;

    /**
     * The product an intent was priced for, for the buyer's receipt. Joins the receipt's read-only
     * snapshot; the intent's product is immutable once issued. Ownership is re-checked here even
     * though payment already scoped the receipt to its buyer.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<UUID> purchasedProduct(UUID user, UUID intentId) {
        if (user == null || intentId == null) {
            return Optional.empty();
        }
        return intents.findById(intentId)
                .filter(intent -> user.equals(intent.userId()))
                .map(PurchaseIntent::productId);
    }

    /**
     * Issues one intent and returns its id.
     *
     * <p>{@code MANDATORY}: the caller owns the transaction, because the row locks this takes (the
     * product row for a start, the team row for a join) must stay held until the intent is
     * committed, so its terms are checked against rows no concurrent writer can change first, and
     * checks on one team run one at a time. An intent reserves no seat: once it commits, another
     * buyer can be issued an intent for the same last seat. The seat is decided after payment, when
     * settlement locks the team again and admits one buyer; the other is refunded.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID createIntent(
            UUID user, String operation, UUID businessId, String inviteRef, UUID quoteId) {
        if (user == null || businessId == null) {
            throw new BizException(OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        }
        PurchaseOperation kind =
                PurchaseOperation.parse(operation)
                        .orElseThrow(() ->
                                new BizException(OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID));
        // A JOIN pays the team's frozen price, so a quote there is a client bug. Unlike an invite
        // reference on a START, a money term is refused rather than ignored.
        if (kind == PurchaseOperation.JOIN && quoteId != null) {
            throw new BizException(OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        }
        // The session says who is logged in; identity says whether that account may still buy.
        users.requireActive(user);
        Instant now = intents.currentTime();
        PurchaseIntent issued =
                switch (kind) {
                    case START -> start(user, businessId, quoteId, now);
                    case JOIN -> join(user, businessId, inviteRef, now);
                };
        return intents.save(issued).id();
    }

    /**
     * START: the business id is a product, and the team it will open does not exist yet. Any invite
     * reference is ignored — a buyer starting their own team may still hold one from browsing. With
     * a quote id the buyer pays the quote they were shown (#183); without one, the latest policy.
     */
    private PurchaseIntent start(UUID user, UUID productId, UUID quoteId, Instant now) {
        CatalogPurchaseSupport.StartQuote quote =
                quoteId == null
                        ? products.lockStartQuote(productId, now)
                        : products.lockQuotedStart(productId, quoteId, user);
        // Reserved, not created: the team is only inserted once the payment succeeds, but the
        // settlement path needs a stable lock target from the moment the quote exists.
        UUID dealId = UuidV7.next();
        PurchaseIntent.Terms terms =
                new PurchaseIntent.Terms(
                        user,
                        PurchaseOperation.START,
                        dealId,
                        quote.productId(),
                        quote.title(),
                        quote.coverImage(),
                        quote.amount(),
                        quote.currency(),
                        quote.targetSize());
        return PurchaseIntent.issue(
                terms,
                now.plusSeconds(PurchaseQuoteWindow.SECONDS),
                now.plusSeconds(quote.lifetimeSeconds()),
                now);
    }

    /**
     * JOIN: the business id is an open team, whose frozen price is the one the buyer pays. An
     * invite reference that names this team and another member freezes that member as the sharer
     * (UC7); any other reference is ignored rather than failing the purchase.
     *
     * <p>The sharer is resolved before the team lock is taken. The lookup reads only the immutable
     * invite row (Redis first, then PostgreSQL), needs nothing the lock protects, and never
     * rejects, so doing it first keeps its I/O out of the window in which concurrent JOINs queue on
     * the team row without changing which rejection a client sees. A JOIN that the lock then
     * refuses has paid one lookup it did not need.
     */
    private PurchaseIntent join(UUID user, UUID dealId, String inviteRef, Instant now) {
        UUID sharer = deals.resolveSharer(inviteRef, dealId, user).orElse(null);
        GroupDealPurchaseSupport.JoinTerms team = deals.lockJoinTerms(dealId, user);
        CatalogPurchaseSupport.PurchasableProduct product =
                products.requirePurchasable(team.productId());
        PurchaseIntent.Terms terms =
                new PurchaseIntent.Terms(
                        user,
                        PurchaseOperation.JOIN,
                        team.dealId(),
                        product.productId(),
                        product.title(),
                        product.coverImage(),
                        team.amount(),
                        team.currency(),
                        team.targetSize());
        // A quote must never outlive the team it buys into, so the team's own deadline caps it.
        Instant quoteValidUntil =
                earlier(now.plusSeconds(PurchaseQuoteWindow.SECONDS), team.expireTime());
        return PurchaseIntent.issue(terms, quoteValidUntil, team.expireTime(), now)
                .attributedTo(sharer);
    }

    private static Instant earlier(Instant one, Instant other) {
        return one.isBefore(other) ? one : other;
    }
}
