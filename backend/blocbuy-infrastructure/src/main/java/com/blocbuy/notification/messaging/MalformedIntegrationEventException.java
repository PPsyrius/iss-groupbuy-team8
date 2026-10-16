package com.blocbuy.notification.messaging;

import java.util.UUID;

/**
 * A message that can never be applied as sent. It is left on the queue, so redrive moves it to the
 * DLQ after {@code maxReceiveCount} receives. The message never contains payload values.
 */
public class MalformedIntegrationEventException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final UUID eventId;

    public MalformedIntegrationEventException(UUID eventId, String reason) {
        super(reason);
        this.eventId = eventId;
    }

    /** Envelope eventId when it could be read, otherwise {@code null}. */
    public UUID eventId() {
        return eventId;
    }
}
