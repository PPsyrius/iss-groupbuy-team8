package com.blocbuy.bootstrap.scheduling;

import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.repository.SchedulerLeaseRepository;
import com.blocbuy.common.util.UuidV7;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Runs one scheduled job under the database lease: take the lease or skip, run, release. Shared by
 * every job, so each of them gets the same single-flight guarantee, metrics and trace id.
 *
 * <p>Never throws: a failed run is counted and logged once, and the next run tries again. Losing
 * the lease mid-run is logged, not prevented — every job run here must be idempotent under its own
 * status guards, which makes an overlap harmless.
 */
@Slf4j
@RequiredArgsConstructor
public class LeasedJobRunner {
    static final String RUNS = "blocbuy.scheduler.runs";
    static final String DURATION = "blocbuy.scheduler.run.duration";
    static final String COMPLETED = "completed";
    static final String SKIPPED = "skipped";
    static final String FAILED = "failed";

    /** V13 {@code owner varchar(128)} minus {@code "/"} and a 36-character UUID. */
    private static final int MAX_HOST_LENGTH = 128 - 37;

    private final SchedulerLeaseRepository leases;
    private final MeterRegistry meters;
    private final String owner;

    /**
     * Identifies this JVM as a lease owner: host (the container id on EC2) plus a per-JVM UUID, so
     * two containers on one host during a redeploy never share an owner.
     */
    public static String ownerId(String host) {
        String name = host == null || host.isBlank() ? "unknown" : host.strip();
        if (name.length() > MAX_HOST_LENGTH) {
            name = name.substring(0, MAX_HOST_LENGTH);
        }
        return name + "/" + UuidV7.next();
    }

    /** Runs {@code work} under the lease {@code job}; one trace id covers the whole run. */
    public void run(String job, Duration lease, Runnable work) {
        MDC.put(ApiConstants.TRACE_ID_MDC_KEY, "job-" + UuidV7.next());
        Timer.Sample sample = Timer.start(meters);
        try {
            String outcome = execute(job, lease, work);
            meters.counter(RUNS, "job", job, "outcome", outcome).increment();
        } finally {
            sample.stop(meters.timer(DURATION, "job", job));
            MDC.remove(ApiConstants.TRACE_ID_MDC_KEY);
        }
    }

    private String execute(String job, Duration lease, Runnable work) {
        boolean acquired;
        try {
            acquired = leases.tryAcquire(job, owner, lease);
        } catch (RuntimeException e) {
            // Includes the 3 s statement timeout on a long-held lease row. Nothing is held yet,
            // so there is nothing to release.
            log.error("Job {} could not take its lease; the next run retries", job, e);
            return FAILED;
        }
        if (!acquired) {
            log.debug("Job {} skipped; another instance holds the lease", job);
            return SKIPPED;
        }
        try {
            work.run();
            return COMPLETED;
        } catch (RuntimeException e) {
            log.error("Job {} failed; the next run retries", job, e);
            return FAILED;
        } finally {
            release(job);
        }
    }

    private void release(String job) {
        try {
            if (!leases.release(job, owner)) {
                log.warn("Job {} lease expired before the run finished", job);
            }
        } catch (RuntimeException e) {
            log.warn("Job {} lease release failed; it lapses on its own", job, e);
        }
    }
}
