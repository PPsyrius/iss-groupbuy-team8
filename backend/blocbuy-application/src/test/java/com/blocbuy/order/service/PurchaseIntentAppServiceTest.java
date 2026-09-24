package com.blocbuy.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.service.CatalogPurchaseSupport;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.ErrorCode;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.service.GroupDealPurchaseSupport;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.order.exception.OrderErrorCode;
import com.blocbuy.order.model.PurchaseIntent;
import com.blocbuy.order.model.PurchaseOperation;
import com.blocbuy.order.repository.PurchaseIntentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/**
 * What the server commits to when a browser asks to start or join a team. Every price, size and
 * deadline in the issued intent must come from a collaborator, never from the request.
 */
class PurchaseIntentAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID DEAL = UUID.randomUUID();

    private final CatalogPurchaseSupport products = mock(CatalogPurchaseSupport.class);
    private final GroupDealPurchaseSupport deals = mock(GroupDealPurchaseSupport.class);
    private final UserAppService users = mock(UserAppService.class);
    private final PurchaseIntentRepository intents = mock(PurchaseIntentRepository.class);

    private final PurchaseIntentAppService service =
            new PurchaseIntentAppService(products, deals, users, intents);

    private void clockReads() {
        when(intents.currentTime()).thenReturn(NOW);
        when(intents.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private void productQuotes(int lifetimeSeconds) {
        when(products.lockStartQuote(PRODUCT, NOW))
                .thenReturn(
                        new CatalogPurchaseSupport.StartQuote(
                                PRODUCT,
                                "Headphones",
                                "https://cdn.example.com/cover.jpg",
                                new BigDecimal("12.50"),
                                "sgd",
                                3,
                                lifetimeSeconds));
    }

    private void teamQuotes(String amount, String currency, Instant expireTime) {
        when(deals.lockJoinTerms(DEAL, USER))
                .thenReturn(
                        new GroupDealPurchaseSupport.JoinTerms(
                                DEAL, PRODUCT, new BigDecimal(amount), currency, 4, expireTime));
        when(products.requirePurchasable(PRODUCT))
                .thenReturn(
                        new CatalogPurchaseSupport.PurchasableProduct(
                                PRODUCT, "Headphones", "https://cdn.example.com/cover.jpg", "sgd"));
    }

    private PurchaseIntent saved() {
        ArgumentCaptor<PurchaseIntent> captor = ArgumentCaptor.forClass(PurchaseIntent.class);
        verify(intents).save(captor.capture());
        return captor.getValue();
    }

    private static void assertRejected(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(thrown -> ((BizException) thrown).errorCode())
                .isEqualTo(expected);
    }

    @Test
    void aStartIsPricedByCatalogAndReservesTheTeamItWillOpen() {
        clockReads();
        productQuotes(7200);

        UUID id = service.createIntent(USER, "START", PRODUCT);

        PurchaseIntent intent = saved();
        assertThat(id).isEqualTo(intent.id());
        assertThat(intent.operation()).isEqualTo(PurchaseOperation.START);
        assertThat(intent.userId()).isEqualTo(USER);
        assertThat(intent.productId()).isEqualTo(PRODUCT);
        assertThat(intent.productTitle()).isEqualTo("Headphones");
        assertThat(intent.productImage()).isEqualTo("https://cdn.example.com/cover.jpg");
        assertThat(intent.amount()).isEqualByComparingTo("12.50");
        assertThat(intent.currency()).isEqualTo("sgd");
        assertThat(intent.targetSize()).isEqualTo(3);
        assertThat(intent.quoteValidUntil()).isEqualTo(NOW.plusSeconds(2400));
        assertThat(intent.businessDeadline()).isEqualTo(NOW.plusSeconds(7200));
        assertThat(intent.fulfilled()).isFalse();
        // The team does not exist yet, so its id is reserved rather than read.
        assertThat(intent.dealId()).isNotNull().isNotEqualTo(intent.id());
        verifyNoInteractions(deals);
    }

    @Test
    void everyStartReservesItsOwnTeamId() {
        clockReads();
        productQuotes(7200);

        service.createIntent(USER, "START", PRODUCT);
        service.createIntent(USER, "START", PRODUCT);

        ArgumentCaptor<PurchaseIntent> captor = ArgumentCaptor.forClass(PurchaseIntent.class);
        verify(intents, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).dealId())
                .isNotEqualTo(captor.getAllValues().get(1).dealId());
    }

    @Test
    void aJoinTakesItsPriceFromTheTeamAndItsProductFromCatalog() {
        clockReads();
        teamQuotes("9.90", "sgd", NOW.plusSeconds(86_400));

        UUID id = service.createIntent(USER, "JOIN", DEAL);

        PurchaseIntent intent = saved();
        assertThat(id).isEqualTo(intent.id());
        assertThat(intent.operation()).isEqualTo(PurchaseOperation.JOIN);
        assertThat(intent.dealId()).isEqualTo(DEAL);
        assertThat(intent.productId()).isEqualTo(PRODUCT);
        assertThat(intent.amount()).isEqualByComparingTo("9.90");
        assertThat(intent.targetSize()).isEqualTo(4);
        assertThat(intent.quoteValidUntil()).isEqualTo(NOW.plusSeconds(2400));
        assertThat(intent.businessDeadline()).isEqualTo(NOW.plusSeconds(86_400));
        verify(products, never()).lockStartQuote(any(), any());
    }

    @Test
    void aQuoteNeverOutlivesTheTeamItBuysInto() {
        clockReads();
        teamQuotes("9.90", "sgd", NOW.plusSeconds(600));

        service.createIntent(USER, "JOIN", DEAL);

        PurchaseIntent intent = saved();
        assertThat(intent.quoteValidUntil()).isEqualTo(NOW.plusSeconds(600));
        assertThat(intent.businessDeadline()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void theTeamDeadlineOnlyCapsTheQuoteWhenItIsTheEarlierOne() {
        clockReads();
        teamQuotes("9.90", "sgd", NOW.plusSeconds(2400));

        service.createIntent(USER, "JOIN", DEAL);

        // Equal instants: neither is before the other, so the window itself stands.
        assertThat(saved().quoteValidUntil()).isEqualTo(NOW.plusSeconds(2400));
    }

    @Test
    void aTeamPricedInAnotherCurrencyCannotBeQuoted() {
        clockReads();
        teamQuotes("9.90", "usd", NOW.plusSeconds(86_400));

        assertRejected(
                () -> service.createIntent(USER, "JOIN", DEAL),
                OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        verify(intents, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "start", "BUY", "REFUND"})
    void onlyStartAndJoinAreUnderstood(String operation) {
        assertRejected(
                () -> service.createIntent(USER, operation, PRODUCT),
                OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        verifyNoInteractions(users, products, deals, intents);
    }

    @Test
    void aMissingBuyerOrBusinessIdIsRejectedBeforeAnythingIsRead() {
        assertRejected(
                () -> service.createIntent(null, "START", PRODUCT),
                OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        assertRejected(
                () -> service.createIntent(USER, "START", null),
                OrderErrorCode.PURCHASE_INTENT_TERMS_INVALID);
        verifyNoInteractions(users, products, deals, intents);
    }

    @Test
    void anInactiveBuyerIsStoppedBeforeAnyLockIsTaken() {
        doThrow(new BizException(IdentityErrorCode.USER_NOT_FOUND)).when(users).requireActive(USER);

        assertRejected(
                () -> service.createIntent(USER, "START", PRODUCT),
                IdentityErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(products, deals);
        verify(intents, never()).save(any());
    }

    @Test
    void catalogsRefusalToPriceAStartIsNotSwallowed() {
        clockReads();
        when(products.lockStartQuote(PRODUCT, NOW))
                .thenThrow(new BizException(CatalogErrorCode.PRODUCT_NOT_STARTABLE));

        assertRejected(
                () -> service.createIntent(USER, "START", PRODUCT),
                CatalogErrorCode.PRODUCT_NOT_STARTABLE);
        verify(intents, never()).save(any());
    }

    @Test
    void groupbuysRefusalToAdmitAJoinIsNotSwallowed() {
        clockReads();
        when(deals.lockJoinTerms(DEAL, USER))
                .thenThrow(new BizException(GroupbuyErrorCode.DEAL_FULL));

        assertRejected(() -> service.createIntent(USER, "JOIN", DEAL), GroupbuyErrorCode.DEAL_FULL);
        verifyNoInteractions(products);
        verify(intents, never()).save(any());
    }

    @Test
    void anUnsellableProductStopsAJoinAfterTheTeamIsAccepted() {
        clockReads();
        teamQuotes("9.90", "sgd", NOW.plusSeconds(86_400));
        when(products.requirePurchasable(PRODUCT))
                .thenThrow(new BizException(CatalogErrorCode.PRODUCT_NOT_PURCHASABLE));

        assertRejected(
                () -> service.createIntent(USER, "JOIN", DEAL),
                CatalogErrorCode.PRODUCT_NOT_PURCHASABLE);
        verify(intents, never()).save(any());
    }

    @Test
    void theBuyerIsCheckedBeforeTheClockAndTheTeamBeforeTheProduct() {
        clockReads();
        teamQuotes("9.90", "sgd", NOW.plusSeconds(86_400));

        service.createIntent(USER, "JOIN", DEAL);

        // Lock order is group deal → intent; catalog's read only happens once the team is held.
        var order = inOrder(users, intents, deals, products);
        order.verify(users).requireActive(USER);
        order.verify(intents).currentTime();
        order.verify(deals).lockJoinTerms(DEAL, USER);
        order.verify(products).requirePurchasable(PRODUCT);
        order.verify(intents).save(any());
    }

    /** UC7: a JOIN that presents an attributing invite freezes its sharer on the intent. */
    @Test
    void aJoinThroughAnInviteFreezesTheSharerTheTeamResolved() {
        clockReads();
        teamQuotes("9.90", "sgd", NOW.plusSeconds(86_400));
        UUID sharer = UUID.randomUUID();
        when(deals.resolveSharer("ref-1", DEAL, USER)).thenReturn(Optional.of(sharer));

        service.createIntent(USER, "JOIN", DEAL, "ref-1");

        assertThat(saved().sharerUserId()).isEqualTo(sharer);
        var order = inOrder(deals);
        order.verify(deals).lockJoinTerms(DEAL, USER);
        order.verify(deals).resolveSharer("ref-1", DEAL, USER);
    }

    /** A reference groupbuy cannot attribute leaves the JOIN unattributed, never failed. */
    @Test
    void aJoinWhoseInviteDoesNotResolveStaysUnattributed() {
        clockReads();
        teamQuotes("9.90", "sgd", NOW.plusSeconds(86_400));
        when(deals.resolveSharer("stale", DEAL, USER)).thenReturn(Optional.empty());

        service.createIntent(USER, "JOIN", DEAL, "stale");

        assertThat(saved().sharerUserId()).isNull();
    }

    /** A buyer starting their own team may still hold an invite; it is ignored, not resolved. */
    @Test
    void aStartIgnoresAnyInviteReference() {
        clockReads();
        productQuotes(86_400);

        service.createIntent(USER, "START", PRODUCT, "ref-1");

        assertThat(saved().sharerUserId()).isNull();
        verify(deals, never()).resolveSharer(any(), any(), any());
    }
}
