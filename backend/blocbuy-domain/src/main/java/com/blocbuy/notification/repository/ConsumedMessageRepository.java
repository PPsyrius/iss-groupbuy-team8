package com.blocbuy.notification.repository;

import java.util.UUID;

/**
 * Consumer idempotency ledger (backend ADR 0007). The marker must be written in the same local
 * transaction as the consumer's effects, so a rollback leaves the event unconsumed and redelivery
 * applies it again.
 */
public interface ConsumedMessageRepository {
    /**
     * Records that {@code consumer} applied the integration event {@code eventId}.
     *
     * @return {@code true} only if this call inserted the marker; {@code false} if the event was
     *     already consumed (a concurrent first delivery blocks until the other transaction ends)
     */
    boolean markConsumed(UUID eventId, String consumer);
}
