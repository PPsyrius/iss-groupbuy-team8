package com.blocbuy.groupbuy.model;

/**
 * GroupDeal lifecycle status (biz_group_deal.status). Allowed values are application-managed per
 * the groupbuy DDL; persisted codes are stable lowercase strings.
 */
public enum DealStatus {
    OPEN("open"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    EXPIRED("expired"),
    CANCELED("canceled");

    private final String code;

    DealStatus(String code) {
        this.code = code;
    }

    /** Stored code, e.g. {@code "open"}. */
    public String code() {
        return code;
    }

    /** Reverse lookup; unknown codes fail fast instead of silently mapping. */
    public static DealStatus fromCode(String code) {
        for (DealStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown deal status code: " + code);
    }
}
