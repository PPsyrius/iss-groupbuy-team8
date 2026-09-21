package com.blocbuy.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.pricing.DualPricingStrategy;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.catalog.pricing.TeamPriceQuote;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.catalog.repository.TeamPriceRepository;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.service.UserAppService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Team-price quotes: policy revisioning, owner-scoped reads, lock ordering, no orphan writes. */
@ExtendWith(MockitoExtension.class)
class TeamPriceAppServiceImplTest {
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID OWNER = UUID.fromString("b0000000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");
    @Mock private ProductRepository products;
    @Mock private TeamPriceRepository prices;
    @Mock private UserAppService users;
    private TeamPriceAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new TeamPriceAppServiceImpl(
                        products,
                        prices,
                        new PricingStrategyResolver(List.of(new DualPricingStrategy())),
                        users);
    }

    /** Proves issueQuote follows lock-active-quote-insert order with 900s UUIDv7 expiry. */
    @Test
    void firstTeamQuoteUsesStrategyAndClockAfterProductLock() {
        stubProduct();
        when(prices.findLatestPolicy(PRODUCT))
                .thenReturn(Optional.of(policy("20", 1, NOW.plusSeconds(3600))));
        when(prices.currentTime()).thenReturn(NOW);
        var quote = service.issueQuote(PRODUCT, OWNER);
        assertThat(quote.id().version()).isEqualTo(7);
        assertThat(quote.ownerId()).isEqualTo(OWNER);
        assertThat(quote.amount()).isEqualByComparingTo("20");
        assertThat(quote.expiresAt()).isEqualTo(NOW.plusSeconds(900));
        var order = inOrder(products, users, prices);
        order.verify(products).lockById(PRODUCT);
        order.verify(users).requireActive(OWNER);
        order.verify(prices).findLatestPolicy(PRODUCT);
        order.verify(prices).currentTime();
        order.verify(prices).insertQuote(quote);
    }

    @Test
    void policyExpiryClipsQuoteLifetime() {
        stubProduct();
        when(prices.findLatestPolicy(PRODUCT))
                .thenReturn(Optional.of(policy("20", 1, NOW.plusSeconds(30))));
        when(prices.currentTime()).thenReturn(NOW);
        assertThat(service.issueQuote(PRODUCT, OWNER).expiresAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void absentPolicyDoesNotInventPriceOrInsertQuote() {
        stubProduct();
        assertThatThrownBy(() -> service.issueQuote(PRODUCT, OWNER))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE);
        verify(prices, never()).insertQuote(any());
    }

    @Test
    void expiredLatestRevisionDoesNotFallBack() {
        stubProduct();
        when(prices.findLatestPolicy(PRODUCT)).thenReturn(Optional.of(policy("20", 2, NOW)));
        when(prices.currentTime()).thenReturn(NOW);
        assertThatThrownBy(() -> service.issueQuote(PRODUCT, OWNER))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE);
        verify(prices, never()).insertQuote(any());
    }

    @Test
    void staleSessionCannotIssueQuote() {
        stubProduct();
        doThrow(new BizException(IdentityErrorCode.ACCOUNT_DISABLED))
                .when(users)
                .requireActive(OWNER);
        assertThatThrownBy(() -> service.issueQuote(PRODUCT, OWNER))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.ACCOUNT_DISABLED);
        verify(prices, never()).insertQuote(any());
    }

    @Test
    void firstRevisionCommitsOnceThenStaleRetryConflicts() {
        stubProduct();
        when(prices.currentTime()).thenReturn(NOW);
        var command =
                new TeamPriceAppService.ConfigurePolicy(
                        new BigDecimal("20"), "sgd", NOW, NOW.plusSeconds(3600), 0);
        var configured = service.configure(PRODUCT, OWNER, command);
        assertThat(configured.version()).isEqualTo(1);
        verify(prices).appendPolicy(configured, OWNER, NOW);
        when(prices.findLatestPolicy(PRODUCT)).thenReturn(Optional.of(configured));
        assertThatThrownBy(() -> service.configure(PRODUCT, OWNER, command))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_VERSION_CONFLICT);
        verify(prices).appendPolicy(any(), any(), any());
    }

    @Test
    void oldQuoteRemainsSnapshotAfterPolicyRevision() {
        var snapshot =
                new TeamPriceQuote(
                        OWNER,
                        PRODUCT,
                        OWNER,
                        1,
                        new BigDecimal("20"),
                        "sgd",
                        NOW,
                        NOW.plusSeconds(900));
        when(prices.findQuoteForOwner(OWNER, OWNER)).thenReturn(Optional.of(snapshot));
        assertThat(service.getQuote(OWNER, OWNER)).isEqualTo(snapshot);
        verify(prices, never()).findLatestPolicy(any());
    }

    @Test
    void foreignQuoteIsIndistinguishableFromMissingQuote() {
        assertThatThrownBy(() -> service.getQuote(PRODUCT, OWNER))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_QUOTE_NOT_FOUND);
        verify(prices).findQuoteForOwner(PRODUCT, OWNER);
    }

    @Test
    void getPolicyReturnsLatestRevisionAfterActiveCheck() {
        TeamPricePolicy latest = policy("20", 2, NOW.plusSeconds(3600));
        when(prices.findLatestPolicy(PRODUCT)).thenReturn(Optional.of(latest));

        assertThat(service.getPolicy(PRODUCT, OWNER)).isEqualTo(latest);
        var order = inOrder(users, prices);
        order.verify(users).requireActive(OWNER);
        order.verify(prices).findLatestPolicy(PRODUCT);
    }

    @Test
    void getPolicyWithoutRevisionIsUnavailable() {
        when(prices.findLatestPolicy(PRODUCT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPolicy(PRODUCT, OWNER))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE);
    }

    @Test
    void disabledActorCannotReadPolicy() {
        doThrow(new BizException(IdentityErrorCode.ACCOUNT_DISABLED))
                .when(users)
                .requireActive(OWNER);

        assertThatThrownBy(() -> service.getPolicy(PRODUCT, OWNER))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.ACCOUNT_DISABLED);
        verify(prices, never()).findLatestPolicy(any());
    }

    @Test
    void configureRejectsIncompatiblePriceWithoutWriting() {
        stubProduct();
        when(prices.currentTime()).thenReturn(NOW);
        var command =
                new TeamPriceAppService.ConfigurePolicy(
                        new BigDecimal("40"), "sgd", NOW, NOW.plusSeconds(3600), 0);

        assertThatThrownBy(() -> service.configure(PRODUCT, OWNER, command))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID);
        verify(prices, never()).appendPolicy(any(), any(), any());
    }

    @Test
    void configureRejectsExpiredWindowWithoutWriting() {
        stubProduct();
        when(prices.currentTime()).thenReturn(NOW);
        var command =
                new TeamPriceAppService.ConfigurePolicy(
                        new BigDecimal("20"), "sgd", NOW.minusSeconds(3600), NOW, 0);

        assertThatThrownBy(() -> service.configure(PRODUCT, OWNER, command))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID);
        verify(prices, never()).appendPolicy(any(), any(), any());
    }

    @Test
    void configureMissingProductIsNotFound() {
        when(products.lockById(PRODUCT)).thenReturn(Optional.empty());
        var command =
                new TeamPriceAppService.ConfigurePolicy(
                        new BigDecimal("20"), "sgd", NOW, NOW.plusSeconds(3600), 0);

        assertThatThrownBy(() -> service.configure(PRODUCT, OWNER, command))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND);
        verify(prices, never()).findLatestPolicy(any());
    }

    @Test
    void issueQuoteMissingProductIsNotFound() {
        when(products.lockById(PRODUCT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueQuote(PRODUCT, OWNER))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND);
        verify(prices, never()).findLatestPolicy(any());
    }

    /** Proves configure follows lock-active-policy-clock-append order with owner and time. */
    @Test
    void configureUsesProductFirstLockOrder() {
        stubProduct();
        when(prices.findLatestPolicy(PRODUCT)).thenReturn(Optional.empty());
        when(prices.currentTime()).thenReturn(NOW);
        var command =
                new TeamPriceAppService.ConfigurePolicy(
                        new BigDecimal("20"), "sgd", NOW, NOW.plusSeconds(3600), 0);

        service.configure(PRODUCT, OWNER, command);

        var order = inOrder(products, users, prices);
        order.verify(products).lockById(PRODUCT);
        order.verify(users).requireActive(OWNER);
        order.verify(prices).findLatestPolicy(PRODUCT);
        order.verify(prices).currentTime();
        order.verify(prices).appendPolicy(any(), eq(OWNER), eq(NOW));
    }

    @Test
    void staleExpectedVersionFailsWithoutClockOrWrite() {
        stubProduct();
        when(prices.findLatestPolicy(PRODUCT))
                .thenReturn(Optional.of(policy("20", 1, NOW.plusSeconds(3600))));
        var command =
                new TeamPriceAppService.ConfigurePolicy(
                        new BigDecimal("21"), "sgd", NOW, NOW.plusSeconds(3600), 0);

        assertThatThrownBy(() -> service.configure(PRODUCT, OWNER, command))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_VERSION_CONFLICT);
        verify(prices, never()).currentTime();
        verify(prices, never()).appendPolicy(any(), any(), any());
    }

    @Test
    void getQuoteDisabledActorCannotRead() {
        doThrow(new BizException(IdentityErrorCode.ACCOUNT_DISABLED))
                .when(users)
                .requireActive(OWNER);

        assertThatThrownBy(() -> service.getQuote(PRODUCT, OWNER))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.ACCOUNT_DISABLED);
        verify(prices, never()).findQuoteForOwner(any(), any());
    }

    @Test
    void configureRejectsExhaustedVersionWithoutWriting() {
        stubProduct();
        TeamPricePolicy exhausted =
                new TeamPricePolicy(
                        PRODUCT,
                        new BigDecimal("20"),
                        "sgd",
                        NOW.minusSeconds(10),
                        NOW.plusSeconds(3600),
                        Integer.MAX_VALUE);
        when(prices.findLatestPolicy(PRODUCT)).thenReturn(Optional.of(exhausted));
        var command =
                new TeamPriceAppService.ConfigurePolicy(
                        new BigDecimal("20"), "sgd", NOW, NOW.plusSeconds(3600), Integer.MAX_VALUE);

        assertThatThrownBy(() -> service.configure(PRODUCT, OWNER, command))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_VERSION_CONFLICT);
        verify(prices, never()).appendPolicy(any(), any(), any());
    }

    private void stubProduct() {
        when(products.lockById(PRODUCT))
                .thenReturn(
                        Optional.of(
                                Product.reconstitute(
                                        PRODUCT,
                                        "P-1",
                                        "Product",
                                        null,
                                        null,
                                        null,
                                        List.of(),
                                        null,
                                        new BigDecimal("40"),
                                        "sgd",
                                        5,
                                        0,
                                        ProductStatus.ON_SALE,
                                        PricingMode.DUAL)));
    }

    private static TeamPricePolicy policy(String amount, int version, Instant until) {
        return new TeamPricePolicy(
                PRODUCT, new BigDecimal(amount), "sgd", NOW.minusSeconds(10), until, version);
    }
}
