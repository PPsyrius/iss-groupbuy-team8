package com.blocbuy.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
                .isInstanceOf(BizException.class);
        verify(prices, never()).insertQuote(any());
    }

    @Test
    void staleSessionCannotIssueQuote() {
        stubProduct();
        doThrow(new BizException(IdentityErrorCode.ACCOUNT_DISABLED))
                .when(users)
                .requireActive(OWNER);
        assertThatThrownBy(() -> service.issueQuote(PRODUCT, OWNER))
                .isInstanceOf(BizException.class);
        verify(prices, never()).insertQuote(any());
    }

    @Test
    void createsFirstRevisionAndRejectsStaleRetryWithoutAnotherWrite() {
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
