package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.repository.OutboxRelayRepository;
import com.blocbuy.common.repository.SchedulerLeaseRepository;
import com.blocbuy.common.service.OutboxRelayService;
import com.blocbuy.integration.outbox.SqsOutboxEventTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import tools.jackson.databind.json.JsonMapper;

/** Two relay instances honoring the shared scheduler-lease contract cannot relay concurrently. */
class OutboxRelayLeaseTest {

    @Test
    void onlyTheLeaseHolderRelaysWhileTheOtherInstanceSkips() throws Exception {
        var lock = new InMemoryLease();
        var relay = mock(OutboxRelayService.class);
        var firstEnteredRelay = new CountDownLatch(1);
        var allowFirstRelayToFinish = new CountDownLatch(1);
        var invocations = new AtomicInteger();
        when(relay.relayBatch(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        ignored -> {
                            invocations.incrementAndGet();
                            firstEnteredRelay.countDown();
                            if (!allowFirstRelayToFinish.await(2, TimeUnit.SECONDS)) {
                                throw new AssertionError("first relay did not get released");
                            }
                            return 1;
                        });
        var first = job(lock, "host/first", relay);
        var second = job(lock, "host/second", relay);

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstRun = pool.submit(first::run);
            assertThat(firstEnteredRelay.await(2, TimeUnit.SECONDS)).isTrue();

            second.run();

            assertThat(invocations).hasValue(1);
            allowFirstRelayToFinish.countDown();
            firstRun.get(2, TimeUnit.SECONDS);
        } finally {
            allowFirstRelayToFinish.countDown();
        }

        assertThat(invocations).hasValue(1);
    }

    @Test
    void renewalFailureStopsOldOwnerBeforeTheNextBatchAfterTakeover() throws Exception {
        var owner = new AtomicReference<String>();
        var renewalFailed = new CountDownLatch(1);
        var firstSendEntered = new CountDownLatch(1);
        var releaseFirstSend = new CountDownLatch(1);
        var firstThread = new AtomicReference<Thread>();
        var firstCalls = new AtomicInteger();
        var secondCalls = new AtomicInteger();
        var leases =
                new SchedulerLeaseRepository() {
                    public boolean tryAcquire(String key, String candidate, Duration lease) {
                        return owner.compareAndSet(null, candidate);
                    }

                    public boolean renew(String key, String candidate, Duration lease) {
                        if (candidate.equals("first")) {
                            if (firstSendEntered.getCount() != 0) {
                                return true;
                            }
                            // Simulate the old lease lapsing while its provider call is in flight.
                            owner.compareAndSet(candidate, null);
                            renewalFailed.countDown();
                            return false;
                        }
                        return candidate.equals(owner.get());
                    }

                    public boolean release(String key, String candidate) {
                        return owner.compareAndSet(candidate, null);
                    }
                };
        var repository = mock(OutboxRelayRepository.class);
        var events =
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(
                                index ->
                                        new OutboxEvent(
                                                UUID.randomUUID(),
                                                "TeamExpired",
                                                1,
                                                UUID.randomUUID(),
                                                "{}",
                                                "lease-test",
                                                Instant.now(),
                                                0))
                        .toList();
        when(repository.findDue(100)).thenReturn(events);
        when(repository.recordResults(
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.eq(10)))
                .thenAnswer(call -> call.<java.util.List<?>>getArgument(0).size());
        var client = mock(SqsClient.class);
        when(client.sendMessageBatch(
                        org.mockito.ArgumentMatchers.any(SendMessageBatchRequest.class)))
                .thenAnswer(
                        call -> {
                            if (Thread.currentThread() == firstThread.get()) {
                                firstCalls.incrementAndGet();
                                firstSendEntered.countDown();
                                assertThat(releaseFirstSend.await(5, TimeUnit.SECONDS)).isTrue();
                            } else {
                                secondCalls.incrementAndGet();
                            }
                            SendMessageBatchRequest request = call.getArgument(0);
                            return SendMessageBatchResponse.builder()
                                    .successful(
                                            request.entries().stream()
                                                    .map(
                                                            entry ->
                                                                    SendMessageBatchResultEntry
                                                                            .builder()
                                                                            .id(entry.id())
                                                                            .build())
                                                    .toList())
                                    .build();
                        });
        var relay =
                new OutboxRelayService(
                        repository,
                        new SqsOutboxEventTransport(
                                client, "http://queue", JsonMapper.builder().build()));
        var first = new LeasedJobRunner(leases, new SimpleMeterRegistry(), "first");
        var second = new LeasedJobRunner(leases, new SimpleMeterRegistry(), "second");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstRun =
                    pool.submit(
                            () -> {
                                firstThread.set(Thread.currentThread());
                                first.run(
                                        OutboxRelayJob.JOB,
                                        Duration.ofMillis(600),
                                        relay::relayBatch);
                            });
            try {
                assertThat(firstSendEntered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(renewalFailed.await(5, TimeUnit.SECONDS)).isTrue();
                second.run(OutboxRelayJob.JOB, Duration.ofSeconds(30), relay::relayBatch);
            } finally {
                releaseFirstSend.countDown();
            }
            firstRun.get(5, TimeUnit.SECONDS);
        }
        assertThat(firstCalls).hasValue(1);
        assertThat(secondCalls).hasValue(2);
        var recorded = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(repository, times(2))
                .recordResults(recorded.capture(), org.mockito.ArgumentMatchers.eq(10));
        assertThat(recorded.getAllValues())
                .extracting(java.util.List::size)
                .containsExactly(20, 10);
    }

    private static OutboxRelayJob job(
            SchedulerLeaseRepository leases, String owner, OutboxRelayService relay) {
        return new OutboxRelayJob(
                new LeasedJobRunner(leases, new SimpleMeterRegistry(), owner), relay);
    }

    private static final class InMemoryLease implements SchedulerLeaseRepository {
        private final AtomicReference<String> owner = new AtomicReference<>();

        @Override
        public boolean tryAcquire(String key, String candidate, Duration lease) {
            assertThat(key).isEqualTo(OutboxRelayJob.JOB);
            return owner.compareAndSet(null, candidate) || owner.get().equals(candidate);
        }

        @Override
        public boolean renew(String key, String candidate, Duration lease) {
            assertThat(key).isEqualTo(OutboxRelayJob.JOB);
            return candidate.equals(owner.get());
        }

        @Override
        public boolean release(String key, String candidate) {
            assertThat(key).isEqualTo(OutboxRelayJob.JOB);
            return owner.compareAndSet(candidate, null);
        }
    }
}
