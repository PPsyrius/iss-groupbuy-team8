package com.blocbuy.order.model;

import java.util.Optional;

/**
 * What a buyer asked for when the server issued a purchase intent: create a team on a product, or
 * enter one that is already open. The wire vocabulary is uppercase because the payment slice's
 * {@code CheckoutQuote} validates {@code START}/{@code JOIN}; unlike order lifecycle status, this
 * is a request kind, not a row state, so it is not subject to the lowercase status convention.
 */
public enum PurchaseOperation {
    START,
    JOIN;

    /** Parses a stored or quoted operation; unknown values are a programming error, not input. */
    public static PurchaseOperation fromCode(String code) {
        return parse(code)
                .orElseThrow(() ->
                        new IllegalArgumentException("unknown purchase operation code: " + code));
    }

    /**
     * Parses request input, where an unknown value is the browser's mistake rather than a
     * programming error. Callers turn the empty result into their own business rejection.
     */
    public static Optional<PurchaseOperation> parse(String code) {
        for (PurchaseOperation operation : values()) {
            if (operation.name().equals(code)) {
                return Optional.of(operation);
            }
        }
        return Optional.empty();
    }

    public String code() {
        return name();
    }
}
