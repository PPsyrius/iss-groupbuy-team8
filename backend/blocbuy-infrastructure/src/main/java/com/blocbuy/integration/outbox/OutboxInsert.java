package com.blocbuy.integration.outbox;

import java.util.UUID;

/** Persisted integration event fields. PostgreSQL assigns the UUIDv7 event ID. */
public record OutboxInsert(
        String eventType, int eventVersion, UUID aggregateId, String payload, String traceId) {}
