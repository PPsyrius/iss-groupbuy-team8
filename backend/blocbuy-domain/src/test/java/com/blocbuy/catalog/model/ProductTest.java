package com.blocbuy.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Aggregate construction guards and state-independent invariants. */
class ProductTest {
    private static final UUID ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");

    @Test
    void reconstituteRejectsNullIdentity() {
        BigDecimal price = new BigDecimal("40.00");

        assertThatThrownBy(() ->
                        reconstitute(
                                null,
                                "Product",
                                price,
                                "sgd",
                                ProductStatus.ON_SALE,
                                PricingMode.DUAL,
                                5,
                                0,
                                0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        reconstitute(ID, "Product", price, "sgd", null, PricingMode.DUAL, 5, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        reconstitute(
                                ID, "Product", price, "sgd", ProductStatus.ON_SALE, null, 5, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstituteRejectsBlankTitle() {
        BigDecimal price = new BigDecimal("40.00");

        assertThatThrownBy(() ->
                        reconstitute(
                                ID,
                                null,
                                price,
                                "sgd",
                                ProductStatus.ON_SALE,
                                PricingMode.DUAL,
                                5,
                                0,
                                0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        reconstitute(
                                ID,
                                "  ",
                                price,
                                "sgd",
                                ProductStatus.ON_SALE,
                                PricingMode.DUAL,
                                5,
                                0,
                                0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstituteRejectsBadPrice() {
        assertThatThrownBy(() ->
                        reconstitute(
                                ID,
                                "Product",
                                null,
                                "sgd",
                                ProductStatus.ON_SALE,
                                PricingMode.DUAL,
                                5,
                                0,
                                0))
                .isInstanceOf(IllegalArgumentException.class);

        BigDecimal negativePrice = new BigDecimal("-1");
        assertThatThrownBy(() ->
                        reconstitute(
                                ID,
                                "Product",
                                negativePrice,
                                "sgd",
                                ProductStatus.ON_SALE,
                                PricingMode.DUAL,
                                5,
                                0,
                                0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstituteRejectsBlankCurrencyAndNegativeVersion() {
        BigDecimal price = new BigDecimal("40.00");

        assertThatThrownBy(() ->
                        reconstitute(
                                ID,
                                "Product",
                                price,
                                null,
                                ProductStatus.ON_SALE,
                                PricingMode.DUAL,
                                5,
                                0,
                                0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        reconstitute(
                                ID,
                                "Product",
                                price,
                                "  ",
                                ProductStatus.ON_SALE,
                                PricingMode.DUAL,
                                5,
                                0,
                                0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        reconstitute(
                                ID,
                                "Product",
                                price,
                                "sgd",
                                ProductStatus.ON_SALE,
                                PricingMode.DUAL,
                                5,
                                0,
                                -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void draftRejectsNullOrBlankProductNo() {
        ListingDetails details = details();

        assertThatThrownBy(() -> Product.draft(ID, null, details))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_INVALID);
        assertThatThrownBy(() -> Product.draft(ID, "!!!", details))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_INVALID);
    }

    @Test
    void publishRejectsZeroPriceWithStock() {
        Product freebie =
                reconstitute(
                        ID,
                        "Product",
                        BigDecimal.ZERO,
                        "sgd",
                        ProductStatus.DRAFT,
                        PricingMode.DUAL,
                        5,
                        0,
                        0);

        assertThatThrownBy(freebie::publish)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_STATE_CONFLICT);
    }

    @Test
    void requireDeletableRejectsSoldDraft() {
        Product sold =
                reconstitute(
                        ID,
                        "Product",
                        new BigDecimal("40.00"),
                        "sgd",
                        ProductStatus.DRAFT,
                        PricingMode.DUAL,
                        5,
                        1,
                        0);

        assertThatThrownBy(sold::requireDeletable)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_REFERENCED);
    }

    @Test
    void requireVersionRejectsExhaustedToken() {
        Product exhausted =
                reconstitute(
                        ID,
                        "Product",
                        new BigDecimal("40.00"),
                        "sgd",
                        ProductStatus.ON_SALE,
                        PricingMode.DUAL,
                        5,
                        0,
                        Integer.MAX_VALUE);

        assertThatThrownBy(() -> exhausted.requireVersion(Integer.MAX_VALUE))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_VERSION_CONFLICT);
    }

    @Test
    void nullImagesDefaultToEmpty() {
        Product product =
                Product.reconstitute(
                        ID,
                        "P-1",
                        "Product",
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("40.00"),
                        "sgd",
                        5,
                        0,
                        ProductStatus.ON_SALE,
                        PricingMode.DUAL,
                        0);

        assertThat(product.images()).isEmpty();
    }

    private static Product reconstitute(
            UUID id,
            String title,
            BigDecimal price,
            String currency,
            ProductStatus status,
            PricingMode pricingMode,
            int stock,
            int soldCount,
            int version) {
        return Product.reconstitute(
                id,
                "P-1",
                title,
                null,
                null,
                null,
                List.of(),
                null,
                price,
                currency,
                stock,
                soldCount,
                status,
                pricingMode,
                version);
    }

    private static ListingDetails details() {
        return new ListingDetails(
                "Tea", null, null, null, List.of(), null, new BigDecimal("12.00"), "sgd", 5);
    }
}
