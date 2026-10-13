package com.blocbuy.bootstrap.scheduling;

import com.blocbuy.common.service.OutboxRelayService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/** Publishes at most one bounded batch per tick under the shared scheduler lease. */
@Slf4j
@RequiredArgsConstructor
public class OutboxRelayJob {

    static final String JOB = "outbox-relay";
    private static final Duration LEASE = Duration.ofMinutes(2);

    private final LeasedJobRunner runner;
    private final OutboxRelayService relay;

    @Scheduled(fixedDelay = 2_000, initialDelay = 2_000)
    public void run() {
        runner.run(
                JOB,
                LEASE,
                leaseValid -> {
                    if (leaseValid.getAsBoolean()) {
                        int updated = relay.relayBatch();
                        if (updated > 0) {
                            log.info("Outbox relay recorded {} delivery results", updated);
                        }
                    }
                });
    }
}
