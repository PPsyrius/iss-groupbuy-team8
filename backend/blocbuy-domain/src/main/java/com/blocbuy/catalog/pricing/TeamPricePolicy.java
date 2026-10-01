package com.blocbuy.catalog.pricing;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.common.exception.BizException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Immutable catalog revision. The latest revision replaces, never falls back to, older policy. */
public record TeamPricePolicy(
        UUID productId,
        BigDecimal amount,
        String currency,
        Instant validFrom,
        Instant validUntil,
        int version) {

    private static final String AMOUNT_FIELD = "amount";

    public TeamPricePolicy {
        validFrom = validFrom == null ? null : validFrom.truncatedTo(ChronoUnit.MICROS);
        validUntil = validUntil == null ? null : validUntil.truncatedTo(ChronoUnit.MICROS);
        if (productId == null || version < 1) {
            throw new BizException(CatalogErrorCode.TEAM_PRICE_INVALID);
        }
        if (validFrom == null) {
            throw BizException.forField(
                    CatalogErrorCode.TEAM_PRICE_INVALID, "validFrom", "Start time is required");
        }
        if (validUntil == null || !validFrom.isBefore(validUntil)) {
            throw BizException.forField(
                    CatalogErrorCode.TEAM_PRICE_INVALID, "validUntil", "Must be after the start");
        }
        if (!"sgd".equals(currency)) {
            throw BizException.forField(
                    CatalogErrorCode.TEAM_PRICE_INVALID, "currency", "Must be SGD (sgd)");
        }
        amount = exactAmount(amount);
    }

    /** The first quote contract supports SGD only; no rounding or provider currency assumptions. */
    static BigDecimal exactAmount(BigDecimal amount) {
        if (amount == null
                || amount.signum() <= 0
                || amount.compareTo(new BigDecimal("9999999999999999.99")) > 0) {
            throw BizException.forField(
                    CatalogErrorCode.TEAM_PRICE_INVALID,
                    AMOUNT_FIELD,
                    "Must be positive and at most 9999999999999999.99");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw BizException.forField(
                    CatalogErrorCode.TEAM_PRICE_INVALID,
                    AMOUNT_FIELD,
                    "Must have at most two decimal places");
        }
    }

    /** Shared by management and issuance; configuration cannot misprice another product. */
    public void requireCompatible(Product product) {
        if (product == null || !product.isOnSale()) {
            throw new BizException(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE);
        }
        if (!productId.equals(product.id())) {
            throw new BizException(CatalogErrorCode.TEAM_PRICE_INVALID);
        }
        if (!currency.equalsIgnoreCase(product.currency())) {
            throw BizException.forField(
                    CatalogErrorCode.TEAM_PRICE_INVALID,
                    "currency",
                    "Must match the listing currency");
        }
        if (amount.compareTo(product.originalPrice()) >= 0) {
            throw BizException.forField(
                    CatalogErrorCode.TEAM_PRICE_INVALID,
                    AMOUNT_FIELD,
                    "Must be strictly below the solo price");
        }
    }

    /** Exact expiry is excluded; callers supply database wall-clock time after obtaining locks. */
    public void requireQuotable(Product product, Instant now) {
        requireCompatible(product);
        if (now.isBefore(validFrom) || !now.isBefore(validUntil)) {
            throw new BizException(CatalogErrorCode.TEAM_PRICE_UNAVAILABLE);
        }
    }
}
