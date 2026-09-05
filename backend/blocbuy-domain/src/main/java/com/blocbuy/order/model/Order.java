package com.blocbuy.order.model;

/**
 * Order aggregate root — placeholder.
 *
 * <p>Rules: the aggregate root is the only entry point for mutating its state; state transitions go
 * through behavioral methods that validate the current state (no public {@code setStatus}); no
 * Lombok {@code @Data}; monetary amounts use {@code BigDecimal}/a Money value object.
 * TODO(TEAM8-order): model fields, invariants, and behavior when the first order use case lands.
 */
public final class Order {

    private final String id;

    private Order(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
    }

    /** Factory: objects are valid from birth. */
    public static Order of(String id) {
        return new Order(id);
    }

    public String id() {
        return id;
    }
}
