package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blocbuy.common.repository.SchedulerLeaseRepository;
import com.blocbuy.common.service.OutboxRelayService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Two relay instances honoring the shared scheduler-lease contract cannot relay concurrently. */
class OutboxRelayLeaseTest {

    @Test
    void onlyTheLeaseHolderRelaysWhileTheOtherInstanceSkips() throws Exception {
        var lock = new InMemoryLease();
        var relay = mock(OutboxRelayService.class);
        var firstEnteredRelay = new CountDownLatch(1);
        var allowFirstRelayToFinish = new CountDownLatch(1);
        var invocations = new AtomicInteger();
        when(relay.relayBatch())
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
