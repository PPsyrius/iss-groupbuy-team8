package com.blocbuy.catalog.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Dual pricing: individual = list price; group = best active deal, currency-guarded. */
class DualPricingStrategyTest {

    private static final UUID PRODUCT_ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");

    private final DualPricingStrategy strategy = new DualPricingStrategy();

    @Test
    void modeIsDual() {
        assertThat(strategy.mode()).isEqualTo(PricingMode.DUAL);
    }

    @Test
    void resolvesDualPriceWhenDealActive() {
        Product product = product("129.00");
        GroupDealOffer offer = offer("89.00", "sgd");

        ListingPrice price = strategy.resolve(product, offer);

        assertThat(price.individualPrice()).isEqualByComparingTo("129.00");
        assertThat(price.groupPrice()).isEqualByComparingTo("89.00");
        assertThat(price.currency()).isEqualTo("sgd");
        assertThat(price.hasGroupPrice()).isTrue();
    }

    @Test
    void fallsBackToIndividualPriceWhenNoDeal() {
        ListingPrice price = strategy.resolve(product("45.00"), null);

        assertThat(price.individualPrice()).isEqualByComparingTo("45.00");
        assertThat(price.groupPrice()).isNull();
        assertThat(price.hasGroupPrice()).isFalse();
    }

    @Test
    void ignoresDealWithMismatchedCurrency() {
        ListingPrice price = strategy.resolve(product("45.00"), offer("35.00", "usd"));

        assertThat(price.individualPrice()).isEqualByComparingTo("45.00");
        assertThat(price.groupPrice()).isNull();
        assertThat(price.currency()).isEqualTo("sgd");
        assertThat(price.hasGroupPrice()).isFalse();
    }

    @Test
    void rejectsNullProduct() {
        assertThatThrownBy(() -> strategy.resolve(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quotesFirstTeamFromPolicyWithoutAnExistingDeal() {
        Instant now = Instant.parse("2026-09-09T00:00:00Z");
        TeamPricePolicy policy =
                new TeamPricePolicy(
                        PRODUCT_ID,
                        new BigDecimal("35.00"),
                        "sgd",
                        now.minusSeconds(1),
                        now.plusSeconds(3600),
                        1);

        assertThat(strategy.quoteTeamPrice(product("45.00"), policy, now))
                .isEqualByComparingTo("35.00");
    }

    /** Verifies an expired policy cannot back a quote even for a compatible product. */
    @Test
    void quoteTeamPriceRejectsExpiredPolicy() {
        Instant now = Instant.parse("2026-09-09T00:00:00Z");
        TeamPricePolicy expired =
                new TeamPricePolicy(
                        PRODUCT_ID, new BigDecimal("35.00"), "sgd", now.minusSeconds(3600), now, 1);
        Product product = product("45.00");

        assertThatThrownBy(() -> strategy.quoteTeamPrice(product, expired, now))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE));
    }

    /** Verifies a delisted product cannot be quoted even with a valid policy. */
    @Test
    void quoteTeamPriceRejectsOffSaleProduct() {
        Instant now = Instant.parse("2026-09-09T00:00:00Z");
        TeamPricePolicy policy =
                new TeamPricePolicy(
                        PRODUCT_ID,
                        new BigDecimal("35.00"),
                        "sgd",
                        now.minusSeconds(1),
                        now.plusSeconds(3600),
                        1);
        Product offSale =
                Product.reconstitute(
                        PRODUCT_ID,
                        "DEMO-P-0001",
                        "Headphones",
                        null,
                        null,
                        null,
                        List.of(),
                        "electronics",
                        new BigDecimal("45.00"),
                        "sgd",
                        10,
                        0,
                        ProductStatus.OFF_SALE,
                        PricingMode.DUAL);

        assertThatThrownBy(() -> strategy.quoteTeamPrice(offSale, policy, now))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE));
    }

    /** Verifies a policy for another product cannot price this one. */
    @Test
    void quoteTeamPriceRejectsForeignProduct() {
        Instant now = Instant.parse("2026-09-09T00:00:00Z");
        TeamPricePolicy foreign =
                new TeamPricePolicy(
                        UUID.randomUUID(),
                        new BigDecimal("35.00"),
                        "sgd",
                        now.minusSeconds(1),
                        now.plusSeconds(3600),
                        1);
        Product product = product("45.00");

        assertThatThrownBy(() -> strategy.quoteTeamPrice(product, foreign, now))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID));
    }

    /** Verifies quotation delegates null-safety to policy compatibility, never NPEs. */
    @Test
    void quoteTeamPriceRejectsNullProduct() {
        Instant now = Instant.parse("2026-09-09T00:00:00Z");
        TeamPricePolicy policy =
                new TeamPricePolicy(
                        PRODUCT_ID,
                        new BigDecimal("35.00"),
                        "sgd",
                        now.minusSeconds(1),
                        now.plusSeconds(3600),
                        1);

        assertThatThrownBy(() -> strategy.quoteTeamPrice(null, policy, now))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE));
    }

    private static Product product(String originalPrice) {
        return Product.reconstitute(
                PRODUCT_ID,
                "DEMO-P-0001",
                "Headphones",
                null,
                null,
                null,
                List.of(),
                "electronics",
                new BigDecimal(originalPrice),
                "sgd",
                10,
                0,
                ProductStatus.ON_SALE,
                PricingMode.DUAL);
    }

    private static GroupDealOffer offer(String dealPrice, String currency) {
        return new GroupDealOffer(
                UUID.fromString("b0000000-0000-7000-8000-000000000001"),
                new BigDecimal(dealPrice),
                currency,
                5,
                2,
                Instant.parse("2027-12-31T23:59:59Z"));
    }
}
