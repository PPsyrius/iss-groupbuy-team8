package com.blocbuy.events.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stable transport envelope shared by commerce producers and downstream consumers. */
public record IntegrationEventEnvelope<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String producer,
        String traceId,
        UUID aggregateId,
        T payload) {

    public IntegrationEventEnvelope {
        Objects.requireNonNull(eventId);
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(occurredAt);
        if (producer == null || producer.isBlank()) {
            throw new IllegalArgumentException("producer must not be blank");
        }
        if (traceId == null || traceId.isBlank() || traceId.length() > 64) {
            throw new IllegalArgumentException("traceId must contain 1 to 64 characters");
        }
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(payload);
    }
}
