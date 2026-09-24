package com.blocbuy.payment.gateway;

import java.util.UUID;

/** Server-owned purchase intent preparation, within the caller's short transaction. */
public interface PaymentIntentPort {

    /**
     * Issues an intent.
     *
     * @param inviteRef an opaque UC7 invite reference passed through untouched, or null; payment
     *     never interprets it, and the implementation ignores one it cannot attribute
     */
    UUID createIntent(UUID user, String operation, UUID businessId, String inviteRef);

    /** Issues an unattributed intent. */
    default UUID createIntent(UUID user, String operation, UUID businessId) {
        return createIntent(user, operation, businessId, null);
    }
}
