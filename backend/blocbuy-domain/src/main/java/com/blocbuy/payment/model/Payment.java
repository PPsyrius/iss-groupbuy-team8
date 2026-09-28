package com.blocbuy.payment.model;

/**
 * ID-only legacy payment view retained for compatibility callers. The implemented checkout
 * aggregate, including its persisted pricing, status and provider references, is {@link
 * CheckoutPayment}; refund obligations are tracked separately.
 */
public final class Payment {

    private final String id;

    private Payment(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
    }

    /** Factory: objects are valid from birth. */
    public static Payment of(String id) {
        return new Payment(id);
    }

    public String id() {
        return id;
    }
}
