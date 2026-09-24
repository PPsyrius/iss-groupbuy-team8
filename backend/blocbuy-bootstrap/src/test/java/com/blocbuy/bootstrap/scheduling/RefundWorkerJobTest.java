package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.payment.service.RefundService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The refund timer delegates one attempt and respects loss of its renewable lease. */
class RefundWorkerJobTest {
    private static final SchedulingProperties.RefundWorker SETTINGS =
            new SchedulingProperties.RefundWorker(
                    Duration.ofSeconds(30), Duration.ofSeconds(45), Duration.ofMinutes(2));

    private final LeasedJobRunner runner = mock(LeasedJobRunner.class);
    private final RefundService refunds = mock(RefundService.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RefundWorkerJob job = new RefundWorkerJob(runner, refunds, SETTINGS, meters);

    @BeforeEach
    void runWorkWithValidLease() {
        doAnswer(
                        call -> {
                            call.<LeasedJobRunner.LeaseAwareWork>getArgument(2).run(() -> true);
                            return null;
                        })
                .when(runner)
                .run(eq(RefundWorkerJob.JOB), eq(Duration.ofMinutes(2)), any());
    }

    @Test
    void idleRunClaimsOnceWithoutCountingAnAttempt() {
        job.run();

        verify(refunds).processNext();
        assertThat(attempts()).isZero();
    }

    @Test
    void dueRunClaimsOnlyOneEvenWhenMoreAreDue() {
        when(refunds.processNext()).thenReturn(true);

        job.run();

        verify(refunds).processNext();
        assertThat(attempts()).isEqualTo(1.0);
    }

    @Test
    void lostLeaseStopsBeforeClaimingMoneyMovement() {
        doAnswer(
                        call -> {
                            call.<LeasedJobRunner.LeaseAwareWork>getArgument(2).run(() -> false);
                            return null;
                        })
                .when(runner)
                .run(eq(RefundWorkerJob.JOB), eq(Duration.ofMinutes(2)), any());

        job.run();

        verifyNoInteractions(refunds);
        assertThat(attempts()).isZero();
    }

    @Test
    void unexpectedFailureDoesNotCountAsACompletedAttempt() {
        when(refunds.processNext()).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(job::run).isInstanceOf(IllegalStateException.class);
        assertThat(attempts()).isZero();
    }

    private double attempts() {
        return meters.find(RefundWorkerJob.REFUNDS).counter() == null
                ? 0.0
                : meters.get(RefundWorkerJob.REFUNDS).counter().count();
    }
}
