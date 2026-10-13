package com.blocbuy.common.model;

import java.time.Instant;
import java.util.UUID;

/** A committed integration event waiting for delivery. */
public record OutboxEvent(
        UUID id,
        String eventType,
        int eventVersion,
        UUID aggregateId,
        String payloadJson,
        String traceId,
        Instant createdAt,
        int attempts) {}
