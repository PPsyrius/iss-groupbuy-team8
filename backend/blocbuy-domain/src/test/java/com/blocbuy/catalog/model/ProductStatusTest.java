package com.blocbuy.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** State-pattern defaults: disallowed transitions fail; published commercial terms are frozen. */
class ProductStatusTest {
    private static final UUID ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");

    @Test
    void onSalePublishThrowsStateConflict() {
        Product published = product(ProductStatus.ON_SALE);

        assertThatThrownBy(published::publish)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_STATE_CONFLICT);
    }

    @Test
    void offSaleDelistThrowsStateConflict() {
        Product delisted = product(ProductStatus.OFF_SALE);

        assertThatThrownBy(delisted::delist)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_STATE_CONFLICT);
    }

    @Test
    void currencyOnlyChangeIsRejectedAfterPublication() {
        Product published = product(ProductStatus.ON_SALE, "usd");
        ListingDetails switchedCurrency =
                new ListingDetails(
                        "Product",
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        new BigDecimal("40.00"),
                        "sgd",
                        5);

        assertThatThrownBy(() -> published.revise(switchedCurrency))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_STATE_CONFLICT);
    }

    @Test
    void fromCodeRejectsUnknownCode() {
        assertThatThrownBy(() -> ProductStatus.fromCode("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(ProductStatus.class)
    void fromCodeResolvesAllCodes(ProductStatus status) {
        assertThat(ProductStatus.fromCode(status.code())).isEqualTo(status);
    }

    private static Product product(ProductStatus status) {
        return product(status, "sgd");
    }

    private static Product product(ProductStatus status, String currency) {
        return Product.reconstitute(
                ID,
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
