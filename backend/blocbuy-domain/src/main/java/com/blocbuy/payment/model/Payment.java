package com.blocbuy.payment.model;

/**
 * Payment aggregate root — placeholder.
 *
 * <p>Rules: the aggregate root is the only entry point for mutating its state; state transitions
 * (created → succeeded/failed/canceled, refunds) go through behavioral methods that validate the
 * current state (no public {@code setStatus}); no Lombok {@code @Data}; monetary amounts use {@code
 * BigDecimal} major units with an explicit currency — conversion to Stripe minor units happens only
 * in the infrastructure gateway. Payment facts live in PostgreSQL, never solely in Redis or derived
 * from provider callbacks without local persistence. TODO(TEAM8-payment): model fields (paymentNo,
 * orderId, amount, currency, status, provider references), invariants (amount > 0, one active
 * payment per order), and behavior when the first payment use case lands.
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
