package com.blocbuy.bootstrap.scheduling;

import com.blocbuy.integration.outbox.OutboxMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/** Deletes only successfully published integration events after the seven-day retention window. */
@Slf4j
@RequiredArgsConstructor
public class OutboxRetentionJob {

    static final String JOB = "outbox-retention";
    private static final Duration RETENTION = Duration.ofDays(7);
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 500;

    private final LeasedJobRunner runner;
    private final OutboxMapper outbox;

    @Scheduled(
            fixedDelayString = "${blocbuy.events.retention.interval:PT6H}",
            initialDelayString = "${blocbuy.events.retention.initial-delay:PT2M}")
    public void run() {
        runner.run(JOB, LEASE, this::deleteExpired);
    }

    private void deleteExpired(BooleanSupplier leaseValid) {
        Instant cutoff = Instant.now().minus(RETENTION);
        int deleted = 0;
        int batch;
        do {
            if (!leaseValid.getAsBoolean()) {
                break;
            }
            batch = outbox.deletePublishedBefore(cutoff, BATCH_SIZE);
            deleted += batch;
        } while (batch == BATCH_SIZE);
        if (deleted > 0) {
            log.info("Outbox retention removed {} published events", deleted);
        }
    }
}
