package com.blocbuy.common.model;

import java.util.UUID;

/** Per-event result returned by an external transport adapter. */
public record OutboxPublishResult(
        UUID eventId, boolean published, String failureReason, boolean attempted) {

    public static OutboxPublishResult published(UUID eventId) {
        return new OutboxPublishResult(eventId, true, null, true);
    }

    public static OutboxPublishResult failed(UUID eventId, String reason) {
        return new OutboxPublishResult(eventId, false, reason, true);
    }

    public static OutboxPublishResult skipped(UUID eventId) {
        return new OutboxPublishResult(eventId, false, null, false);
    }
}
