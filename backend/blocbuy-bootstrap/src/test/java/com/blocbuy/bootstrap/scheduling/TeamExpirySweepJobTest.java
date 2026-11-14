package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.groupbuy.service.GroupDealExpirySweeper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The timer hands the configured lease and batch to the runner and records what a run did. */
class TeamExpirySweepJobTest {
    private static final SchedulingProperties.ExpirySweep SETTINGS =
            new SchedulingProperties.ExpirySweep(
                    Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofMinutes(3), 42);

    private final LeasedJobRunner runner = mock(LeasedJobRunner.class);
    private final GroupDealExpirySweeper sweeper = mock(GroupDealExpirySweeper.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final TeamExpirySweepJob job =
            new TeamExpirySweepJob(
                    runner, sweeper, SETTINGS, new TeamExpiryShardingProperties(1), meters);

    /** The mocked runner runs the work inline, as the real one does once it holds the lease. */
    @BeforeEach
    void runWorkInline() {
        doAnswer(
                        call -> {
                            call.<LeasedJobRunner.LeaseAwareWork>getArgument(2).run(() -> true);
                            return null;
                        })
                .when(runner)
                .run(eq(TeamExpirySweepJob.JOB), eq(Duration.ofMinutes(3)), any());
    }

    @Test
    void aSweepWithDueTeamsIsCountedPerResult() {
        when(sweeper.sweep(eq(42), any()))
                .thenReturn(new GroupDealExpirySweeper.Result(6, 3, 2, 1));

        job.run();

        verify(sweeper).sweep(eq(42), any());
        assertThat(teams("expired")).isEqualTo(3.0);
        assertThat(teams("already_resolved")).isEqualTo(2.0);
        assertThat(teams("failed")).isEqualTo(1.0);
    }

    @Test
    void anIdleSweepCountsNothing() {
        when(sweeper.sweep(eq(42), any()))
                .thenReturn(new GroupDealExpirySweeper.Result(0, 0, 0, 0));

        job.run();

        assertThat(teams("expired")).isZero();
        assertThat(teams("failed")).isZero();
    }

    @Test
    void oneShardPerTickRotatesEvenWhenTheFirstLeaseIsSkipped() {
        var partitioned =
                new TeamExpirySweepJob(
                        runner, sweeper, SETTINGS, new TeamExpiryShardingProperties(6), meters);
        List<Integer> attempted = new ArrayList<>();
        when(sweeper.sweep(eq(42), eq(6), anyInt(), any()))
                .thenReturn(new GroupDealExpirySweeper.Result(1, 1, 0, 0));
        doAnswer(
                        call -> {
                            String key = call.getArgument(0);
                            int index = Integer.parseInt(key.substring(key.lastIndexOf('/') + 1));
                            assertThat(key).isEqualTo("team-expiry-sweep/v1/6/" + index);
                            attempted.add(index);
                            if (attempted.size() > 1) {
                                call.<LeasedJobRunner.LeaseAwareWork>getArgument(2).run(() -> true);
                                verify(sweeper).sweep(eq(42), eq(6), eq(index), any());
                            }
                            return null;
                        })
                .when(runner)
                .run(anyString(), eq(Duration.ofMinutes(3)), any());

        for (int i = 0; i < 6; i++) {
            partitioned.run();
        }

        assertThat(attempted).containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5);
        for (int i = 1; i < attempted.size(); i++) {
            assertThat(attempted.get(i)).isEqualTo((attempted.get(i - 1) + 1) % 6);
        }
        verify(sweeper, times(5)).sweep(eq(42), eq(6), anyInt(), any());
        verify(sweeper, never()).sweep(anyInt(), any());
    }

    private double teams(String result) {
        return meters.get(TeamExpirySweepJob.TEAMS).tag("result", result).counter().count();
    }
}
