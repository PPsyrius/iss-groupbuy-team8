package com.blocbuy.notification.event;

import com.blocbuy.notification.model.NotificationEvent;

/**
 * Inbound port for consumed integration events (backend ADR 0007, #446). The SQS consumer lives in
 * the infrastructure module, which may not depend on the application module, so the use case is
 * declared here and implemented by the application layer.
 */
public interface NotificationEventHandler {
    /**
     * Applies {@code event} at most once for {@code consumer}: one local transaction records the
     * consumed-message marker and the notifications. The caller acknowledges the message only after
     * this method returns; any exception means nothing was committed and the message is retried.
     */
    ConsumeOutcome handle(String consumer, NotificationEvent event);

    /** Result of a committed handling; both outcomes are safe to acknowledge. */
    enum ConsumeOutcome {
        /** First delivery: marker and effects committed (possibly zero notifications). */
        APPLIED,
        /** The (eventId, consumer) marker already existed; nothing was written. */
        DUPLICATE
    }
}
