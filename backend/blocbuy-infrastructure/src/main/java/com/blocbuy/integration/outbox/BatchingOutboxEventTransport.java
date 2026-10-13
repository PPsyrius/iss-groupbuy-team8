package com.blocbuy.integration.outbox;

import com.blocbuy.common.gateway.OutboxEventTransport;
import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

/** Shared bounded batching, failure isolation and cooperative lease cancellation. */
@Slf4j
abstract class BatchingOutboxEventTransport<E> implements OutboxEventTransport {

    private static final int PROVIDER_BATCH_SIZE = 10;

    @Override
    public final List<OutboxPublishResult> publish(
            List<OutboxEvent> events, BooleanSupplier leaseValid) {
        Map<UUID, OutboxPublishResult> results = new HashMap<>();
        for (int start = 0;
                start < events.size() && leaseValid.getAsBoolean();
                start += PROVIDER_BATCH_SIZE) {
            List<OutboxEvent> batch =
                    events.subList(start, Math.min(start + PROVIDER_BATCH_SIZE, events.size()));
            List<OutboxEvent> sendable = new ArrayList<>();
            List<E> entries = new ArrayList<>();
            for (OutboxEvent event : batch) {
                try {
                    entries.add(entry(event));
                    sendable.add(event);
                } catch (RuntimeException failure) {
                    log.warn(
                            "Outbox event could not be serialized: eventId={}",
                            event.id(),
                            failure);
                    results.put(
                            event.id(),
                            OutboxPublishResult.failed(
                                    event.id(), failure.getClass().getSimpleName()));
                }
            }
            if (entries.isEmpty() || !leaseValid.getAsBoolean()) {
                continue;
            }
            publishBatch(sendable, entries, results);
        }
        return events.stream()
                .map(
                        event ->
                                results.getOrDefault(
                                        event.id(), OutboxPublishResult.skipped(event.id())))
                .toList();
    }

    private void publishBatch(
            List<OutboxEvent> events, List<E> entries, Map<UUID, OutboxPublishResult> results) {
        try {
            for (OutboxPublishResult result : send(events, entries)) {
                results.put(result.eventId(), result);
            }
        } catch (RuntimeException failure) {
            log.warn("Outbox provider batch failed for {} events", events.size(), failure);
            for (OutboxEvent event : events) {
                results.put(
                        event.id(),
                        OutboxPublishResult.failed(event.id(), failure.getClass().getSimpleName()));
            }
        }
    }

    protected abstract E entry(OutboxEvent event);

    protected abstract List<OutboxPublishResult> send(List<OutboxEvent> events, List<E> entries);

    protected static List<OutboxPublishResult> results(
            List<OutboxEvent> events,
            Set<String> succeeded,
            Map<String, String> failures,
            String missingReason) {
        return events.stream()
                .map(
                        event ->
                                succeeded.contains(event.id().toString())
                                        ? OutboxPublishResult.published(event.id())
                                        : OutboxPublishResult.failed(
                                                event.id(),
                                                failures.getOrDefault(
                                                        event.id().toString(), missingReason)))
                .toList();
    }
}
