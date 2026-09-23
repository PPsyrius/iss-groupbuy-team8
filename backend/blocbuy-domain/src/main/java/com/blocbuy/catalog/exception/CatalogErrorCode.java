package com.blocbuy.catalog.exception;

import com.blocbuy.common.exception.ErrorCode;

/**
 * Catalog domain error codes. Once published, codes are never reused or redefined; messages may be
 * localized via {@link #messageKey()}, codes may not.
 */
public enum CatalogErrorCode implements ErrorCode {
    PRODUCT_INVALID("CATALOG_400_002", 400, "Invalid listing", "error.catalog.productInvalid"),
    PRODUCT_STATE_CONFLICT(
            "CATALOG_409_002",
            409,
            "Listing state does not allow this operation",
            "error.catalog.productStateConflict"),
    PRODUCT_NUMBER_CONFLICT(
            "CATALOG_409_003",
            409,
            "Product number already exists",
            "error.catalog.productNumberConflict"),
    PRODUCT_REFERENCED(
            "CATALOG_409_004",
            409,
            "Listing has business history and cannot be deleted",
            "error.catalog.productReferenced"),
    PRODUCT_VERSION_CONFLICT(
            "CATALOG_412_002",
            412,
            "Listing version precondition failed",
            "error.catalog.productVersionConflict"),
    PRODUCT_NOT_FOUND("CATALOG_404_001", 404, "Product not found", "error.catalog.productNotFound"),
    TEAM_PRICE_INVALID(
            "CATALOG_400_001", 400, "Invalid team price policy", "error.catalog.teamPriceInvalid"),
    TEAM_PRICE_UNAVAILABLE(
            "CATALOG_409_001",
            409,
            "Team price is unavailable",
            "error.catalog.teamPriceUnavailable"),
    TEAM_PRICE_VERSION_CONFLICT(
            "CATALOG_412_001",
            412,
            "Team price policy version conflict",
            "error.catalog.teamPriceVersionConflict"),
    TEAM_PRICE_QUOTE_NOT_FOUND(
            "CATALOG_404_002",
            404,
            "Team price quote not found",
            "error.catalog.teamPriceQuoteNotFound"),
    PRODUCT_NOT_PURCHASABLE(
            "CATALOG_409_005",
            409,
            "Product is not on sale or out of stock",
            "error.catalog.productNotPurchasable"),
    PRODUCT_NOT_STARTABLE(
            "CATALOG_409_006",
            409,
            "Product has no group-buy terms configured, so no team can be started on it",
            "error.catalog.productNotStartable"),
    PRICING_MODE_UNSUPPORTED(
            "CATALOG_500_001",
            500,
            "Unsupported pricing mode",
            "error.catalog.pricingModeUnsupported");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;
    private final String messageKey;

    CatalogErrorCode(String code, int httpStatus, String defaultMessage, String messageKey) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
        this.messageKey = messageKey;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }
}
