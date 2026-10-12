package com.blocbuy.bootstrap.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.integration.outbox.OutboxMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxRetentionJobTest {

    private final LeasedJobRunner runner = mock(LeasedJobRunner.class);
    private final OutboxMapper outbox = mock(OutboxMapper.class);
    private final OutboxRetentionJob job = new OutboxRetentionJob(runner, outbox);

    @Test
    void deletesPublishedRowsInBoundedBatchesUnderTheExistingLease() {
        when(outbox.deletePublishedBefore(any(), eq(500))).thenReturn(500, 7);
        doAnswer(
                        call -> {
                            call.<LeasedJobRunner.LeaseAwareWork>getArgument(2).run(() -> true);
                            return null;
                        })
                .when(runner)
                .run(eq("outbox-retention"), eq(Duration.ofMinutes(5)), any());

        job.run();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(outbox, org.mockito.Mockito.times(2))
                .deletePublishedBefore(cutoff.capture(), eq(500));
        assertThat(Duration.between(cutoff.getAllValues().get(0), Instant.now()))
                .isBetween(Duration.ofDays(7).minusSeconds(2), Duration.ofDays(7).plusSeconds(2));
        assertThat(cutoff.getAllValues().get(1)).isEqualTo(cutoff.getAllValues().get(0));
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

        org.mockito.Mockito.verifyNoInteractions(outbox);
    }

    @Test
    void handlesAnEmptyRetentionBatch() {
        when(outbox.deletePublishedBefore(any(), eq(500))).thenReturn(0);
        doAnswer(
                        call -> {
                            call.<LeasedJobRunner.LeaseAwareWork>getArgument(2).run(() -> true);
                            return null;
                        })
                .when(runner)
                .run(eq("outbox-retention"), eq(Duration.ofMinutes(5)), any());

        job.run();

        verify(outbox).deletePublishedBefore(any(), eq(500));
    }
}
