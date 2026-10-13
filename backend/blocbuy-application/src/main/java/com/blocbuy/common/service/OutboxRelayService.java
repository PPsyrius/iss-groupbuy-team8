package com.blocbuy.common.service;

import com.blocbuy.common.gateway.OutboxEventTransport;
import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import com.blocbuy.common.repository.OutboxRelayRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Coordinates one bounded outbox delivery batch without holding a database transaction over I/O.
 */
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    static final int BATCH_SIZE = 100;
    static final int MAX_ATTEMPTS = 10;

    private final OutboxRelayRepository repository;
    private final OutboxEventTransport transport;

    public int relayBatch() {
        List<OutboxEvent> events = repository.findDue(BATCH_SIZE);
        if (events.isEmpty()) {
            return 0;
        }

        List<OutboxPublishResult> results;
        try {
            results = transport.publish(events);
            if (results == null || results.size() != events.size()) {
                throw new IllegalStateException("Transport returned an incomplete result batch");
            }
            for (int index = 0; index < events.size(); index++) {
                if (!events.get(index).id().equals(results.get(index).eventId())) {
                    throw new IllegalStateException(
                            "Transport returned results for different events");
                }
            }
        } catch (RuntimeException failure) {
            log.warn("Outbox transport failed for {} events", events.size(), failure);
            results = failedResults(events, failure.getClass().getSimpleName());
        }

        int updated = repository.recordResults(results, MAX_ATTEMPTS);
        for (int index = 0; index < events.size(); index++) {
            OutboxEvent event = events.get(index);
            OutboxPublishResult result = results.get(index);
            if (!result.published() && event.attempts() + 1 >= MAX_ATTEMPTS) {
                log.error(
                        "Outbox event exhausted retries: eventId={} traceId={}",
                        event.id(),
                        event.traceId());
            }
        }
        return updated;
    }

    private static List<OutboxPublishResult> failedResults(
            List<OutboxEvent> events, String reason) {
        List<OutboxPublishResult> results = new ArrayList<>(events.size());
        for (OutboxEvent event : events) {
            results.add(OutboxPublishResult.failed(event.id(), reason));
        }
        return results;
    }
}
