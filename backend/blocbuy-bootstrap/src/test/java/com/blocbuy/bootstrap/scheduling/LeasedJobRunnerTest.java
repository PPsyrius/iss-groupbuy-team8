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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

        runner.run(JOB, LEASE, () -> traceId.set(MDC.get(ApiConstants.TRACE_ID_MDC_KEY)));

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

        runner.run(JOB, LEASE, runs::incrementAndGet);

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
                () -> {
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

        runner.run(JOB, LEASE, runs::incrementAndGet);

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

        runner.run(JOB, LEASE, runs::incrementAndGet);
        runner.run(JOB, LEASE, runs::incrementAndGet);

        assertThat(runs).hasValue(2);
        assertThat(runs(LeasedJobRunner.COMPLETED)).isEqualTo(2.0);
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
}
