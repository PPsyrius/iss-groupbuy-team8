package com.blocbuy.bootstrap.scheduling;

import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.repository.SchedulerLeaseRepository;
import com.blocbuy.common.util.UuidV7;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Runs one scheduled job under the database lease: take the lease or skip, run, release. Shared by
 * every job, so each of them gets the same renewable lease, metrics and trace id.
 *
 * <p>Never throws: a failed run is counted and logged once, and the next run tries again. The lease
 * is renewed every third of its duration. If renewal fails, lease-aware work stops taking new
 * items; its current item must still be idempotent in case another instance takes over.
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

    /** Work that can stop between bounded items after lease renewal fails. */
    @FunctionalInterface
    public interface LeaseAwareWork {
        void run(BooleanSupplier leaseValid);
    }

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
    public void run(String job, Duration lease, LeaseAwareWork work) {
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

    private String execute(String job, Duration lease, LeaseAwareWork work) {
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
        AtomicBoolean leaseValid = new AtomicBoolean(true);
        CountDownLatch stop = new CountDownLatch(1);
        CompletableFuture<Void> renewal = startRenewal(job, lease, leaseValid, stop);
        String outcome = COMPLETED;
        try {
            work.run(leaseValid::get);
        } catch (RuntimeException e) {
            log.error("Job {} failed; the next run retries", job, e);
            outcome = FAILED;
        } finally {
            stopRenewal(renewal, stop);
            release(job);
        }
        return leaseValid.get() ? outcome : FAILED;
    }

    private CompletableFuture<Void> startRenewal(
            String job, Duration lease, AtomicBoolean leaseValid, CountDownLatch stop) {
        String traceId = MDC.get(ApiConstants.TRACE_ID_MDC_KEY);
        Duration renewalInterval = lease.dividedBy(3);
        var finished = new CompletableFuture<Void>();
        Thread.ofVirtual()
                .name("lease-renewal-" + job)
                .start(
                        () -> {
                            try {
                                MDC.put(ApiConstants.TRACE_ID_MDC_KEY, traceId);
                                while (leaseValid.get()
                                        && !stop.await(
                                                renewalInterval.toMillis(),
                                                TimeUnit.MILLISECONDS)) {
                                    if (!leases.renew(job, owner, lease)) {
                                        leaseValid.set(false);
                                        log.warn(
                                                "Job {} lease could not be renewed; stopping its batch",
                                                job);
                                    }
                                }
                            } catch (InterruptedException stopped) {
                                leaseValid.set(false);
                                Thread.currentThread().interrupt();
                                log.warn(
                                        "Job {} lease renewal interrupted; stopping its batch",
                                        job);
                            } catch (RuntimeException e) {
                                leaseValid.set(false);
                                log.error(
                                        "Job {} lease renewal failed; stopping its batch", job, e);
                            } finally {
                                MDC.remove(ApiConstants.TRACE_ID_MDC_KEY);
                                finished.complete(null);
                            }
                        });
        return finished;
    }

    private static void stopRenewal(CompletableFuture<Void> renewal, CountDownLatch stop) {
        stop.countDown();
        renewal.join();
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
