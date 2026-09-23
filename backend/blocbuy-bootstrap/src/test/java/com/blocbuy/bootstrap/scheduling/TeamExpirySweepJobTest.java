package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.service.GroupDealExpirySweeper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The timer hands the configured lease and batch to the runner and records what a run did. */
class TeamExpirySweepJobTest {
    private static final SchedulingProperties.TeamExpiry SETTINGS =
            new SchedulingProperties.TeamExpiry(
                    Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofMinutes(3), 42);

    private final LeasedJobRunner runner = mock(LeasedJobRunner.class);
    private final GroupDealExpirySweeper sweeper = mock(GroupDealExpirySweeper.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final TeamExpirySweepJob job =
            new TeamExpirySweepJob(runner, sweeper, SETTINGS, meters);

    /** The mocked runner runs the work inline, as the real one does once it holds the lease. */
    @BeforeEach
    void runWorkInline() {
        doAnswer(
                        call -> {
                            call.<Runnable>getArgument(2).run();
                            return null;
                        })
                .when(runner)
                .run(eq(TeamExpirySweepJob.JOB), eq(Duration.ofMinutes(3)), any());
    }

    @Test
    void aSweepWithDueTeamsIsCountedPerResult() {
        when(sweeper.sweep(42)).thenReturn(new GroupDealExpirySweeper.Result(6, 3, 2, 1));

        job.run();

        verify(sweeper).sweep(42);
        assertThat(teams("expired")).isEqualTo(3.0);
        assertThat(teams("already_resolved")).isEqualTo(2.0);
        assertThat(teams("failed")).isEqualTo(1.0);
    }

    @Test
    void anIdleSweepCountsNothing() {
        when(sweeper.sweep(42)).thenReturn(new GroupDealExpirySweeper.Result(0, 0, 0, 0));

        job.run();

        assertThat(teams("expired")).isZero();
        assertThat(teams("failed")).isZero();
    }

    private double teams(String result) {
        return meters.get(TeamExpirySweepJob.TEAMS).tag("result", result).counter().count();
    }
}
