package com.blocbuy.payment.gateway;

import java.util.Optional;
import java.util.UUID;

/** Server-owned purchase intent preparation and read-back, within the caller's transaction. */
public interface PaymentIntentPort {

    /**
     * Issues an intent.
     *
     * @param inviteRef an opaque UC7 invite reference passed through untouched, or null; payment
     *     never interprets it, and the implementation ignores one it cannot attribute
     * @param quoteId an opaque price-quote id passed through untouched, or null; payment never
     *     interprets it, and the implementation prices the intent at that quote or refuses it
     */
    UUID createIntent(UUID user, String operation, UUID businessId, String inviteRef, UUID quoteId);

    /** Issues an intent priced by the server's current terms rather than a shown quote. */
    default UUID createIntent(UUID user, String operation, UUID businessId, String inviteRef) {
        return createIntent(user, operation, businessId, inviteRef, null);
    }

    /** Issues an unattributed intent. */
    default UUID createIntent(UUID user, String operation, UUID businessId) {
        return createIntent(user, operation, businessId, null);
    }

    /**
     * Reads back the product an issued intent was priced for, for the buyer's receipt.
     *
     * @return the product id, or empty when the intent does not exist or belongs to another user; a
     *     receipt must still be answered without it
     */
    Optional<UUID> purchasedProduct(UUID user, UUID intentId);
}
