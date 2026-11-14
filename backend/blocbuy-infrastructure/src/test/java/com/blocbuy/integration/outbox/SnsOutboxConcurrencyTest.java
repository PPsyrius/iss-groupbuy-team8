package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;
import software.amazon.awssdk.services.sns.model.PublishBatchResultEntry;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
class SnsOutboxConcurrencyTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 9})
    void rejectsInvalidConstructorBounds(int concurrency) {
        assertThatThrownBy(() -> transport(concurrency, SnsOutboxConcurrencyTest::success))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validationAndTransportShutdownDoNotUseOrCloseTheSharedSdkClient() {
        var client = mock(SnsClient.class);
        assertThatThrownBy(() -> new SnsOutboxEventTransport(client, "arn:topic", json(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        try (var transport = new SnsOutboxEventTransport(client, "arn:topic", json(), 4)) {
            assertThat(transport.publish(List.of())).isEmpty();
        }
        verifyNoInteractions(client);
    }

    @Test
    void defaultConstructorSendsSequentiallyOnTheCallerThread() {
        Thread caller = Thread.currentThread();
        var calls = new AtomicInteger();
        var client =
                client(request -> {
                    assertThat(Thread.currentThread()).isSameAs(caller);
                    calls.incrementAndGet();
                    return success(request);
                });
        try (var transport = new SnsOutboxEventTransport(client, "arn:topic", json())) {
            assertThat(transport.publish(events(30))).allMatch(OutboxPublishResult::published);
        }
        assertThat(calls).hasValue(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8})
    void capsConcurrentCallsAcrossOverlappingAndReusedPublishInvocations(int concurrency)
            throws Exception {
        var entered = new CountDownLatch(concurrency);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var calls = new AtomicInteger();
        try (var transport =
                transport(
                        concurrency,
                        request -> {
                            int current = active.incrementAndGet();
                            maximum.accumulateAndGet(current, Math::max);
                            calls.incrementAndGet();
                            entered.countDown();
                            await(release);
                            active.decrementAndGet();
                            return success(request);
                        })) {
            var first = publishAsync(transport, events(100), () -> true);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var second = publishAsync(transport, events(100), () -> true);
                release.countDown();
                assertThat(first.get(5, TimeUnit.SECONDS)).allMatch(OutboxPublishResult::published);
                assertThat(second.get(5, TimeUnit.SECONDS))
                        .allMatch(OutboxPublishResult::published);
                assertThat(transport.publish(events(10))).allMatch(OutboxPublishResult::published);
            } finally {
                release.countDown();
            }
        }
        assertThat(maximum).hasValue(concurrency);
        assertThat(active).hasValue(0);
        assertThat(calls).hasValue(21);
    }

    @Test
    void parallelResultsKeepInputOrderAndIsolateSerializationAndProviderFailures() {
        var events = new ArrayList<>(events(41));
        OutboxEvent invalid = events.get(3);
        events.set(
                3,
                new OutboxEvent(
                        invalid.id(),
                        invalid.eventType(),
                        invalid.eventVersion(),
                        invalid.aggregateId(),
                        "{invalid",
                        invalid.traceId(),
                        invalid.createdAt(),
                        0));
        var sizes = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
        try (var transport =
                transport(
                        4,
                        request -> {
                            var entries = request.publishBatchRequestEntries();
                            sizes.add(entries.size());
                            if (entries.getFirst().id().equals(events.get(10).id().toString())) {
                                throw new IllegalStateException("provider batch unavailable");
                            }
                            if (entries.getFirst().id().equals(events.get(20).id().toString())) {
                                return PublishBatchResponse.builder()
                                        .successful(
                                                entries.subList(2, entries.size()).stream()
                                                        .map(entry ->
                                                                PublishBatchResultEntry.builder()
                                                                        .id(entry.id())
                                                                        .build())
                                                        .toList())
                                        .failed(
                                                BatchResultErrorEntry.builder()
                                                        .id(entries.getFirst().id())
                                                        .code("Throttled")
                                                        .build())
                                        .build();
                            }
                            return success(request);
                        })) {
            var results = transport.publish(events);
            assertThat(results)
                    .extracting(OutboxPublishResult::eventId)
                    .containsExactlyElementsOf(events.stream().map(OutboxEvent::id).toList());
            assertThat(results.get(3).attempted()).isTrue();
            assertThat(results.get(3).published()).isFalse();
            assertThat(results.subList(10, 20))
                    .allMatch(result ->
                            result.attempted()
                                    && "IllegalStateException".equals(result.failureReason()));
            assertThat(results.get(20).failureReason()).isEqualTo("Throttled");
            assertThat(results.get(21).failureReason()).isEqualTo("SNS batch failure");
            assertThat(results.stream().filter(OutboxPublishResult::published)).hasSize(28);
        }
        assertThat(sizes).containsExactlyInAnyOrder(9, 10, 10, 10, 1);
    }

    @Test
    void leaseLossJoinsInflightCallsAndSkipsEveryLaterWave() throws Exception {
        var lease = new AtomicBoolean(true);
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var transport =
                transport(
                        4,
                        request -> {
                            calls.incrementAndGet();
                            entered.countDown();
                            await(release);
                            return success(request);
                        })) {
            var publishing = publishAsync(transport, events(100), lease::get);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                lease.set(false);
                assertThat(publishing).isNotDone();
            } finally {
                release.countDown();
            }
            var results = publishing.get(5, TimeUnit.SECONDS);
            assertThat(results.subList(0, 40)).allMatch(OutboxPublishResult::published);
            assertThat(results.subList(40, 100)).noneMatch(OutboxPublishResult::attempted);
        }
        assertThat(calls).hasValue(4);
    }

    @Test
    void rechecksLeaseInsideWorkerBeforeCallingSdk() {
        var calls = new AtomicInteger();
        Thread caller = Thread.currentThread();
        try (var transport =
                transport(
                        4,
                        request -> {
                            calls.incrementAndGet();
                            return success(request);
                        })) {
            assertThat(transport.publish(events(20), () -> Thread.currentThread() == caller))
                    .noneMatch(OutboxPublishResult::attempted);
        }
        assertThat(calls).hasValue(0);
    }

    @Test
    void interruptedCallerStillJoinsInflightCallsAfterLeaseCancellation() throws Exception {
        var lease = new AtomicBoolean(true);
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var callerInterrupted = new AtomicBoolean();
        try (var transport =
                transport(
                        2,
                        request -> {
                            calls.incrementAndGet();
                            entered.countDown();
                            await(release);
                            return success(request);
                        })) {
            var task =
                    new java.util.concurrent.FutureTask<>(() -> {
                        var results = transport.publish(events(100), lease::get);
                        callerInterrupted.set(Thread.currentThread().isInterrupted());
                        return results;
                    });
            Thread caller = Thread.ofPlatform().start(task);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                lease.set(false);
                caller.interrupt();
                assertThat(task.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            var results = task.get(5, TimeUnit.SECONDS);
            assertThat(results.subList(0, 20)).allMatch(OutboxPublishResult::published);
            assertThat(results.subList(20, 100)).noneMatch(OutboxPublishResult::attempted);
            caller.join(5_000);
            assertThat(caller.isAlive()).isFalse();
        }
        assertThat(calls).hasValue(2);
        assertThat(callerInterrupted).isTrue();
    }

    @Test
    void callerFailureFencesAdmissionAndJoinsAlreadyAdmittedWork() throws Exception {
        var entered = new CountDownLatch(1);
        var failed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var checks = new AtomicInteger();
        var calls = new AtomicInteger();
        try (var transport =
                transport(
                        4,
                        request -> {
                            calls.incrementAndGet();
                            entered.countDown();
                            await(release);
                            return success(request);
                        })) {
            var publishing =
                    publishAsync(
                            transport,
                            events(100),
                            () -> {
                                if (!Thread.currentThread()
                                                .getName()
                                                .startsWith("outbox-sns-publish-")
                                        && checks.incrementAndGet() == 3) {
                                    await(entered);
                                    failed.countDown();
                                    throw new IllegalStateException("lease signal unavailable");
                                }
                                return true;
                            });
            try {
                assertThat(failed.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(publishing).isNotDone();
            } finally {
                release.countDown();
            }
            assertThatThrownBy(() -> publishing.get(5, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(IllegalStateException.class);
        }
        assertThat(calls).hasValue(1);
    }

    @Test
    void shutdownStopsNewWorkAndWaitsForInflightSdkCallsWithoutInterruptingThem() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var interrupted = new AtomicBoolean();
        var transport =
                transport(
                        2,
                        request -> {
                            calls.incrementAndGet();
                            entered.countDown();
                            try {
                                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            } catch (InterruptedException failure) {
                                interrupted.set(true);
                                Thread.currentThread().interrupt();
                            }
                            return success(request);
                        });
        var publishing = publishAsync(transport, events(100), () -> true);
        Thread closing = null;
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            // close sets its stop flag before waiting for this held publication lock.
            closing = Thread.ofPlatform().start(transport::close);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (closing.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(closing.getState()).isEqualTo(Thread.State.WAITING);
        } finally {
            release.countDown();
        }
        assertThat(publishing.get(5, TimeUnit.SECONDS).subList(20, 100))
                .noneMatch(OutboxPublishResult::attempted);
        closing.join(5_000);
        assertThat(closing.isAlive()).isFalse();
        assertThat(transport.publish(events(10))).noneMatch(OutboxPublishResult::attempted);
        transport.close();
        assertThat(interrupted).isFalse();
        assertThat(calls).hasValue(2);
    }

    @Test
    void unexpectedWorkerFailureFencesLaterWavesAndJoinsOtherInflightCalls() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var transport =
                transport(
                        2,
                        request -> {
                            int call = calls.incrementAndGet();
                            entered.countDown();
                            await(entered);
                            if (call == 1) {
                                throw new AssertionError("unexpected worker failure");
                            }
                            await(release);
                            return success(request);
                        })) {
            var publishing = publishAsync(transport, events(100), () -> true);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(publishing).isNotDone();
            } finally {
                release.countDown();
            }
            assertThatThrownBy(() -> publishing.get(5, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(AssertionError.class);
        }
        assertThat(calls).hasValue(2);
    }

    @Test
    void propagatesTraceContextWithoutChangingTheCallerContext() {
        MDC.put("traceId", "producer-test");
        try (var transport =
                transport(
                        2,
                        request -> {
                            assertThat(MDC.get("traceId")).isEqualTo("producer-test");
                            return success(request);
                        })) {
            assertThat(transport.publish(events(20))).allMatch(OutboxPublishResult::published);
            assertThat(MDC.get("traceId")).isEqualTo("producer-test");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void earlyWorkerFailureFencesAdmissionBeyondTheCurrentWave() {
        var calls = new AtomicInteger();
        try (var transport =
                transport(
                        8,
                        request -> {
                            calls.incrementAndGet();
                            throw new AssertionError("early worker failure");
                        })) {
            assertThatThrownBy(() -> transport.publish(events(100)))
                    .hasRootCauseInstanceOf(AssertionError.class);
        }
        assertThat(calls.get()).isBetween(1, 8);
    }

    @Test
    void measuresIsolatedTransportWithControlledProviderLatency() {
        long sequential = timedPublish(1);
        long parallel = timedPublish(4);
        log.info(
                "Synthetic SNS transport benchmark: 100 events, 10 calls, 30ms/call; "
                        + "concurrency1={}ms concurrency4={}ms; excludes database and AWS",
                TimeUnit.NANOSECONDS.toMillis(sequential),
                TimeUnit.NANOSECONDS.toMillis(parallel));
        // Timing is diagnostic, not a flaky CI speed assertion. Cap/overlap have deterministic
        // tests.
    }

    private static long timedPublish(int concurrency) {
        try (var transport =
                transport(
                        concurrency,
                        request -> {
                            try {
                                Thread.sleep(30);
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(
                                        "interrupted synthetic provider", failure);
                            }
                            return success(request);
                        })) {
            long start = System.nanoTime();
            assertThat(transport.publish(events(100))).allMatch(OutboxPublishResult::published);
            return System.nanoTime() - start;
        }
    }

    private static CompletableFuture<List<OutboxPublishResult>> publishAsync(
            SnsOutboxEventTransport transport,
            List<OutboxEvent> events,
            java.util.function.BooleanSupplier lease) {
        return CompletableFuture.supplyAsync(() -> transport.publish(events, lease));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static SnsOutboxEventTransport transport(
            int concurrency, Function<PublishBatchRequest, PublishBatchResponse> send) {
        return new SnsOutboxEventTransport(client(send), "arn:topic", json(), concurrency);
    }

    private static SnsClient client(Function<PublishBatchRequest, PublishBatchResponse> send) {
        var client = mock(SnsClient.class);
        when(client.publishBatch(any(PublishBatchRequest.class)))
                .thenAnswer(call -> send.apply(call.getArgument(0)));
        return client;
    }

    private static PublishBatchResponse success(PublishBatchRequest request) {
        return PublishBatchResponse.builder()
                .successful(
                        request.publishBatchRequestEntries().stream()
                                .map(entry ->
                                        PublishBatchResultEntry.builder().id(entry.id()).build())
                                .toList())
                .build();
    }

    private static List<OutboxEvent> events(int count) {
        return SqsOutboxEventTransportTest.events(count);
    }

    private static tools.jackson.databind.ObjectMapper json() {
        return JsonMapper.builder().build();
    }
}
