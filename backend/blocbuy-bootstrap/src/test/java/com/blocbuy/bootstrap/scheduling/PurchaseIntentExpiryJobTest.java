package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.order.service.PurchaseIntentExpirySweeper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The timer hands the configured lease and batch to the runner and records what a run did. */
class PurchaseIntentExpiryJobTest {
    private static final SchedulingProperties.ExpirySweep SETTINGS =
            new SchedulingProperties.ExpirySweep(
                    Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofMinutes(3), 42);

    private final LeasedJobRunner runner = mock(LeasedJobRunner.class);
    private final PurchaseIntentExpirySweeper sweeper = mock(PurchaseIntentExpirySweeper.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final PurchaseIntentExpiryJob job =
            new PurchaseIntentExpiryJob(runner, sweeper, SETTINGS, meters);

    /** The mocked runner runs the work inline, as the real one does once it holds the lease. */
    @BeforeEach
    void runWorkInline() {
        doAnswer(
                        call -> {
                            call.<LeasedJobRunner.LeaseAwareWork>getArgument(2).run(() -> true);
                            return null;
                        })
                .when(runner)
                .run(eq(PurchaseIntentExpiryJob.JOB), eq(Duration.ofMinutes(3)), any());
    }

    @Test
    void aSweepWithDueIntentsIsCountedPerResult() {
        when(sweeper.sweep(eq(42), any()))
                .thenReturn(new PurchaseIntentExpirySweeper.Result(6, 3, 2, 1));

        job.run();

        verify(sweeper).sweep(eq(42), any());
        assertThat(intents("expired")).isEqualTo(3.0);
        assertThat(intents("skipped")).isEqualTo(2.0);
        assertThat(intents("failed")).isEqualTo(1.0);
    }

    @Test
    void anIdleSweepCountsNothing() {
        when(sweeper.sweep(eq(42), any()))
                .thenReturn(new PurchaseIntentExpirySweeper.Result(0, 0, 0, 0));

        job.run();

        assertThat(intents("expired")).isZero();
        assertThat(intents("failed")).isZero();
    }

    private double intents(String result) {
        return meters.get(PurchaseIntentExpiryJob.INTENTS).tag("result", result).counter().count();
    }
}
