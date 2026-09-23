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

    /**
     * Only an {@link #OPEN} deal may move, and never to itself. Terminal states never re-enter. The
     * full matrix is decided here — services must never branch on status with {@code if/else}.
     */
    public boolean canTransitionTo(DealStatus target) {
        return this == OPEN && target != null && target != OPEN;
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
