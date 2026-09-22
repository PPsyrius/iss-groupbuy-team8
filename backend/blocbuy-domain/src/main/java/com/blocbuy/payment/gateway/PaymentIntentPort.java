package com.blocbuy.payment.gateway;

import java.util.UUID;

/** Server-owned purchase intent preparation, within the caller's short transaction. */
public interface PaymentIntentPort {
    UUID createIntent(UUID user, String operation, UUID businessId);
}
