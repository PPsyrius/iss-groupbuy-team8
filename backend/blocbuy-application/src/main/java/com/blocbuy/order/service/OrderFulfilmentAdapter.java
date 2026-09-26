package com.blocbuy.order.service;

import com.blocbuy.catalog.service.CatalogPurchaseSupport;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.model.NewDealTerms;
import com.blocbuy.groupbuy.model.PaidParticipation;
import com.blocbuy.groupbuy.model.ParticipationResult;
import com.blocbuy.groupbuy.service.GroupDealPurchaseSupport;
import com.blocbuy.groupbuy.service.GroupDealSettlementSupport;
import com.blocbuy.groupbuy.service.PaidGroupDealService;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.order.exception.OrderErrorCode;
import com.blocbuy.order.model.Order;
import com.blocbuy.order.model.PurchaseIntent;
import com.blocbuy.order.model.PurchaseOperation;
import com.blocbuy.order.repository.OrderRepository;
import com.blocbuy.order.repository.PurchaseIntentRepository;
import com.blocbuy.payment.event.PaymentSucceeded;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.model.CheckoutQuote;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The business half of the checkout — the production implementation of {@link PaymentBusinessPort},
 * whose own Javadoc still says "No production stub is supplied". The payment slice owns the money;
 * this class owns what the money buys: a team membership, an order, and the consumption of the
 * purchase intent that priced them.
 *
 * <p><b>Lock order is fixed and global: group deal → purchase intent → payment.</b> Every method
 * here takes the team lock before the intent lock, and the payment slice takes its own lock after
 * calling in. Any other order deadlocks against a concurrent JOIN on the same team.
 *
 * <p><b>Rejection versus failure.</b> An {@link Outcome} carrying a rejection is a business refusal
 * of money that has already moved: the caller commits it and arranges a refund. So every check that
 * can reject runs <em>before</em> the first write — a rejection returned after a write would commit
 * that write alongside the refund. Anything that goes wrong after the writes begin is technical and
 * throws, rolling the whole transaction back.
 *
 * <p>Collaborators are the other domains' public application facades. This class never touches
 * {@code biz_product}, {@code biz_group_deal} or {@code biz_group_member} itself.
 */
@RequiredArgsConstructor
public class OrderFulfilmentAdapter implements PaymentBusinessPort {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT).withZone(ZoneOffset.UTC);

    private static final String MEMBER_PAID = "paid";

    private final PurchaseIntentRepository intents;
    private final OrderRepository orders;
    private final GroupDealPurchaseSupport teams;
    private final GroupDealSettlementSupport settlement;
    private final PaidGroupDealService groups;
    private final CatalogPurchaseSupport products;
    private final UserAppService users;

    /**
     * Re-validates the buyer's claim on an intent and hands back the frozen quote. JOIN re-checks
     * admission under the team lock, because a quote issued minutes ago may be for a team that has
     * since filled up — cheaper to refuse here than to refund later.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CheckoutQuote lockQuote(UUID authenticatedUser, UUID intentId) {
        PurchaseIntent preliminary = readIntent(intentId);
        if (preliminary.operation() == PurchaseOperation.JOIN) {
            teams.lockJoinTerms(preliminary.dealId(), authenticatedUser);
        }
        PurchaseIntent stored = lockIntent(intentId);
        stored.requireOwner(authenticatedUser);
        if (stored.fulfilled()) {
            throw new BizException(OrderErrorCode.PURCHASE_INTENT_ALREADY_FULFILLED);
        }
        return quoteOf(stored);
    }

    /**
     * Re-reads the intent under the same locks and proves it still says what the payment was bound
     * to. A difference is not a business rejection: the intent is immutable once issued, so a
     * changed one means the row was tampered with or the wrong quote was carried, and the caller
     * must not go on to bind a provider session to it.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForSettlement(CheckoutQuote quote) {
        settlement.lockTeam(quote.businessId());
        PurchaseIntent stored = lockIntent(quote.intentId());
        if (!quote.equals(quoteOf(stored))) {
            throw new IllegalStateException("Immutable purchase intent changed");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome fulfil(PaymentSucceeded event) {
        CheckoutQuote quote = event.quote();
        settlement.lockTeam(quote.businessId());
        PurchaseIntent intent = lockIntent(quote.intentId());
        requireQuoteMatchesIntent(quote, intent);

        if (intent.fulfilled()) {
            return replayOf(event, intent);
        }
        if (rejects(() -> users.requireActive(quote.userId()))) {
            return new Outcome(null, null, "BUYER_INACTIVE");
        }
        if (rejects(() -> products.requirePurchasable(intent.productId()))) {
            return new Outcome(null, null, "PRODUCT_UNAVAILABLE");
        }

        // Writes start here; from this point a failure throws rather than rejects.
        ParticipationResult admission = admit(event, intent);
        if (!admission.applied()) {
            return new Outcome(intent.dealId(), null, "GROUP_REJECTED");
        }
        Order order = orders.save(orderFor(event, intent));
        if (!settlement.linkOrder(intent.dealId(), event.paymentId(), order.id())) {
            throw new IllegalStateException(
                    "Admitted payment has no member row to carry its order");
        }
        intents.markFulfilled(intent, event.paymentId());
        if (!products.consumeOneUnit(intent.productId())) {
            // The last unit went to a concurrent settlement on another team between the check above
            // and here. Stock is funds-safety class: roll the whole fulfilment back and let the
            // webhook retry, where the purchasability check refuses it cleanly instead.
            throw new IllegalStateException("Stock disappeared during settlement");
        }
        return new Outcome(intent.dealId(), order.id(), null);
    }

    /**
     * Proves fulfilment from the rows it would have created, never from a payment result flag. The
     * order's own status is deliberately not checked: it is created PENDING and later moves to
     * CONFIRMED or CANCELLED with the team's lifecycle, none of which unmakes the fulfilment.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public FulfilmentEvidence fulfilmentEvidence(PaymentSucceeded event) {
        Optional<Order> order = orders.findByPaymentId(event.paymentId());
        Optional<GroupDealSettlementSupport.SettledMember> member =
                settlement.settledMember(event.paymentId());
        if (order.isEmpty() && member.isEmpty()) {
            return FulfilmentEvidence.NONE;
        }
        if (order.isEmpty() || member.isEmpty()) {
            return FulfilmentEvidence.INCONSISTENT;
        }
        return consistent(event.quote(), order.orElseThrow(), member.orElseThrow())
                ? FulfilmentEvidence.COMPLETE
                : FulfilmentEvidence.INCONSISTENT;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockExpiredTeam(UUID dealId) {
        settlement.lockExpiredTeam(dealId);
    }

    /** The same payment asking again; a different one finds the intent already spent. */
    private Outcome replayOf(PaymentSucceeded event, PurchaseIntent intent) {
        if (!event.paymentId().equals(intent.fulfilledPaymentId())) {
            return new Outcome(null, null, "INTENT_ALREADY_FULFILLED");
        }
        UUID orderId =
                orders.findByPaymentId(event.paymentId())
                        .map(Order::id)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Fulfilled intent without its order"));
        return new Outcome(intent.dealId(), orderId, null);
    }

    private ParticipationResult admit(PaymentSucceeded event, PurchaseIntent intent) {
        CheckoutQuote quote = event.quote();
        PaidParticipation paid =
                new PaidParticipation(
                        event.paymentId(), quote.userId(), quote.amount(), quote.currency());
        if (intent.operation() == PurchaseOperation.START) {
            NewDealTerms terms =
                    new NewDealTerms(
                            intent.dealId(),
                            businessNumber("GB", intent.dealId(), event.confirmedAt()),
                            intent.productId(),
                            quote.amount(),
                            quote.currency(),
                            intent.targetSize(),
                            quote.businessDeadline());
            return groups.startConfirmed(terms, paid);
        }
        return groups.joinConfirmed(intent.dealId(), paid, intent.sharerUserId());
    }

    private Order orderFor(PaymentSucceeded event, PurchaseIntent intent) {
        CheckoutQuote quote = event.quote();
        Order.NewOrderTerms terms =
                new Order.NewOrderTerms(
                        businessNumber("BB", event.paymentId(), event.confirmedAt()),
                        event.paymentId(),
                        intent.dealId(),
                        quote.userId(),
                        intent.productId(),
                        intent.productTitle(),
                        intent.productImage(),
                        1,
                        quote.amount(),
                        quote.currency());
        return Order.create(terms, event.confirmedAt());
    }

    private boolean consistent(
            CheckoutQuote quote, Order order, GroupDealSettlementSupport.SettledMember member) {
        return order.id().equals(member.orderId())
                && quote.userId().equals(order.userId())
                && order.userId().equals(member.userId())
                && order.dealId().equals(member.dealId())
                && quote.businessId().equals(order.dealId())
                && quote.amount().compareTo(order.totalAmount()) == 0
                && quote.currency().equals(order.currency())
                && MEMBER_PAID.equals(member.status())
                && intentSpentOn(quote, order);
    }

    /** The intent must name this order's team and record the payment that consumed it. */
    private boolean intentSpentOn(CheckoutQuote quote, Order order) {
        return intents.findById(quote.intentId())
                .filter(intent -> order.paymentId().equals(intent.fulfilledPaymentId()))
                .filter(intent -> order.dealId().equals(intent.dealId()))
                .isPresent();
    }

    /**
     * The quote is minted from the intent, so a mismatch here is not a buyer error — it means the
     * payment is carrying a quote that never belonged to this intent.
     */
    private static void requireQuoteMatchesIntent(CheckoutQuote quote, PurchaseIntent intent) {
        if (!quote.equals(quoteOf(intent))) {
            throw new IllegalStateException("Payment quote does not match its purchase intent");
        }
    }

    private PurchaseIntent readIntent(UUID intentId) {
        return intents.findById(intentId)
                .orElseThrow(() -> new BizException(OrderErrorCode.PURCHASE_INTENT_NOT_FOUND));
    }

    /**
     * Taken after the team lock. A row that vanished between the two reads is a technical fault.
     */
    private PurchaseIntent lockIntent(UUID intentId) {
        return intents.lockById(intentId)
                .orElseThrow(() -> new IllegalStateException("Purchase intent row disappeared"));
    }

    private static CheckoutQuote quoteOf(PurchaseIntent intent) {
        return new CheckoutQuote(
                intent.id(),
                intent.userId(),
                intent.operation().code(),
                intent.dealId(),
                intent.amount(),
                intent.currency(),
                intent.quoteValidUntil(),
                intent.businessDeadline());
    }

    /** Runs a collaborator's guard and reports whether it refused on business grounds. */
    private static boolean rejects(Runnable guard) {
        try {
            guard.run();
            return false;
        } catch (BizException refused) {
            return true;
        }
    }

    /**
     * Business-facing number: prefix, UTC day, and the random tail of a UUIDv7 that is already
     * unique per payment or team. Deriving it rather than drawing a sequence keeps a replayed
     * fulfilment from inventing a second number for the same fact.
     */
    private static String businessNumber(String prefix, UUID id, Instant when) {
        String tail = Long.toHexString(id.getLeastSignificantBits());
        return prefix
                + DAY.format(when)
                + tail.substring(Math.max(0, tail.length() - 12)).toUpperCase(Locale.ROOT);
    }
}
