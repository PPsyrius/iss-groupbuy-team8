package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.common.repository.OutboxRetentionRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxRetentionJobTest {

    private final LeasedJobRunner runner = mock(LeasedJobRunner.class);
    private final OutboxRetentionRepository outbox = mock(OutboxRetentionRepository.class);
    private final OutboxRetentionJob job = new OutboxRetentionJob(runner, outbox);

    @Test
    void fullBatchDoesNotDrainTheBacklogWithinTheSameRun() {
        when(outbox.deletePublishedBefore(any())).thenReturn(500);
        doAnswer(
                        call -> {
                            call.<LeasedJobRunner.LeaseAwareWork>getArgument(2).run(() -> true);
                            return null;
                        })
                .when(runner)
                .run(eq("outbox-retention"), eq(Duration.ofMinutes(5)), any());

        job.run();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(outbox).deletePublishedBefore(cutoff.capture());
        assertThat(Duration.between(cutoff.getValue(), Instant.now()))
                .isBetween(Duration.ofDays(7).minusSeconds(2), Duration.ofDays(7).plusSeconds(2));
    }

    @Test
    void stopsBeforeDeletingWhenTheLeaseIsLost() {
        doAnswer(
                        call -> {
                            call.<LeasedJobRunner.LeaseAwareWork>getArgument(2).run(() -> false);
                            return null;
                        })
                .when(runner)
                .run(eq("outbox-retention"), eq(Duration.ofMinutes(5)), any());

        job.run();

        verifyNoInteractions(outbox);
    }

    @Test
    void handlesAnEmptyRetentionBatch() {
        when(outbox.deletePublishedBefore(any())).thenReturn(0);
        doAnswer(
                        call -> {
                            call.<LeasedJobRunner.LeaseAwareWork>getArgument(2).run(() -> true);
                            return null;
                        })
                .when(runner)
                .run(eq("outbox-retention"), eq(Duration.ofMinutes(5)), any());

        job.run();

        verify(outbox).deletePublishedBefore(any());
    }
}
