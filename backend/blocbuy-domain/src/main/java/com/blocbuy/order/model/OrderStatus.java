package com.blocbuy.order.model;

/**
 * Order lifecycle status (biz_order.status). Allowed values are application-managed per the order
 * DDL; persisted codes are stable lowercase strings. Orders are born {@link #PENDING} after a
 * verified payment and end {@link #CONFIRMED} (team succeeded) or {@link #CANCELLED} (team
 * expired); terminal states never re-enter.
 */
public enum OrderStatus {
    PENDING("pending"),
    CONFIRMED("confirmed"),
    CANCELLED("cancelled");

    private final String code;

    OrderStatus(String code) {
        this.code = code;
    }

    /**
     * Only a pending order may move, and never to itself. The full matrix is decided here —
     * services must never branch on status with {@code if/else}.
     */
    public boolean canTransitionTo(OrderStatus target) {
        return this == PENDING && target != null && target != PENDING;
    }

    /** Stored code, e.g. {@code "pending"}. */
    public String code() {
        return code;
    }

    /** Reverse lookup; unknown codes fail fast instead of silently mapping. */
    public static OrderStatus fromCode(String code) {
        for (OrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown order status code: " + code);
    }
}
