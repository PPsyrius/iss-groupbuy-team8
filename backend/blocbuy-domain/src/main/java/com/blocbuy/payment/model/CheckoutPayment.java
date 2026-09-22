package com.blocbuy.payment.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable payment attempt; provider references cannot be rebound after confirmation. */
public record CheckoutPayment(
        UUID id,
        CheckoutQuote quote,
        String requestKey,
        Instant createdAt,
        Instant sessionExpiresAt,
        String successUrl,
        String cancelUrl,
        String sessionId,
        String checkoutUrl,
        String providerPaymentIntentId,
        Status status,
        int version) {
    public enum Status {
        CREATED,
        SUCCEEDED,
        FAILED,
        CANCELED
    }

    public CheckoutPayment {
        Objects.requireNonNull(id);
        Objects.requireNonNull(quote);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(sessionExpiresAt);
        Objects.requireNonNull(status);
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("Invalid request key");
        }
        if (version < 0 || sessionExpiresAt.isAfter(quote.quoteValidUntil())) {
            throw new IllegalArgumentException("Invalid payment snapshot");
        }
    }

    public CheckoutPayment bind(String session, String url, String intent) {
        if (session == null
                || session.isBlank()
                || (sessionId != null && !sessionId.equals(session))
                || (providerPaymentIntentId != null
                        && intent != null
                        && !providerPaymentIntentId.equals(intent))) {
            throw new IllegalStateException("Provider reference mismatch");
        }
        return new CheckoutPayment(
                id,
                quote,
                requestKey,
                createdAt,
                sessionExpiresAt,
                successUrl,
                cancelUrl,
                session,
                url == null ? checkoutUrl : url,
                intent == null ? providerPaymentIntentId : intent,
                status,
                version);
    }

    /** Trusted paid facts may repair cancellation through the audited recovery service. */
    public CheckoutPayment succeed() {
        return withStatus(Status.SUCCEEDED);
    }

    /** Only retrieved expired/unpaid Sessions without a PaymentIntent can use this transition. */
    public CheckoutPayment cancelUnpaid() {
        return status == Status.SUCCEEDED ? this : withStatus(Status.CANCELED);
    }

    /** Caller must verify an expired/unpaid Session with its PaymentIntent canceled by Stripe. */
    public CheckoutPayment failTerminalUnpaid() {
        return status == Status.SUCCEEDED ? this : withStatus(Status.FAILED);
    }

    private CheckoutPayment withStatus(Status next) {
        return new CheckoutPayment(
                id,
                quote,
                requestKey,
                createdAt,
                sessionExpiresAt,
                successUrl,
                cancelUrl,
                sessionId,
                checkoutUrl,
                providerPaymentIntentId,
                next,
                version);
    }

    public String providerKey() {
        return "blocbuy-checkout-" + id;
    }
}
