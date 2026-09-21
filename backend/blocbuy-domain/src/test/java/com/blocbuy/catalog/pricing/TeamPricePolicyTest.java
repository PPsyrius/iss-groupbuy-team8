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
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Team-price policy: exact SGD amounts, microsecond window, compatibility guards. */
class TeamPricePolicyTest {
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

    /** Rejects zero/negative/3-decimal/1e30 SGD amounts lacking exact 2-decimal storage. */
    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "1.001", "10000000000000000", "1e30"})
    void rejectsAmountsThatCannotBeStoredExactly(String amount) {
        Instant validUntil = NOW.plusSeconds(60);

        assertThatThrownBy(() -> policy(amount, "sgd", NOW, validUntil))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID));
    }

    @Test
    void normalizesExactCentsWithoutRounding() {
        assertThat(policy("0.010", "sgd", NOW, NOW.plusSeconds(60)).amount())
                .isEqualTo(new BigDecimal("0.01"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "jpy", "SGD", "", "not-a-currency"})
    void rejectsUnsupportedCurrencyInsteadOfAssumingMinorUnits(String currency) {
        Instant validUntil = NOW.plusSeconds(60);

        assertThatThrownBy(() -> policy("20", currency, NOW, validUntil))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID));
    }

    @Test
    void rejectsEmptyValidityWindow() {
        assertThatThrownBy(() -> policy("20", "sgd", NOW, NOW))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID));
    }

    /** Rejects 100ns window collapsing to zero under PostgreSQL microsecond truncation. */
    @Test
    void rejectsWindowThatCollapsesAtPostgresqlMicrosecondPrecision() {
        Instant from = Instant.parse("2026-09-09T00:00:00.000000100Z");
        Instant until = Instant.parse("2026-09-09T00:00:00.000000200Z");

        assertThatThrownBy(() -> policy("20", "sgd", from, until))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID);
    }

    @Test
    void startInclusiveAndEndExclusive() {
        Instant validUntil = NOW.plusSeconds(60);
        TeamPricePolicy policy = policy("20", "sgd", NOW, validUntil);
        Product product = product("sgd", ProductStatus.ON_SALE);

        policy.requireQuotable(product, NOW);

        Instant beforeStart = NOW.minusNanos(1);
        assertThatThrownBy(() -> policy.requireQuotable(product, beforeStart))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE));

        assertThatThrownBy(() -> policy.requireQuotable(product, validUntil))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE));
    }

    @Test
    void rejectsCurrencyMismatchAndPriceNotBelowOriginal() {
        Instant validUntil = NOW.plusSeconds(60);
        TeamPricePolicy policy = policy("20", "sgd", NOW, validUntil);

        Product currencyMismatchProduct = product("usd", ProductStatus.ON_SALE);
        assertThatThrownBy(() -> policy.requireCompatible(currencyMismatchProduct))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID));

        TeamPricePolicy equalPricePolicy = policy("40", "sgd", NOW, validUntil);
        Product product = product("sgd", ProductStatus.ON_SALE);
        assertThatThrownBy(() -> equalPricePolicy.requireCompatible(product))
                .isInstanceOfSatisfying(
                        BizException.class,
                        ex ->
                                assertThat(ex.errorCode())
                                        .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID));
    }

    @Test
    void rejectsOffSaleProduct() {
        Instant validUntil = NOW.plusSeconds(60);
        TeamPricePolicy policy = policy("20", "sgd", NOW, validUntil);
        Product offSaleProduct = product("sgd", ProductStatus.OFF_SALE);

        assertThatThrownBy(() -> policy.requireCompatible(offSaleProduct))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE);
    }

    /** Verifies absent identity, amount, and revision fields fail before business validation. */
    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void rejectsInvalidIdentityAmountAndVersion(ThrowingCallable construction) {
        assertThatThrownBy(construction)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID);
    }

    static Stream<ThrowingCallable> invalidIdentities() {
        Instant validUntil = NOW.plusSeconds(60);
        BigDecimal amount = new BigDecimal("20");
        return Stream.of(
                () -> new TeamPricePolicy(null, amount, "sgd", NOW, validUntil, 1),
                () -> new TeamPricePolicy(PRODUCT, amount, "sgd", null, validUntil, 1),
                () -> new TeamPricePolicy(PRODUCT, amount, "sgd", NOW, null, 1),
                () -> new TeamPricePolicy(PRODUCT, amount, "sgd", NOW, validUntil, 0),
                () -> new TeamPricePolicy(PRODUCT, null, "sgd", NOW, validUntil, 1));
    }

    /** Verifies compatibility checks cannot run against an absent product. */
    @Test
    void requireCompatibleRejectsNullProduct() {
        TeamPricePolicy policy = policy("20", "sgd", NOW, NOW.plusSeconds(60));

        assertThatThrownBy(() -> policy.requireCompatible(null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE);
    }

    /** Verifies configuration cannot misprice another product. */
    @Test
    void requireCompatibleRejectsForeignProduct() {
        TeamPricePolicy policy = policy("20", "sgd", NOW, NOW.plusSeconds(60));
        Product foreign =
                Product.reconstitute(
                        UUID.randomUUID(),
                        "P-9",
                        "Product",
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        new BigDecimal("40.00"),
                        "sgd",
                        5,
                        0,
                        ProductStatus.ON_SALE,
                        PricingMode.DUAL);

        assertThatThrownBy(() -> policy.requireCompatible(foreign))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.TEAM_PRICE_INVALID);
    }

    private static TeamPricePolicy policy(
            String amount, String currency, Instant from, Instant until) {
        return new TeamPricePolicy(PRODUCT, new BigDecimal(amount), currency, from, until, 1);
    }

    private static Product product(String currency, ProductStatus status) {
        return Product.reconstitute(
                PRODUCT,
                "P-1",
                "Product",
                null,
                null,
                null,
                List.of(),
                null,
                new BigDecimal("40.00"),
                currency,
                5,
                0,
                status,
                PricingMode.DUAL);
    }
}
