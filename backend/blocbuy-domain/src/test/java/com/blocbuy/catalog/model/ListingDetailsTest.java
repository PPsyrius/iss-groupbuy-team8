package com.blocbuy.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Editable listing validation and defensive normalization. */
class ListingDetailsTest {

    @Test
    void validDetailsExposeValuesAndDefensivelyCopyImages() {
        List<String> images = new ArrayList<>(List.of("image-1"));

        ListingDetails details =
                new ListingDetails(
                        "Tea",
                        "Fresh",
                        "Green tea",
                        "cover",
                        images,
                        "drinks",
                        new BigDecimal("12"),
                        "sgd",
                        5);
        images.add("image-2");

        assertThat(details.title()).isEqualTo("Tea");
        assertThat(details.subtitle()).isEqualTo("Fresh");
        assertThat(details.description()).isEqualTo("Green tea");
        assertThat(details.coverImage()).isEqualTo("cover");
        assertThat(details.images()).containsExactly("image-1");
        assertThat(details.category()).isEqualTo("drinks");
        assertThat(details.originalPrice()).isEqualByComparingTo("12.00");
        assertThat(details.currency()).isEqualTo("sgd");
        assertThat(details.stock()).isEqualTo(5);
    }

    @Test
    void nullImagesNormalizeToEmptyList() {
        ListingDetails details = validDetails(null);

        assertThat(details.images()).isEmpty();
    }

    @Test
    void rejectsInvalidTitle() {
        assertInvalid(() -> details(null, null, null, null, List.of(), null, price(), "sgd", 0));
        assertInvalid(() -> details(" ", null, null, null, List.of(), null, price(), "sgd", 0));
        assertInvalid(
                () ->
                        details(
                                "t".repeat(257),
                                null,
                                null,
                                null,
                                List.of(),
                                null,
                                price(),
                                "sgd",
                                0));
    }

    @Test
    void rejectsOversizedOptionalText() {
        assertInvalid(
                () ->
                        details(
                                "Tea",
                                "s".repeat(257),
                                null,
                                null,
                                List.of(),
                                null,
                                price(),
                                "sgd",
                                0));
        assertInvalid(
                () ->
                        details(
                                "Tea",
                                null,
                                "d".repeat(10001),
                                null,
                                List.of(),
                                null,
                                price(),
                                "sgd",
                                0));
        assertInvalid(
                () ->
                        details(
                                "Tea",
                                null,
                                null,
                                "c".repeat(1025),
                                List.of(),
                                null,
                                price(),
                                "sgd",
                                0));
        assertInvalid(
                () ->
                        details(
                                "Tea",
                                null,
                                null,
                                null,
                                List.of(),
                                "c".repeat(65),
                                price(),
                                "sgd",
                                0));
    }

    @Test
    void rejectsInvalidCommercialValues() {
        assertInvalid(() -> details("Tea", null, null, null, List.of(), null, null, "sgd", 0));
        assertInvalid(
                () -> details("Tea", null, null, null, List.of(), null, BigDecimal.ZERO, "sgd", 0));
        assertInvalid(
                () ->
                        details(
                                "Tea",
                                null,
                                null,
                                null,
                                List.of(),
                                null,
                                new BigDecimal("10000000000000000.00"),
                                "sgd",
                                0));
        assertInvalid(() -> details("Tea", null, null, null, List.of(), null, price(), "usd", 0));
        assertInvalid(() -> details("Tea", null, null, null, List.of(), null, price(), "sgd", -1));
    }

    @Test
    void rejectsPriceThatRequiresRounding() {
        assertInvalid(
                () ->
                        details(
                                "Tea",
                                null,
                                null,
                                null,
                                List.of(),
                                null,
                                new BigDecimal("12.345"),
                                "sgd",
                                0));
    }

    @Test
    void rejectsInvalidImageCollection() {
        assertInvalid(
                () ->
                        details(
                                "Tea",
                                null,
                                null,
                                null,
                                java.util.Collections.nCopies(21, "image"),
                                null,
                                price(),
                                "sgd",
                                0));
        assertInvalid(
                () ->
                        details(
                                "Tea",
                                null,
                                null,
                                null,
                                java.util.Collections.singletonList(null),
                                null,
                                price(),
                                "sgd",
                                0));
        assertInvalid(
                () -> details("Tea", null, null, null, List.of(" "), null, price(), "sgd", 0));
        assertInvalid(
                () ->
                        details(
                                "Tea",
                                null,
                                null,
                                null,
                                List.of("i".repeat(1025)),
                                null,
                                price(),
                                "sgd",
                                0));
    }

    private static ListingDetails validDetails(List<String> images) {
        return details("Tea", null, null, null, images, null, price(), "sgd", 0);
    }

    private static ListingDetails details(
            String title,
            String subtitle,
            String description,
            String coverImage,
            List<String> images,
            String category,
            BigDecimal originalPrice,
            String currency,
            int stock) {
        return new ListingDetails(
                title,
                subtitle,
                description,
                coverImage,
                images,
                category,
                originalPrice,
                currency,
                stock);
    }

    private static BigDecimal price() {
        return new BigDecimal("12.00");
    }

    private static void assertInvalid(ThrowingCallable construction) {
        assertThatThrownBy(construction)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(CatalogErrorCode.PRODUCT_INVALID);
    }
}
