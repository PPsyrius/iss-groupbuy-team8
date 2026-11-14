package com.blocbuy.integration.outbox;

import com.blocbuy.common.gateway.OutboxEventTransport;
import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/** Shared bounded batching, failure isolation and cooperative lease cancellation. */
@Slf4j
abstract class BatchingOutboxEventTransport<E> implements OutboxEventTransport {

    private static final int PROVIDER_BATCH_SIZE = 10;
    private static final PublishingSettings SEQUENTIAL = new PublishingSettings(1, false);
    private final ReentrantLock publishing = new ReentrantLock();
    private final int concurrency;
    private final boolean boundedAdmission;
    private volatile boolean closed;

    protected BatchingOutboxEventTransport() {
        this(SEQUENTIAL);
    }

    protected BatchingOutboxEventTransport(PublishingSettings settings) {
        this.concurrency = settings.concurrency();
        this.boundedAdmission = settings.boundedAdmission();
    }

    /** Immutable validation completes before the extensible transport's constructor is entered. */
    protected record PublishingSettings(int concurrency, boolean boundedAdmission) {

        protected PublishingSettings {
            if (concurrency < 1 || concurrency > 8) {
                throw new IllegalArgumentException(
                        "SNS publish concurrency must be between 1 and 8");
            }
        }
    }

    @Override
    public final List<OutboxPublishResult> publish(
            List<OutboxEvent> events, BooleanSupplier leaseValid) {
        if (!boundedAdmission) {
            return publishLocked(events, leaseValid);
        }
        // Serial admission makes the cap transport-wide, including overlapping/reused callers.
        publishing.lock();
        try {
            return publishLocked(events, leaseValid);
        } finally {
            publishing.unlock();
        }
    }

    private List<OutboxPublishResult> publishLocked(
            List<OutboxEvent> events, BooleanSupplier leaseValid) {
        Map<UUID, OutboxPublishResult> results = new ConcurrentHashMap<>();
        var failed = new AtomicBoolean();
        BooleanSupplier active = () -> !closed && !failed.get() && leaseValid.getAsBoolean();
        var pending = new ArrayList<CompletableFuture<Void>>();
        // No executor at the default setting; otherwise only one bounded wave is admitted.
        try (ExecutorService executor =
                concurrency == 1
                        ? null
                        : new ThreadPoolExecutor(
                                concurrency,
                                concurrency,
                                0,
                                TimeUnit.MILLISECONDS,
                                new ArrayBlockingQueue<>(concurrency),
                                Thread.ofPlatform().name("outbox-sns-publish-", 0).factory())) {
            try {
                for (int start = 0;
                        start < events.size() && active.getAsBoolean();
                        start += PROVIDER_BATCH_SIZE) {
                    List<OutboxEvent> batch =
                            events.subList(
                                    start, Math.min(start + PROVIDER_BATCH_SIZE, events.size()));
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
                    if (entries.isEmpty() || !active.getAsBoolean()) {
                        continue;
                    }
                    if (executor == null) {
                        publishBatch(sendable, entries, results);
                    } else {
                        Map<String, String> context = MDC.getCopyOfContextMap();
                        pending.add(
                                CompletableFuture.runAsync(
                                        () -> {
                                            try {
                                                if (context != null) {
                                                    MDC.setContextMap(context);
                                                }
                                                if (active.getAsBoolean()) {
                                                    publishBatch(sendable, entries, results);
                                                }
                                            } catch (RuntimeException | Error failure) {
                                                // Unexpected worker failures fence subsequent
                                                // waves, not ordinary SNS failures.
                                                failed.set(true);
                                                throw failure;
                                            } finally {
                                                MDC.clear();
                                            }
                                        },
                                        executor));
                        if (pending.size() == concurrency) {
                            join(pending);
                            pending.clear();
                        }
                    }
                }
            } catch (RuntimeException | Error failure) {
                failed.set(true);
                throw failure;
            } finally {
                // join is uninterruptible: an interrupted scheduler must not abandon SDK work.
                join(pending);
            }
        }
        return events.stream()
                .map(event ->
                        results.getOrDefault(event.id(), OutboxPublishResult.skipped(event.id())))
                .toList();
    }

    private static void join(List<CompletableFuture<Void>> pending) {
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
    }

    /** Stops admission immediately and joins outstanding SDK calls without interrupting them. */
    protected final void closePublisher() {
        closed = true;
        publishing.lock();
        // The active publisher has joined its work and closed its executor before this lock.
        publishing.unlock();
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
                .map(event ->
                        succeeded.contains(event.id().toString())
                                ? OutboxPublishResult.published(event.id())
                                : OutboxPublishResult.failed(
                                        event.id(),
                                        failures.getOrDefault(
                                                event.id().toString(), missingReason)))
                .toList();
    }
}
