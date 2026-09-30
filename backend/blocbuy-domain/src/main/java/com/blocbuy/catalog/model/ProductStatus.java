package com.blocbuy.catalog.model;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.common.exception.BizException;

/**
 * Product lifecycle status (biz_product.status). Allowed values are application-managed per the
 * catalog DDL; persisted codes are stable lowercase strings. Only {@link #ON_SALE} products are
 * visible in the buyer listing browse.
 */
public enum ProductStatus {
    DRAFT("draft") {
        /** First publication leaves draft permanently. */
        @Override
        ProductStatus publish() {
            return ON_SALE;
        }

        /** Only never-published drafts admit deletion. References are checked by the use case. */
        @Override
        CatalogErrorCode deletionError() {
            return null;
        }

        /** No accepted commercial history exists before publication. */
        @Override
        void requireCommercialEdit(Product current, ListingDetails next) {
            /* Draft terms may change. */
        }
    },
    ON_SALE("on_sale") {
        /** Delisting hides future browse/quotes without changing accepted history. */
        @Override
        ProductStatus delist() {
            return OFF_SALE;
        }
    },
    OFF_SALE("off_sale") {
        /** Re-publication retains the product identity and historical commercial terms. */
        @Override
        ProductStatus publish() {
            return ON_SALE;
        }
    };

    private final String code;

    ProductStatus(String code) {
        this.code = code;
    }

    /** State-pattern default denies publication unless the concrete state allows it. */
    ProductStatus publish() {
        throw new BizException(CatalogErrorCode.PRODUCT_STATE_CONFLICT);
    }

    /** State-pattern default denies delisting unless the concrete state allows it. */
    ProductStatus delist() {
        throw new BizException(CatalogErrorCode.PRODUCT_STATE_CONFLICT);
    }

    /** Published states never become deletable again. */
    CatalogErrorCode deletionError() {
        return CatalogErrorCode.PRODUCT_STATE_CONFLICT;
    }

    /** Catalog editing must never act as a stock adjustment or reprice a published product. */
    void requireCommercialEdit(Product current, ListingDetails next) {
        if (current.originalPrice().compareTo(next.originalPrice()) != 0
                || !current.currency().equals(next.currency())
                || current.stock() != next.stock()) {
            throw new BizException(CatalogErrorCode.PRODUCT_STATE_CONFLICT);
        }
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
