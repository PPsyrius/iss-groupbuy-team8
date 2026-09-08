package com.blocbuy.catalog.model;

/**
 * Product lifecycle status (biz_product.status). Allowed values are application-managed per the
 * catalog DDL; persisted codes are stable lowercase strings. Only {@link #ON_SALE} products are
 * visible in the buyer listing browse.
 */
public enum ProductStatus {
    DRAFT("draft"),
    ON_SALE("on_sale"),
    OFF_SALE("off_sale");

    private final String code;

    ProductStatus(String code) {
        this.code = code;
    }

    /** Stored code, e.g. {@code "on_sale"}. */
    public String code() {
        return code;
    }

    /** Reverse lookup; unknown codes fail fast instead of silently mapping. */
    public static ProductStatus fromCode(String code) {
        for (ProductStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown product status code: " + code);
    }
}
