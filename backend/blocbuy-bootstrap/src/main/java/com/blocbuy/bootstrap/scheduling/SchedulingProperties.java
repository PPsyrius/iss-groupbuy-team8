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
 * @param purchaseIntentExpiry the unpaid-intent sweep
 * @param refundWorker the single-obligation refund worker
 */
@ConfigurationProperties("blocbuy.scheduling")
public record SchedulingProperties(
        boolean enabled,
        @DefaultValue ExpirySweep teamExpiry,
        @DefaultValue ExpirySweep purchaseIntentExpiry,
        @DefaultValue RefundWorker refundWorker) {

    /** Upper bound on one sweep, so a backlog drains in bounded runs. */
    static final int MAX_BATCH_SIZE = 500;

    static final Duration MIN_LEASE = Duration.ofSeconds(10);

    /**
     * Settings shared by independently configured expiry sweeps.
     *
     * @param interval fixed delay between the end of one run and the start of the next
     * @param initialDelay delay before the first run, so startup finishes first
     * @param lease how long one run keeps other instances out; a crashed holder blocks no longer
     * @param batchSize most candidates selected per run, 1..500
     */
    public record ExpirySweep(
            @DefaultValue("PT1M") Duration interval,
            @DefaultValue("PT30S") Duration initialDelay,
            @DefaultValue("PT2M") Duration lease,
            @DefaultValue("100") int batchSize) {

        /** Rejects values that would disable the sweep silently or overload one run. */
        public ExpirySweep {
            if (interval == null || !interval.isPositive()) {
                throw new IllegalArgumentException("expiry-sweep.interval must be positive");
            }
            if (initialDelay == null || initialDelay.isNegative()) {
                throw new IllegalArgumentException(
                        "expiry-sweep.initial-delay must not be negative");
            }
            if (lease == null || lease.compareTo(MIN_LEASE) < 0) {
                throw new IllegalArgumentException(
                        "expiry-sweep.lease must be at least " + MIN_LEASE);
            }
            if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
                throw new IllegalArgumentException(
                        "expiry-sweep.batch-size must be between 1 and " + MAX_BATCH_SIZE);
            }
        }
    }

    /** One independently transactional refund attempt per tick, under a renewable lease. */
    public record RefundWorker(
            @DefaultValue("PT30S") Duration interval,
            @DefaultValue("PT45S") Duration initialDelay,
            @DefaultValue("PT2M") Duration lease) {

        public RefundWorker {
            if (interval == null || !interval.isPositive()) {
                throw new IllegalArgumentException("refund-worker.interval must be positive");
            }
            if (initialDelay == null || initialDelay.isNegative()) {
                throw new IllegalArgumentException(
                        "refund-worker.initial-delay must not be negative");
            }
            if (lease == null || lease.compareTo(MIN_LEASE) < 0) {
                throw new IllegalArgumentException(
                        "refund-worker.lease must be at least " + MIN_LEASE);
            }
        }
    }
}
