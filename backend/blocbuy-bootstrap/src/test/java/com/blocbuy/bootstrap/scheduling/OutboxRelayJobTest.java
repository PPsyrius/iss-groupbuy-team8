package com.blocbuy.bootstrap.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.service.OutboxRelayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxRelayJobTest {

    private final LeasedJobRunner runner = mock(LeasedJobRunner.class);
    private final OutboxRelayService relay = mock(OutboxRelayService.class);
    private final OutboxRelayJob job = new OutboxRelayJob(runner, relay);

    @BeforeEach
    void runWorkUnderTheLease() {
        doAnswer(
                        call -> {
                            call.<LeasedJobRunner.LeaseAwareWork>getArgument(2).run(() -> true);
                            return null;
                        })
                .when(runner)
                .run(eq(OutboxRelayJob.JOB), any(), any());
    }

    @Test
    void relaysOneBatchWhenTheLeaseIsValid() {
        when(relay.relayBatch()).thenReturn(3);

        job.run();

        verify(relay).relayBatch();
    }

    @Test
    void skipsRelayWhenTheLeaseIsInvalid() {
        doAnswer(
                        call -> {
                            call.<LeasedJobRunner.LeaseAwareWork>getArgument(2).run(() -> false);
                            return null;
                        })
                .when(runner)
                .run(eq(OutboxRelayJob.JOB), any(), any());

        job.run();

        verify(relay, never()).relayBatch();
    }

    @Test
    void logsNoWorkWhenBatchIsEmpty() {
        when(relay.relayBatch()).thenReturn(0);

        job.run();

        verify(relay).relayBatch();
    }
}
