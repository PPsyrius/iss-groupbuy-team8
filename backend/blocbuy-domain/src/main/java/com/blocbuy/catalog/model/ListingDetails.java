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
        if (title == null
                || title.isBlank()
                || title.length() > 256
                || tooLong(subtitle, 256)
                || tooLong(description, 10000)
                || tooLong(coverImage, 1024)
                || tooLong(category, 64)
                || originalPrice == null
                || originalPrice.signum() <= 0
                || originalPrice.compareTo(new BigDecimal("9999999999999999.99")) > 0
                || !"sgd".equals(currency)
                || stock < 0) {
            throw new BizException(CatalogErrorCode.PRODUCT_INVALID);
        }
        if (images != null
                && (images.size() > 20
                        || images.stream()
                                .anyMatch(
                                        image ->
                                                image == null
                                                        || image.isBlank()
                                                        || image.length() > 1024))) {
            throw new BizException(CatalogErrorCode.PRODUCT_INVALID);
        }
        images = images == null ? List.of() : List.copyOf(images);
        try {
            originalPrice = originalPrice.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BizException(CatalogErrorCode.PRODUCT_INVALID);
        }
    }

    /** Optional descriptive fields may be absent, but cannot exceed storage/API limits. */
    private static boolean tooLong(String value, int maximum) {
        return value != null && value.length() > maximum;
    }
}
