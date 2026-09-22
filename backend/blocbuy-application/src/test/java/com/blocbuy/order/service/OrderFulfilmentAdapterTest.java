package com.blocbuy.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.service.CatalogPurchaseSupport;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.ErrorCode;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.model.NewDealTerms;
import com.blocbuy.groupbuy.model.ParticipationResult;
import com.blocbuy.groupbuy.service.GroupDealPurchaseSupport;
import com.blocbuy.groupbuy.service.GroupDealSettlementSupport;
import com.blocbuy.groupbuy.service.PaidGroupDealService;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.order.exception.OrderErrorCode;
import com.blocbuy.order.model.Order;
import com.blocbuy.order.model.OrderStatus;
import com.blocbuy.order.model.PurchaseIntent;
import com.blocbuy.order.model.PurchaseOperation;
import com.blocbuy.order.repository.OrderRepository;
import com.blocbuy.order.repository.PurchaseIntentRepository;
import com.blocbuy.payment.event.PaymentSucceeded;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.model.CheckoutQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * What the order module does with money that has already moved. The line that matters in every
 * case: a rejection may only be returned before the first write, and anything that fails after is a
 * throw that rolls the whole settlement back.
 */
class OrderFulfilmentAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-09-22T10:05:00Z");
    private static final UUID INTENT = UUID.randomUUID();
    private static final UUID DEAL = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PAYMENT = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("12.50");

    private final PurchaseIntentRepository intents = mock(PurchaseIntentRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final GroupDealPurchaseSupport teams = mock(GroupDealPurchaseSupport.class);
    private final GroupDealSettlementSupport settlement = mock(GroupDealSettlementSupport.class);
    private final PaidGroupDealService groups = mock(PaidGroupDealService.class);
    private final CatalogPurchaseSupport products = mock(CatalogPurchaseSupport.class);
    private final UserAppService users = mock(UserAppService.class);

    private final OrderFulfilmentAdapter adapter =
            new OrderFulfilmentAdapter(intents, orders, teams, settlement, groups, products, users);

    // ---- fixtures ----------------------------------------------------------------------------

    private static PurchaseIntent intent(PurchaseOperation operation, UUID fulfilledPayment) {
        return PurchaseIntent.reconstitute(
                INTENT,
                new PurchaseIntent.Terms(
                        USER,
                        operation,
                        DEAL,
                        PRODUCT,
                        "Headphones",
                        "https://cdn.example.com/cover.jpg",
                        AMOUNT,
                        "sgd",
                        3),
                NOW.plusSeconds(2400),
                NOW.plusSeconds(7200),
                fulfilledPayment,
                NOW,
                fulfilledPayment == null ? 0 : 1);
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

    private static PaymentSucceeded event(PurchaseIntent intent) {
        return new PaymentSucceeded(PAYMENT, quoteOf(intent), PAID_AT);
    }

    private static Order storedOrder(
            UUID buyer, UUID deal, BigDecimal amount, String currency, OrderStatus status) {
        return Order.reconstitute(
                new Order.NewOrderTerms(
                        "BB20260922ABCDEF",
                        PAYMENT,
                        deal,
                        buyer,
                        PRODUCT,
                        "Headphones",
                        null,
                        1,
                        amount,
                        currency),
                ORDER,
                amount,
                new Order.Lifecycle(status, PAID_AT, null, PAID_AT, 0));
    }

    private static Order storedOrder(BigDecimal amount, String currency, OrderStatus status) {
        return storedOrder(USER, DEAL, amount, currency, status);
    }

    private static Order storedOrder() {
        return storedOrder(AMOUNT, "sgd", OrderStatus.PENDING);
    }

    private static ParticipationResult applied() {
        return new ParticipationResult("applied", DEAL, "open", 1, null);
    }

    private PurchaseIntent locked(PurchaseOperation operation, UUID fulfilledPayment) {
        PurchaseIntent stored = intent(operation, fulfilledPayment);
        when(intents.lockById(INTENT)).thenReturn(Optional.of(stored));
        return stored;
    }

    private void settlementSucceeds() {
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
        when(settlement.linkOrder(any(), any(), any())).thenReturn(true);
        when(products.consumeOneUnit(PRODUCT)).thenReturn(true);
    }

    private static void assertRejected(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(thrown -> ((BizException) thrown).errorCode())
                .isEqualTo(expected);
    }

    private void assertNothingWasWritten() {
        verify(orders, never()).save(any());
        verify(intents, never()).markFulfilled(any(), any());
        verify(products, never()).consumeOneUnit(any());
        verifyNoInteractions(groups);
    }

    // ---- lockQuote ---------------------------------------------------------------------------

    @Test
    void aJoinQuoteIsRevalidatedAgainstTheTeamBeforeItIsHandedBack() {
        PurchaseIntent stored = intent(PurchaseOperation.JOIN, null);
        when(intents.findById(INTENT)).thenReturn(Optional.of(stored));
        when(intents.lockById(INTENT)).thenReturn(Optional.of(stored));

        assertThat(adapter.lockQuote(USER, INTENT)).isEqualTo(quoteOf(stored));

        var order = inOrder(teams, intents);
        order.verify(teams).lockJoinTerms(DEAL, USER);
        order.verify(intents).lockById(INTENT);
    }

    @Test
    void aStartQuoteHasNoTeamToRevalidate() {
        PurchaseIntent stored = intent(PurchaseOperation.START, null);
        when(intents.findById(INTENT)).thenReturn(Optional.of(stored));
        when(intents.lockById(INTENT)).thenReturn(Optional.of(stored));

        assertThat(adapter.lockQuote(USER, INTENT).operation()).isEqualTo("START");
        verifyNoInteractions(teams);
    }

    @Test
    void anUnknownIntentIsAMiss() {
        when(intents.findById(INTENT)).thenReturn(Optional.empty());

        assertRejected(
                () -> adapter.lockQuote(USER, INTENT), OrderErrorCode.PURCHASE_INTENT_NOT_FOUND);
    }

    @Test
    void anotherBuyersIntentIsAMissRatherThanAForbidden() {
        PurchaseIntent stored = intent(PurchaseOperation.START, null);
        when(intents.findById(INTENT)).thenReturn(Optional.of(stored));
        when(intents.lockById(INTENT)).thenReturn(Optional.of(stored));

        assertRejected(
                () -> adapter.lockQuote(UUID.randomUUID(), INTENT),
                OrderErrorCode.PURCHASE_INTENT_NOT_FOUND);
    }

    @Test
    void aSpentIntentCannotBackASecondCheckout() {
        PurchaseIntent stored = intent(PurchaseOperation.START, PAYMENT);
        when(intents.findById(INTENT)).thenReturn(Optional.of(stored));
        when(intents.lockById(INTENT)).thenReturn(Optional.of(stored));

        assertRejected(
                () -> adapter.lockQuote(USER, INTENT),
                OrderErrorCode.PURCHASE_INTENT_ALREADY_FULFILLED);
    }

    @Test
    void anIntentThatVanishesBetweenTheTwoReadsIsATechnicalFault() {
        when(intents.findById(INTENT))
                .thenReturn(Optional.of(intent(PurchaseOperation.START, null)));
        when(intents.lockById(INTENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.lockQuote(USER, INTENT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTeamThatFilledUpSinceTheQuoteRefusesTheCheckout() {
        when(intents.findById(INTENT))
                .thenReturn(Optional.of(intent(PurchaseOperation.JOIN, null)));
        when(teams.lockJoinTerms(DEAL, USER))
                .thenThrow(new BizException(GroupbuyErrorCode.DEAL_FULL));

        assertRejected(() -> adapter.lockQuote(USER, INTENT), GroupbuyErrorCode.DEAL_FULL);
        verify(intents, never()).lockById(any());
    }

    // ---- lockForSettlement -------------------------------------------------------------------

    @Test
    void settlementLocksTheTeamBeforeTheIntentAndAcceptsAnUnchangedQuote() {
        PurchaseIntent stored = locked(PurchaseOperation.JOIN, null);

        adapter.lockForSettlement(quoteOf(stored));

        var order = inOrder(settlement, intents);
        order.verify(settlement).lockTeam(DEAL);
        order.verify(intents).lockById(INTENT);
    }

    @Test
    void anIntentThatNoLongerMatchesItsQuoteStopsTheSettlement() {
        CheckoutQuote issued = quoteOf(intent(PurchaseOperation.JOIN, null));
        locked(PurchaseOperation.START, null); // same ids, different operation

        assertThatThrownBy(() -> adapter.lockForSettlement(issued))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- fulfil ------------------------------------------------------------------------------

    @Test
    void aStartCreatesTheTeamTheIntentReservedAndAPendingOrder() {
        PurchaseIntent stored = locked(PurchaseOperation.START, null);
        when(groups.startConfirmed(any(), any())).thenReturn(applied());
        settlementSucceeds();

        PaymentBusinessPort.Outcome outcome = adapter.fulfil(event(stored));

        ArgumentCaptor<NewDealTerms> terms = ArgumentCaptor.forClass(NewDealTerms.class);
        verify(groups).startConfirmed(terms.capture(), any());
        assertThat(terms.getValue().id()).isEqualTo(DEAL);
        assertThat(terms.getValue().productId()).isEqualTo(PRODUCT);
        assertThat(terms.getValue().targetSize()).isEqualTo(3);
        assertThat(terms.getValue().expireTime()).isEqualTo(NOW.plusSeconds(7200));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(saved.capture());
        Order order = saved.getValue();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.paymentId()).isEqualTo(PAYMENT);
        assertThat(order.dealId()).isEqualTo(DEAL);
        assertThat(order.userId()).isEqualTo(USER);
        assertThat(order.quantity()).isEqualTo(1);
        assertThat(order.totalAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(order.productTitle()).isEqualTo("Headphones");
        assertThat(order.orderNo()).startsWith("BB20260922");

        assertThat(outcome.accepted()).isTrue();
        assertThat(outcome.dealId()).isEqualTo(DEAL);
        assertThat(outcome.orderId()).isEqualTo(order.id());
        verify(intents).markFulfilled(stored, PAYMENT);
        verify(products).consumeOneUnit(PRODUCT);
    }

    @Test
    void aJoinEntersTheExistingTeamInsteadOfCreatingOne() {
        PurchaseIntent stored = locked(PurchaseOperation.JOIN, null);
        when(groups.joinConfirmed(eq(DEAL), any())).thenReturn(applied());
        settlementSucceeds();

        assertThat(adapter.fulfil(event(stored)).accepted()).isTrue();

        verify(groups).joinConfirmed(eq(DEAL), any());
        verify(groups, never()).startConfirmed(any(), any());
    }

    @Test
    void theTeamIsLockedBeforeTheIntent() {
        PurchaseIntent stored = locked(PurchaseOperation.JOIN, null);
        when(groups.joinConfirmed(eq(DEAL), any())).thenReturn(applied());
        settlementSucceeds();

        adapter.fulfil(event(stored));

        var order = inOrder(settlement, intents);
        order.verify(settlement).lockTeam(DEAL);
        order.verify(intents).lockById(INTENT);
    }

    @Test
    void theSamePaymentAskingTwiceGetsTheOriginalAnswerAndWritesNothing() {
        PurchaseIntent stored = locked(PurchaseOperation.JOIN, PAYMENT);
        when(orders.findByPaymentId(PAYMENT)).thenReturn(Optional.of(storedOrder()));

        PaymentBusinessPort.Outcome outcome = adapter.fulfil(event(stored));

        assertThat(outcome.accepted()).isTrue();
        assertThat(outcome.dealId()).isEqualTo(DEAL);
        assertThat(outcome.orderId()).isEqualTo(ORDER);
        assertNothingWasWritten();
    }

    @Test
    void aFulfilledIntentWithNoOrderBehindItIsATechnicalFault() {
        PurchaseIntent stored = locked(PurchaseOperation.JOIN, PAYMENT);
        when(orders.findByPaymentId(PAYMENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.fulfil(event(stored)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aSecondPaymentForTheSameIntentIsRejectedNotApplied() {
        PurchaseIntent stored = locked(PurchaseOperation.JOIN, UUID.randomUUID());

        PaymentBusinessPort.Outcome outcome = adapter.fulfil(event(stored));

        assertThat(outcome.rejection()).isEqualTo("INTENT_ALREADY_FULFILLED");
        assertThat(outcome.dealId()).isNull();
        assertNothingWasWritten();
    }

    @Test
    void anInactiveBuyerIsRefusedBeforeAnythingIsWritten() {
        PurchaseIntent stored = locked(PurchaseOperation.JOIN, null);
        doThrow(new BizException(IdentityErrorCode.USER_NOT_FOUND)).when(users).requireActive(USER);

        assertThat(adapter.fulfil(event(stored)).rejection()).isEqualTo("BUYER_INACTIVE");
        assertNothingWasWritten();
    }

    @Test
    void anUnsellableProductIsRefusedBeforeAnythingIsWritten() {
        PurchaseIntent stored = locked(PurchaseOperation.JOIN, null);
        doThrow(new BizException(CatalogErrorCode.PRODUCT_NOT_PURCHASABLE))
                .when(products)
                .requirePurchasable(PRODUCT);

        assertThat(adapter.fulfil(event(stored)).rejection()).isEqualTo("PRODUCT_UNAVAILABLE");
        assertNothingWasWritten();
    }

    @Test
    void aTeamThatRefusesTheAdmissionLeavesNoOrderBehind() {
        PurchaseIntent stored = locked(PurchaseOperation.JOIN, null);
        when(groups.joinConfirmed(eq(DEAL), any()))
                .thenReturn(ParticipationResult.compensation(DEAL, "GROUPBUY_409_004"));

        PaymentBusinessPort.Outcome outcome = adapter.fulfil(event(stored));

        assertThat(outcome.rejection()).isEqualTo("GROUP_REJECTED");
        assertThat(outcome.dealId()).isEqualTo(DEAL);
        verify(orders, never()).save(any());
        verify(intents, never()).markFulfilled(any(), any());
        verify(products, never()).consumeOneUnit(any());
    }

    @Test
    void anAdmittedPaymentWithoutAMemberRowRollsTheSettlementBack() {
        PurchaseIntent stored = locked(PurchaseOperation.JOIN, null);
        when(groups.joinConfirmed(eq(DEAL), any())).thenReturn(applied());
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
        when(settlement.linkOrder(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> adapter.fulfil(event(stored)))
                .isInstanceOf(IllegalStateException.class);
        verify(products, never()).consumeOneUnit(any());
    }

    @Test
    void losingTheLastUnitAfterAdmissionRollsTheSettlementBackRatherThanRejecting() {
        // A rejection here would commit the membership and the order next to a refund; stock is
        // funds-safety class, so the whole settlement is thrown away and the webhook retries.
        PurchaseIntent stored = locked(PurchaseOperation.JOIN, null);
        when(groups.joinConfirmed(eq(DEAL), any())).thenReturn(applied());
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
        when(settlement.linkOrder(any(), any(), any())).thenReturn(true);
        when(products.consumeOneUnit(PRODUCT)).thenReturn(false);

        assertThatThrownBy(() -> adapter.fulfil(event(stored)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aPaymentCarryingSomeoneElsesQuoteIsATechnicalFault() {
        locked(PurchaseOperation.JOIN, null);
        CheckoutQuote tampered =
                new CheckoutQuote(
                        INTENT,
                        USER,
                        "JOIN",
                        DEAL,
                        new BigDecimal("0.01"),
                        "sgd",
                        NOW.plusSeconds(2400),
                        NOW.plusSeconds(7200));

        assertThatThrownBy(() -> adapter.fulfil(new PaymentSucceeded(PAYMENT, tampered, PAID_AT)))
                .isInstanceOf(IllegalStateException.class);
        assertNothingWasWritten();
    }

    // ---- fulfilmentEvidence ------------------------------------------------------------------

    private void evidenceRows(Order order, GroupDealSettlementSupport.SettledMember member) {
        when(orders.findByPaymentId(PAYMENT)).thenReturn(Optional.ofNullable(order));
        when(settlement.settledMember(PAYMENT)).thenReturn(Optional.ofNullable(member));
    }

    private static GroupDealSettlementSupport.SettledMember member(UUID orderId, String status) {
        return new GroupDealSettlementSupport.SettledMember(DEAL, USER, orderId, status);
    }

    private static GroupDealSettlementSupport.SettledMember member(UUID deal, UUID buyer) {
        return new GroupDealSettlementSupport.SettledMember(deal, buyer, ORDER, "paid");
    }

    private PaymentBusinessPort.FulfilmentEvidence evidence() {
        return adapter.fulfilmentEvidence(event(intent(PurchaseOperation.JOIN, null)));
    }

    private void intentSpentOnThisPayment() {
        when(intents.findById(INTENT))
                .thenReturn(Optional.of(intent(PurchaseOperation.JOIN, PAYMENT)));
    }

    @Test
    void noRowsAtAllMeansTheFulfilmentNeverHappened() {
        evidenceRows(null, null);

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.NONE);
    }

    @Test
    void anOrderWithoutAMembershipIsHalfDone() {
        evidenceRows(storedOrder(), null);

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.INCONSISTENT);
    }

    @Test
    void aMembershipWithoutAnOrderIsHalfDone() {
        evidenceRows(null, member(ORDER, "paid"));

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.INCONSISTENT);
    }

    @Test
    void matchingRowsOnBothSidesAreCompleteEvidence() {
        evidenceRows(storedOrder(), member(ORDER, "paid"));
        intentSpentOnThisPayment();

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.COMPLETE);
    }

    @Test
    void aConfirmedOrderIsStillEvidenceOfItsOwnFulfilment() {
        // The team succeeded after settlement; the lifecycle move does not unmake the fulfilment.
        evidenceRows(storedOrder(AMOUNT, "sgd", OrderStatus.CONFIRMED), member(ORDER, "paid"));
        intentSpentOnThisPayment();

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.COMPLETE);
    }

    @Test
    void aMemberPointingAtAnotherOrderIsInconsistent() {
        evidenceRows(storedOrder(), member(UUID.randomUUID(), "paid"));

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.INCONSISTENT);
    }

    @Test
    void anOrderBelongingToAnotherBuyerIsInconsistent() {
        evidenceRows(
                storedOrder(UUID.randomUUID(), DEAL, AMOUNT, "sgd", OrderStatus.PENDING),
                member(ORDER, "paid"));

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.INCONSISTENT);
    }

    @Test
    void aMembershipHeldByAnotherBuyerIsInconsistent() {
        evidenceRows(storedOrder(), member(DEAL, UUID.randomUUID()));

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.INCONSISTENT);
    }

    @Test
    void aMembershipOnAnotherTeamIsInconsistent() {
        evidenceRows(storedOrder(), member(UUID.randomUUID(), USER));

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.INCONSISTENT);
    }

    @Test
    void rowsThatAgreeWithEachOtherButNotWithTheQuotedTeamAreInconsistent() {
        UUID elsewhere = UUID.randomUUID();
        evidenceRows(
                storedOrder(USER, elsewhere, AMOUNT, "sgd", OrderStatus.PENDING),
                member(elsewhere, USER));

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.INCONSISTENT);
    }

    @Test
    void anOrderChargedInAnotherCurrencyIsInconsistent() {
        evidenceRows(storedOrder(AMOUNT, "usd", OrderStatus.PENDING), member(ORDER, "paid"));

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.INCONSISTENT);
    }

    @Test
    void aMemberThatIsNotPaidIsInconsistent() {
        evidenceRows(storedOrder(), member(ORDER, "refunded"));

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.INCONSISTENT);
    }

    @Test
    void anOrderChargedADifferentAmountIsInconsistent() {
        evidenceRows(
                storedOrder(new BigDecimal("9.90"), "sgd", OrderStatus.PENDING),
                member(ORDER, "paid"));

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.INCONSISTENT);
    }

    @Test
    void anIntentNotSpentOnThisPaymentIsInconsistent() {
        evidenceRows(storedOrder(), member(ORDER, "paid"));
        when(intents.findById(INTENT))
                .thenReturn(Optional.of(intent(PurchaseOperation.JOIN, UUID.randomUUID())));

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.INCONSISTENT);
    }

    @Test
    void aVanishedIntentIsInconsistent() {
        evidenceRows(storedOrder(), member(ORDER, "paid"));
        when(intents.findById(INTENT)).thenReturn(Optional.empty());

        assertThat(evidence()).isEqualTo(PaymentBusinessPort.FulfilmentEvidence.INCONSISTENT);
    }

    // ---- lockExpiredTeam ---------------------------------------------------------------------

    @Test
    void sweepingAnExpiredTeamGoesThroughGroupbuy() {
        adapter.lockExpiredTeam(DEAL);

        verify(settlement).lockExpiredTeam(DEAL);
    }
}
