package com.blocbuy.payment.gateway;

import com.blocbuy.payment.model.CheckoutPayment;
import java.time.Instant;
import java.util.Optional;

/** Hosted Checkout capability, separate from the legacy PaymentIntent API. No SDK types. */
public interface CheckoutGateway {
    Session createCheckoutSession(CheckoutPayment payment);

    Session retrieveCheckoutSession(String sessionId);

    Session expireCheckoutSession(String sessionId);

    Optional<Event> verifyCheckoutWebhook(String rawBody, String signature);

    record Session(
            String id,
            String paymentId,
            String intentId,
            String paymentIntentId,
            long amountMinor,
            String currency,
            String status,
            String paymentStatus,
            boolean liveMode,
            String url,
            Instant expiresAt,
            String paymentIntentStatus) {
        /** A response without expanded intent facts cannot establish terminal failure. */
        public Session(
                String id,
                String paymentId,
                String intentId,
                String paymentIntentId,
                long amountMinor,
                String currency,
                String status,
                String paymentStatus,
                boolean liveMode,
                String url,
                Instant expiresAt) {
            this(
                    id,
                    paymentId,
                    intentId,
                    paymentIntentId,
                    amountMinor,
                    currency,
                    status,
                    paymentStatus,
                    liveMode,
                    url,
                    expiresAt,
                    null);
        }

        public boolean paid() {
            return "complete".equals(status) && "paid".equals(paymentStatus);
        }

        public boolean terminalUnpaidFailure() {
            return "expired".equals(status)
                    && "unpaid".equals(paymentStatus)
                    && paymentIntentId != null
                    && "canceled".equals(paymentIntentStatus);
        }
    }

    record Event(String id, String type, String sessionId, boolean liveMode, String account) {}
}
