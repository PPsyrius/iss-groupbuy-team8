package com.blocbuy.catalog.model;

/**
 * Listing pricing resolution mode (biz_product.pricing_mode). Selects the {@code PricingStrategy}
 * used to resolve the displayed price pair at read time; persisted codes are stable lowercase
 * strings. New pricing modes add an enum constant plus a strategy — browse logic stays untouched.
 */
public enum PricingMode {
    /** Individual price vs group-buy price from the best active deal. */
    DUAL("dual");

    private final String code;

    PricingMode(String code) {
        this.code = code;
    }

    /** Stored code, e.g. {@code "dual"}. */
    public String code() {
        return code;
    }

    /** Reverse lookup; unknown codes fail fast instead of silently mapping. */
    public static PricingMode fromCode(String code) {
        for (PricingMode mode : values()) {
            if (mode.code.equals(code)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("unknown pricing mode code: " + code);
    }
}
