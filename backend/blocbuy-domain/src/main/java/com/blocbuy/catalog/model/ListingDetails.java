package com.blocbuy.catalog.model;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Validated editable listing content; identity, counters, lifecycle and pricing mode are
 * server-owned.
 */
public record ListingDetails(
        String title,
        String subtitle,
        String description,
        String coverImage,
        List<String> images,
        String category,
        BigDecimal originalPrice,
        String currency,
        int stock) {

    /** Reject invalid data before any persistence; do not silently round monetary input. */
    public ListingDetails {
        if (title == null || title.isBlank() || title.length() > 256) {
            throw invalid("title", "Must be nonblank and at most 256 characters");
        }
        requireLength(subtitle, 256, "subtitle");
        requireLength(description, 10000, "description");
        requireLength(coverImage, 1024, "coverImage");
        requireLength(category, 64, "category");
        if (originalPrice == null
                || originalPrice.signum() <= 0
                || originalPrice.compareTo(new BigDecimal("9999999999999999.99")) > 0) {
            throw invalid("originalPrice", "Must be positive and at most 9999999999999999.99");
        }
        if (!"sgd".equals(currency)) {
            throw invalid("currency", "Must be SGD (sgd)");
        }
        if (stock < 0) {
            throw invalid("stock", "Must be zero or greater");
        }
        if (images != null
                && (images.size() > 20
                        || images.stream()
                                .anyMatch(
                                        image ->
                                                image == null
                                                        || image.isBlank()
                                                        || image.length() > 1024))) {
            throw invalid(
                    "images",
                    "Must contain at most 20 nonblank image URLs of at most 1024 characters each");
        }
        images = images == null ? List.of() : List.copyOf(images);
        try {
            originalPrice = originalPrice.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw invalid("originalPrice", "Must have at most two decimal places");
        }
    }

    /** Optional descriptive fields may be absent, but cannot exceed storage/API limits. */
    private static void requireLength(String value, int maximum, String field) {
        if (value != null && value.length() > maximum) {
            throw invalid(field, "Must be at most " + maximum + " characters");
        }
    }

    private static BizException invalid(String field, String message) {
        return BizException.forField(CatalogErrorCode.PRODUCT_INVALID, "listing." + field, message);
    }
}
