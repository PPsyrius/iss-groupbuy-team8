package com.blocbuy.bootstrap.scheduling;

import com.blocbuy.groupbuy.service.GroupDealExpirySweeper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Timer for the team-expiry sweep (#28). Resolves overdue open teams as expired; their orders are
 * cancelled and refund obligations recorded by the {@code TeamExpired} subscribers in the same
 * transaction. No money moves here — the refund worker drains those obligations separately.
 */
@Slf4j
@RequiredArgsConstructor
public class TeamExpirySweepJob {
    static final String JOB = "team-expiry-sweep";
    static final String TEAMS = "blocbuy.team.expiry.teams";

    private final LeasedJobRunner runner;
    private final GroupDealExpirySweeper sweeper;
    private final SchedulingProperties.TeamExpiry settings;
    private final MeterRegistry meters;

    /** Fixed delay: a slow run delays the next one instead of overlapping it on this instance. */
    @Scheduled(
            fixedDelayString = "${blocbuy.scheduling.team-expiry.interval:PT1M}",
            initialDelayString = "${blocbuy.scheduling.team-expiry.initial-delay:PT30S}")
    public void run() {
        runner.run(JOB, settings.lease(), this::sweepOnce);
    }

    private void sweepOnce() {
        long started = System.nanoTime();
        GroupDealExpirySweeper.Result result = sweeper.sweep(settings.batchSize());
        meters.counter(TEAMS, "result", "expired").increment(result.expired());
        meters.counter(TEAMS, "result", "already_resolved").increment(result.alreadyResolved());
        meters.counter(TEAMS, "result", "failed").increment(result.failed());
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        if (result.due() > 0) {
            log.info(
                    "Team expiry sweep: due={} expired={} alreadyResolved={} failed={} tookMs={}",
                    result.due(),
                    result.expired(),
                    result.alreadyResolved(),
                    result.failed(),
                    tookMs);
        } else {
            log.debug("Team expiry sweep: nothing due, tookMs={}", tookMs);
        }
    }
}
