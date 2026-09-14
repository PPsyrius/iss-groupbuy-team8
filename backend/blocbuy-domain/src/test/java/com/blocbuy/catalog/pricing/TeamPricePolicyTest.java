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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TeamPricePolicyTest {
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "1.001", "10000000000000000", "1e30"})
    void rejectsAmountsThatCannotBeStoredExactly(String amount) {
        Instant validUntil = NOW.plusSeconds(60);

        assertThatThrownBy(() -> policy(amount, "sgd", NOW, validUntil))
                .isInstanceOf(BizException.class);
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
                .isInstanceOf(BizException.class);
    }

    @Test
    void rejectsEmptyValidityWindow() {
        assertThatThrownBy(() -> policy("20", "sgd", NOW, NOW)).isInstanceOf(BizException.class);
    }

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
                .isInstanceOf(BizException.class);

        assertThatThrownBy(() -> policy.requireQuotable(product, validUntil))
                .isInstanceOf(BizException.class);
    }

    @Test
    void rejectsCurrencyMismatchAndPriceNotBelowOriginal() {
        Instant validUntil = NOW.plusSeconds(60);
        TeamPricePolicy policy = policy("20", "sgd", NOW, validUntil);

        Product currencyMismatchProduct = product("usd", ProductStatus.ON_SALE);
        assertThatThrownBy(() -> policy.requireCompatible(currencyMismatchProduct))
                .isInstanceOf(BizException.class);

        TeamPricePolicy equalPricePolicy = policy("40", "sgd", NOW, validUntil);
        Product product = product("sgd", ProductStatus.ON_SALE);
        assertThatThrownBy(() -> equalPricePolicy.requireCompatible(product))
                .isInstanceOf(BizException.class);
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
