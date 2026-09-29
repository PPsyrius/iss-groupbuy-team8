package com.blocbuy.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.model.ProductTeamTerms;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.catalog.repository.TeamPriceRepository;
import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;

/** What catalog will and will not let a checkout be priced against. */
class CatalogPurchaseSupportImplTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    private static final UUID PRODUCT = UUID.randomUUID();

    private final ProductRepository products = mock(ProductRepository.class);
    private final TeamPriceRepository teamPrices = mock(TeamPriceRepository.class);
    private final CatalogPurchaseSupportImpl support =
            new CatalogPurchaseSupportImpl(products, teamPrices);

    private static Product product(ProductStatus status, int stock, String currency) {
        return Product.reconstitute(
                PRODUCT,
                "DEMO-P-0001",
                "Headphones",
                "subtitle",
                "description",
                "https://cdn.example.com/cover.jpg",
                List.of("https://cdn.example.com/1.jpg"),
                "electronics",
                new BigDecimal("100.00"),
                currency,
                stock,
                3,
                status,
                PricingMode.DUAL);
    }

    private static Product onSale() {
        return product(ProductStatus.ON_SALE, 10, "sgd");
    }

    private static TeamPricePolicy policy(String amount, Instant from, Instant until) {
        return new TeamPricePolicy(PRODUCT, new BigDecimal(amount), "sgd", from, until, 1);
    }

    private void given(Product product, ProductTeamTerms terms, TeamPricePolicy policy) {
        when(products.lockById(PRODUCT)).thenReturn(Optional.ofNullable(product));
        when(products.findTeamTerms(PRODUCT)).thenReturn(Optional.ofNullable(terms));
        when(teamPrices.findLatestPolicy(PRODUCT)).thenReturn(Optional.ofNullable(policy));
    }

    private static ProductTeamTerms terms(Integer targetSize, Integer lifetimeSeconds) {
        return new ProductTeamTerms(PRODUCT, targetSize, lifetimeSeconds);
    }

    @Test
    void aStartQuoteCarriesTheServerPriceAndTheProductsTeamTerms() {
        given(
                onSale(),
                terms(3, 7200),
                policy("40.00", NOW.minusSeconds(60), NOW.plusSeconds(9999)));

        CatalogPurchaseSupport.StartQuote quote = support.lockStartQuote(PRODUCT, NOW);

        assertThat(quote.productId()).isEqualTo(PRODUCT);
        assertThat(quote.title()).isEqualTo("Headphones");
        assertThat(quote.coverImage()).isEqualTo("https://cdn.example.com/cover.jpg");
        assertThat(quote.amount()).isEqualByComparingTo("40.00");
        assertThat(quote.currency()).isEqualTo("sgd");
        assertThat(quote.targetSize()).isEqualTo(3);
        assertThat(quote.lifetimeSeconds()).isEqualTo(7200);
    }

    @Test
    void aMissingProductOrMissingTermsRowIsNotFound() {
        given(null, terms(3, 7200), policy("40.00", NOW.minusSeconds(60), NOW.plusSeconds(9999)));
        assertThatThrownBy(() -> support.lockStartQuote(PRODUCT, NOW))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(CatalogErrorCode.PRODUCT_NOT_FOUND.defaultMessage());

        given(onSale(), null, policy("40.00", NOW.minusSeconds(60), NOW.plusSeconds(9999)));
        assertThatThrownBy(() -> support.lockStartQuote(PRODUCT, NOW))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(CatalogErrorCode.PRODUCT_NOT_FOUND.defaultMessage());
    }

    @ParameterizedTest
    @CsvSource({
        "DRAFT, 10, sgd",
        "OFF_SALE, 10, sgd",
        "ON_SALE, 0, sgd",
        "ON_SALE, 10, usd",
    })
    void anUnsellableProductIsNeverQuoted(String status, int stock, String currency) {
        given(
                product(ProductStatus.valueOf(status), stock, currency),
                terms(3, 7200),
                policy("40.00", NOW.minusSeconds(60), NOW.plusSeconds(9999)));
        assertThatThrownBy(() -> support.lockStartQuote(PRODUCT, NOW))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(CatalogErrorCode.PRODUCT_NOT_PURCHASABLE.defaultMessage());
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {"null, 7200", "3, null", "1, 7200", "3, 2399", "3, 86401"})
    void aProductWithoutUsableTeamTermsCannotStartATeam(Integer target, Integer lifetime) {
        given(
                onSale(),
                terms(target, lifetime),
                policy("40.00", NOW.minusSeconds(60), NOW.plusSeconds(9999)));
        assertThatThrownBy(() -> support.lockStartQuote(PRODUCT, NOW))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_NOT_STARTABLE);
    }

    @Test
    void theTeamTermBoundsAreInclusiveAtBothEnds() {
        assertThat(terms(2, 2400).configured()).isTrue();
        assertThat(terms(2, 86_400).configured()).isTrue();
    }

    @Test
    void aMissingPriceRevisionLeavesNothingToQuote() {
        given(onSale(), terms(3, 7200), null);
        assertThatThrownBy(() -> support.lockStartQuote(PRODUCT, NOW))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE.defaultMessage());
    }

    @Test
    void aRevisionThatDoesNotCoverTheWholeQuoteWindowIsRefused() {
        // Not in force yet.
        given(onSale(), terms(3, 7200), policy("40.00", NOW.plusSeconds(1), NOW.plusSeconds(9999)));
        assertThatThrownBy(() -> support.lockStartQuote(PRODUCT, NOW))
                .isInstanceOf(BizException.class);

        // Lapses one second before the 2400s quote window closes.
        given(
                onSale(),
                terms(3, 7200),
                policy("40.00", NOW.minusSeconds(60), NOW.plusSeconds(2400)));
        assertThatThrownBy(() -> support.lockStartQuote(PRODUCT, NOW))
                .isInstanceOf(BizException.class);

        // Exactly one second past the window is enough.
        given(
                onSale(),
                terms(3, 7200),
                policy("40.00", NOW.minusSeconds(60), NOW.plusSeconds(2401)));
        assertThat(support.lockStartQuote(PRODUCT, NOW).amount()).isEqualByComparingTo("40.00");
    }

    @Test
    void aTeamPriceThatIsNotBelowTheOriginalPriceIsRefused() {
        given(
                onSale(),
                terms(3, 7200),
                policy("100.00", NOW.minusSeconds(60), NOW.plusSeconds(9999)));
        assertThatThrownBy(() -> support.lockStartQuote(PRODUCT, NOW))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE.defaultMessage());
    }

    @Test
    void quotingNeedsAClock() {
        assertThatThrownBy(() -> support.lockStartQuote(PRODUCT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void joinOnlyNeedsTheProductToBeSellable() {
        when(products.findById(PRODUCT)).thenReturn(Optional.of(onSale()));

        CatalogPurchaseSupport.PurchasableProduct purchasable = support.requirePurchasable(PRODUCT);

        assertThat(purchasable.productId()).isEqualTo(PRODUCT);
        assertThat(purchasable.title()).isEqualTo("Headphones");
        assertThat(purchasable.currency()).isEqualTo("sgd");
    }

    @Test
    void joinRejectsAMissingOrUnsellableProduct() {
        when(products.findById(PRODUCT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> support.requirePurchasable(PRODUCT))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(CatalogErrorCode.PRODUCT_NOT_FOUND.defaultMessage());

        when(products.findById(PRODUCT))
                .thenReturn(Optional.of(product(ProductStatus.ON_SALE, 0, "sgd")));
        assertThatThrownBy(() -> support.requirePurchasable(PRODUCT))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(CatalogErrorCode.PRODUCT_NOT_PURCHASABLE.defaultMessage());
    }

    @Test
    void stockIsTakenThroughTheConditionalUpdateAndItsOutcomeIsReported() {
        when(products.consumeOneUnit(PRODUCT)).thenReturn(true, false);
        assertThat(support.consumeOneUnit(PRODUCT)).isTrue();
        assertThat(support.consumeOneUnit(PRODUCT)).isFalse();
    }

    // ---- #183: a START priced at the quote the buyer was shown ----

    private static final UUID BUYER = UUID.randomUUID();
    private static final UUID QUOTE = UUID.randomUUID();

    private static TeamPriceQuote quote(UUID productId, String amount, Instant expiresAt) {
        return new TeamPriceQuote(
                QUOTE,
                productId,
                BUYER,
                1,
                new BigDecimal(amount),
                "sgd",
                expiresAt.minusSeconds(900),
                expiresAt);
    }

    private void givenQuoted(Product product, ProductTeamTerms terms, TeamPriceQuote quote) {
        when(products.lockById(PRODUCT)).thenReturn(Optional.ofNullable(product));
        when(products.findTeamTerms(PRODUCT)).thenReturn(Optional.ofNullable(terms));
        when(teamPrices.currentTime()).thenReturn(NOW);
        when(teamPrices.findQuoteForOwner(QUOTE, BUYER)).thenReturn(Optional.ofNullable(quote));
    }

    private static CatalogErrorCode codeOf(Throwable error) {
        return (CatalogErrorCode) ((BizException) error).errorCode();
    }

    @Test
    void aQuotedStartChargesTheQuoteAndNeverReadsTheLatestPolicy() {
        givenQuoted(onSale(), terms(3, 7200), quote(PRODUCT, "40.00", NOW.plusSeconds(600)));
        // A later revision at another price must not move the quote.
        when(teamPrices.findLatestPolicy(PRODUCT))
                .thenReturn(
                        Optional.of(policy("80.00", NOW.minusSeconds(60), NOW.plusSeconds(9999))));

        CatalogPurchaseSupport.StartQuote start = support.lockQuotedStart(PRODUCT, QUOTE, BUYER);

        assertThat(start.productId()).isEqualTo(PRODUCT);
        assertThat(start.title()).isEqualTo("Headphones");
        assertThat(start.coverImage()).isEqualTo("https://cdn.example.com/cover.jpg");
        assertThat(start.amount()).isEqualByComparingTo("40.00");
        assertThat(start.currency()).isEqualTo("sgd");
        assertThat(start.targetSize()).isEqualTo(3);
        assertThat(start.lifetimeSeconds()).isEqualTo(7200);
        verify(teamPrices, never()).findLatestPolicy(any());
    }

    @Test
    void aQuoteIsHonouredEvenWhenNoPolicyIsInForceAnyMore() {
        givenQuoted(onSale(), terms(3, 7200), quote(PRODUCT, "40.00", NOW.plusSeconds(600)));
        when(teamPrices.findLatestPolicy(PRODUCT)).thenReturn(Optional.empty());

        assertThat(support.lockQuotedStart(PRODUCT, QUOTE, BUYER).amount())
                .isEqualByComparingTo("40.00");
    }

    @Test
    void aQuoteExpiresExactlyAtItsDeadline() {
        givenQuoted(onSale(), terms(3, 7200), quote(PRODUCT, "40.00", NOW));
        assertThatThrownBy(() -> support.lockQuotedStart(PRODUCT, QUOTE, BUYER))
                .isInstanceOf(BizException.class)
                .extracting(CatalogPurchaseSupportImplTest::codeOf)
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_QUOTE_EXPIRED);

        givenQuoted(onSale(), terms(3, 7200), quote(PRODUCT, "40.00", NOW.plusNanos(1_000)));
        assertThat(support.lockQuotedStart(PRODUCT, QUOTE, BUYER).amount())
                .isEqualByComparingTo("40.00");
    }

    @Test
    void aMissingForeignOrOtherProductQuoteIsTheSameNotFound() {
        // Missing, or owned by someone else: the owner-scoped lookup finds nothing either way.
        givenQuoted(onSale(), terms(3, 7200), null);
        assertThatThrownBy(() -> support.lockQuotedStart(PRODUCT, QUOTE, BUYER))
                .isInstanceOf(BizException.class)
                .extracting(CatalogPurchaseSupportImplTest::codeOf)
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_QUOTE_NOT_FOUND);

        givenQuoted(
                onSale(), terms(3, 7200), quote(UUID.randomUUID(), "40.00", NOW.plusSeconds(600)));
        assertThatThrownBy(() -> support.lockQuotedStart(PRODUCT, QUOTE, BUYER))
                .isInstanceOf(BizException.class)
                .extracting(CatalogPurchaseSupportImplTest::codeOf)
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_QUOTE_NOT_FOUND);
    }

    @Test
    void aQuoteThatNoLongerUndercutsTheOriginalPriceIsRefused() {
        givenQuoted(onSale(), terms(3, 7200), quote(PRODUCT, "100.00", NOW.plusSeconds(600)));
        assertThatThrownBy(() -> support.lockQuotedStart(PRODUCT, QUOTE, BUYER))
                .isInstanceOf(BizException.class)
                .extracting(CatalogPurchaseSupportImplTest::codeOf)
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE);
    }

    @Test
    void aQuotedStartStillNeedsASellableStartableProductAndChecksThatFirst() {
        givenQuoted(
                product(ProductStatus.OFF_SALE, 10, "sgd"),
                terms(3, 7200),
                quote(PRODUCT, "40.00", NOW.plusSeconds(600)));
        assertThatThrownBy(() -> support.lockQuotedStart(PRODUCT, QUOTE, BUYER))
                .isInstanceOf(BizException.class)
                .extracting(CatalogPurchaseSupportImplTest::codeOf)
                .isEqualTo(CatalogErrorCode.PRODUCT_NOT_PURCHASABLE);

        givenQuoted(onSale(), terms(null, 7200), quote(PRODUCT, "40.00", NOW.plusSeconds(600)));
        assertThatThrownBy(() -> support.lockQuotedStart(PRODUCT, QUOTE, BUYER))
                .isInstanceOf(BizException.class)
                .extracting(CatalogPurchaseSupportImplTest::codeOf)
                .isEqualTo(CatalogErrorCode.PRODUCT_NOT_STARTABLE);

        givenQuoted(null, terms(3, 7200), quote(PRODUCT, "40.00", NOW.plusSeconds(600)));
        assertThatThrownBy(() -> support.lockQuotedStart(PRODUCT, QUOTE, BUYER))
                .isInstanceOf(BizException.class)
                .extracting(CatalogPurchaseSupportImplTest::codeOf)
                .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND);

        verify(teamPrices, never()).findQuoteForOwner(any(), any());
    }

    @Test
    void theQuotedStartLocksThenReadsTheClockThenTheQuote() {
        givenQuoted(onSale(), terms(3, 7200), quote(PRODUCT, "40.00", NOW.plusSeconds(600)));

        support.lockQuotedStart(PRODUCT, QUOTE, BUYER);

        InOrder order = inOrder(products, teamPrices);
        order.verify(products).lockById(PRODUCT);
        order.verify(teamPrices).currentTime();
        order.verify(teamPrices).findQuoteForOwner(QUOTE, BUYER);
    }

    @Test
    void aQuotedStartNeedsAQuoteAndABuyer() {
        assertThatThrownBy(() -> support.lockQuotedStart(PRODUCT, null, BUYER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> support.lockQuotedStart(PRODUCT, QUOTE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
