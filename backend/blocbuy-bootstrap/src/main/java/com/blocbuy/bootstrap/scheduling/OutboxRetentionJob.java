package com.blocbuy.bootstrap.scheduling;

import com.blocbuy.common.repository.OutboxRetentionRepository;
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

    private final LeasedJobRunner runner;
    private final OutboxRetentionRepository outbox;

    @Scheduled(
            fixedDelayString = "${blocbuy.events.retention.interval:PT6H}",
            initialDelayString = "${blocbuy.events.retention.initial-delay:PT2M}")
    public void run() {
        runner.run(JOB, LEASE, this::deleteExpired);
    }

    private void deleteExpired(BooleanSupplier leaseValid) {
        if (!leaseValid.getAsBoolean()) {
            return;
        }
        Instant cutoff = Instant.now().minus(RETENTION);
        // A full batch leaves the remainder for the next tick, not another SQL call in this run.
        int deleted = outbox.deletePublishedBefore(cutoff);
        if (deleted > 0) {
            log.info("Outbox retention removed {} published events", deleted);
        }
    }
}
