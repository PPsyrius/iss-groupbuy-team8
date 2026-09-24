package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.repository.SchedulerLeaseRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.QueryTimeoutException;

/** Take the lease or skip, run, always release what was taken, and never throw to the timer. */
class LeasedJobRunnerTest {
    private static final String JOB = "test-job";
    private static final String OWNER = "host/owner";
    private static final Duration LEASE = Duration.ofMinutes(2);

    private final SchedulerLeaseRepository leases = mock(SchedulerLeaseRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final LeasedJobRunner runner = new LeasedJobRunner(leases, meters, OWNER);
    private final AtomicInteger runs = new AtomicInteger();

    @Test
    void anAcquiredLeaseRunsTheWorkUnderATraceIdAndReleasesIt() {
        when(leases.tryAcquire(JOB, OWNER, LEASE)).thenReturn(true);
        when(leases.release(JOB, OWNER)).thenReturn(true);
        var traceId = new AtomicReference<String>();

        runner.run(JOB, LEASE, ignored -> traceId.set(MDC.get(ApiConstants.TRACE_ID_MDC_KEY)));

        assertThat(traceId.get()).startsWith("job-");
        assertThat(MDC.get(ApiConstants.TRACE_ID_MDC_KEY)).isNull();
        verify(leases).release(JOB, OWNER);
        assertThat(runs(LeasedJobRunner.COMPLETED)).isEqualTo(1.0);
        assertThat(meters.get(LeasedJobRunner.DURATION).tag("job", JOB).timer().count())
                .isEqualTo(1);
    }

    @Test
    void aLeaseHeldElsewhereSkipsTheRunWithoutReleasing() {
        when(leases.tryAcquire(JOB, OWNER, LEASE)).thenReturn(false);

        runner.run(JOB, LEASE, ignored -> runs.incrementAndGet());

        assertThat(runs).hasValue(0);
        verify(leases, never()).release(any(), any());
        assertThat(runs(LeasedJobRunner.SKIPPED)).isEqualTo(1.0);
    }

    @Test
    void aFailingRunIsCountedReleasedAndNotRethrown() {
        when(leases.tryAcquire(JOB, OWNER, LEASE)).thenReturn(true);
        when(leases.release(JOB, OWNER)).thenReturn(true);

        runner.run(
                JOB,
                LEASE,
                ignored -> {
                    throw new IllegalStateException("boom");
                });

        verify(leases).release(JOB, OWNER);
        assertThat(runs(LeasedJobRunner.FAILED)).isEqualTo(1.0);
        assertThat(MDC.get(ApiConstants.TRACE_ID_MDC_KEY)).isNull();
    }

    /** R6: the 3 s statement timeout on a long-held lease row costs one run, nothing more. */
    @Test
    void anAcquireThatThrowsRunsNothingAndReleasesNothing() {
        when(leases.tryAcquire(JOB, OWNER, LEASE))
                .thenThrow(new QueryTimeoutException("canceling statement"));

        runner.run(JOB, LEASE, ignored -> runs.incrementAndGet());

        assertThat(runs).hasValue(0);
        verify(leases, never()).release(any(), any());
        assertThat(runs(LeasedJobRunner.FAILED)).isEqualTo(1.0);
    }

    @Test
    void aLeaseLostMidRunOrAFailedReleaseStillCompletesTheRun() {
        when(leases.tryAcquire(JOB, OWNER, LEASE)).thenReturn(true);
        when(leases.release(JOB, OWNER))
                .thenReturn(false)
                .thenThrow(new QueryTimeoutException("canceling statement"));

        runner.run(JOB, LEASE, ignored -> runs.incrementAndGet());
        runner.run(JOB, LEASE, ignored -> runs.incrementAndGet());

        assertThat(runs).hasValue(2);
        assertThat(runs(LeasedJobRunner.COMPLETED)).isEqualTo(2.0);
    }

    @Test
    void failedRenewalInvalidatesTheLeaseAwareWorkAndTheRun() {
        Duration shortLease = Duration.ofMillis(60);
        var renewalAttempted = new CountDownLatch(1);
        var observedValidity = new AtomicReference<Boolean>();
        when(leases.tryAcquire(JOB, OWNER, shortLease)).thenReturn(true);
        when(leases.renew(JOB, OWNER, shortLease))
                .thenAnswer(
                        ignored -> {
                            renewalAttempted.countDown();
                            return false;
                        });
        when(leases.release(JOB, OWNER)).thenReturn(false);

        runner.run(
                JOB,
                shortLease,
                leaseValid -> {
                    await(renewalAttempted);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    while (leaseValid.getAsBoolean() && System.nanoTime() < deadline) {
                        Thread.onSpinWait();
                    }
                    observedValidity.set(leaseValid.getAsBoolean());
                });

        assertThat(observedValidity).hasValue(false);
        assertThat(runs(LeasedJobRunner.FAILED)).isEqualTo(1.0);
    }

    @Test
    void completedRunSignalsStopWithoutInterruptingAnInFlightRenewal() throws Exception {
        Duration shortLease = Duration.ofMillis(60);
        var renewing = new CountDownLatch(1);
        var finishRenewal = new CountDownLatch(1);
        var interrupted = new AtomicReference<Boolean>(false);
        when(leases.tryAcquire(JOB, OWNER, shortLease)).thenReturn(true);
        when(leases.renew(JOB, OWNER, shortLease))
                .thenAnswer(
                        ignored -> {
                            renewing.countDown();
                            try {
                                if (!finishRenewal.await(2, TimeUnit.SECONDS)) {
                                    throw new QueryTimeoutException("renewal did not finish");
                                }
                            } catch (InterruptedException e) {
                                interrupted.set(true);
                                Thread.currentThread().interrupt();
                                throw new QueryTimeoutException("renewal was interrupted", e);
                            }
                            return true;
                        });
        when(leases.release(JOB, OWNER)).thenReturn(true);

        Thread finish =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    await(renewing);
                                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
                                    finishRenewal.countDown();
                                });
        try {
            runner.run(
                    JOB,
                    shortLease,
                    ignored -> {
                        await(renewing);
                        Thread.currentThread().interrupt();
                    });

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(interrupted).hasValue(false);
            assertThat(runs(LeasedJobRunner.COMPLETED)).isEqualTo(1.0);
            verify(leases).release(JOB, OWNER);
        } finally {
            Thread.interrupted();
            finishRenewal.countDown();
            finish.join();
        }
    }

    @Test
    void successfulRenewalKeepsTheWorkValidAndCarriesTheTraceId() {
        Duration shortLease = Duration.ofMillis(60);
        var renewed = new CountDownLatch(1);
        var renewalTraceId = new AtomicReference<String>();
        when(leases.tryAcquire(JOB, OWNER, shortLease)).thenReturn(true);
        when(leases.renew(JOB, OWNER, shortLease))
                .thenAnswer(
                        ignored -> {
                            renewalTraceId.set(MDC.get(ApiConstants.TRACE_ID_MDC_KEY));
                            renewed.countDown();
                            return true;
                        });
        when(leases.release(JOB, OWNER)).thenReturn(true);

        runner.run(
                JOB,
                shortLease,
                leaseValid -> {
                    await(renewed);
                    assertThat(leaseValid.getAsBoolean()).isTrue();
                });

        assertThat(renewalTraceId.get()).startsWith("job-");
        assertThat(runs(LeasedJobRunner.COMPLETED)).isEqualTo(1.0);
    }

    @Test
    void renewalExceptionInvalidatesTheRun() {
        Duration shortLease = Duration.ofMillis(60);
        var renewalAttempted = new CountDownLatch(1);
        when(leases.tryAcquire(JOB, OWNER, shortLease)).thenReturn(true);
        when(leases.renew(JOB, OWNER, shortLease))
                .thenAnswer(
                        ignored -> {
                            renewalAttempted.countDown();
                            throw new QueryTimeoutException("canceling renewal");
                        });
        when(leases.release(JOB, OWNER)).thenReturn(false);

        runner.run(JOB, shortLease, ignored -> await(renewalAttempted));

        assertThat(runs(LeasedJobRunner.FAILED)).isEqualTo(1.0);
    }

    @Test
    void interruptedRenewalStopsTheBatchAndFailsTheRun() {
        Duration shortLease = Duration.ofMillis(60);
        var renewalAttempted = new CountDownLatch(1);
        when(leases.tryAcquire(JOB, OWNER, shortLease)).thenReturn(true);
        when(leases.renew(JOB, OWNER, shortLease))
                .thenAnswer(
                        ignored -> {
                            renewalAttempted.countDown();
                            Thread.currentThread().interrupt();
                            return true;
                        });
        when(leases.release(JOB, OWNER)).thenReturn(false);

        runner.run(
                JOB,
                shortLease,
                leaseValid -> {
                    await(renewalAttempted);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    while (leaseValid.getAsBoolean() && System.nanoTime() < deadline) {
                        Thread.onSpinWait();
                    }
                    assertThat(leaseValid.getAsBoolean()).isFalse();
                });

        assertThat(runs(LeasedJobRunner.FAILED)).isEqualTo(1.0);
    }

    @Test
    void ownerIdsAreUniquePerJvmAndFitTheV13Column() {
        String first = LeasedJobRunner.ownerId("ip-10-0-0-1");

        assertThat(first).startsWith("ip-10-0-0-1/").hasSize("ip-10-0-0-1/".length() + 36);
        assertThat(LeasedJobRunner.ownerId("ip-10-0-0-1")).isNotEqualTo(first);
        assertThat(LeasedJobRunner.ownerId(null)).startsWith("unknown/");
        assertThat(LeasedJobRunner.ownerId(" ")).startsWith("unknown/");
        assertThat(LeasedJobRunner.ownerId("h".repeat(300))).hasSize(128);
    }

    private double runs(String outcome) {
        return meters.get(LeasedJobRunner.RUNS)
                .tag("job", JOB)
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
