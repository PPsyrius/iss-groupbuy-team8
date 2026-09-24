package com.blocbuy.bootstrap.scheduling;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Scheduled jobs, bound from {@code blocbuy.scheduling.*}. The master switch is off unless a
 * deployment turns it on after its backlog check, so no test or tool profile starts a job by
 * accident. Invalid values fail startup rather than the first run.
 *
 * @param enabled master switch; read by {@code SchedulingConfiguration}'s condition
 * @param teamExpiry the team-expiry sweep
 */
@ConfigurationProperties("blocbuy.scheduling")
public record SchedulingProperties(boolean enabled, @DefaultValue TeamExpiry teamExpiry) {

    /** Upper bound on one sweep, so a backlog drains in bounded runs. */
    static final int MAX_BATCH_SIZE = 500;

    static final Duration MIN_LEASE = Duration.ofSeconds(10);

    /**
     * Team-expiry sweep settings.
     *
     * @param interval fixed delay between the end of one run and the start of the next
     * @param initialDelay delay before the first run, so startup finishes first
     * @param lease how long one run keeps other instances out; a crashed holder blocks no longer
     * @param batchSize most teams resolved per run, 1..500
     */
    public record TeamExpiry(
            @DefaultValue("PT1M") Duration interval,
            @DefaultValue("PT30S") Duration initialDelay,
            @DefaultValue("PT2M") Duration lease,
            @DefaultValue("100") int batchSize) {

        /** Rejects values that would disable the sweep silently or overload one run. */
        public TeamExpiry {
            if (interval == null || !interval.isPositive()) {
                throw new IllegalArgumentException("team-expiry.interval must be positive");
            }
            if (initialDelay == null || initialDelay.isNegative()) {
                throw new IllegalArgumentException(
                        "team-expiry.initial-delay must not be negative");
            }
            if (lease == null || lease.compareTo(MIN_LEASE) < 0) {
                throw new IllegalArgumentException(
                        "team-expiry.lease must be at least " + MIN_LEASE);
            }
            if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
                throw new IllegalArgumentException(
                        "team-expiry.batch-size must be between 1 and " + MAX_BATCH_SIZE);
            }
        }
    }
}
