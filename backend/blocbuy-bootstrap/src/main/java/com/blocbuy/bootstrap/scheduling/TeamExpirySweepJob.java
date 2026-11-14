package com.blocbuy.bootstrap.scheduling;

import com.blocbuy.groupbuy.service.GroupDealExpirySweeper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
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
    private static final String RESULT_TAG = "result";

    private final LeasedJobRunner runner;
    private final GroupDealExpirySweeper sweeper;
    private final SchedulingProperties.ExpirySweep settings;
    private final TeamExpiryShardingProperties sharding;
    private final MeterRegistry meters;
    private final AtomicInteger nextShard =
            new AtomicInteger(
                    ThreadLocalRandom.current()
                            .nextInt(TeamExpiryShardingProperties.MAX_SHARD_COUNT));

    /** Fixed delay: a slow run delays the next one instead of overlapping it on this instance. */
    @Scheduled(
            fixedDelayString = "${blocbuy.scheduling.team-expiry.interval:PT1M}",
            initialDelayString = "${blocbuy.scheduling.team-expiry.initial-delay:PT30S}")
    public void run() {
        if (sharding.count() == 1) {
            runner.run(JOB, settings.lease(), leaseValid -> sweepOnce(0, leaseValid));
            return;
        }
        // Advance even when the lease is held elsewhere. One tick attempts exactly one shard.
        int shardIndex =
                nextShard.getAndUpdate(index -> (index + 1) % sharding.count()) % sharding.count();
        String key = JOB + "/v1/" + sharding.count() + "/" + shardIndex;
        runner.run(key, settings.lease(), leaseValid -> sweepOnce(shardIndex, leaseValid));
    }

    private void sweepOnce(int shardIndex, BooleanSupplier leaseValid) {
        long started = System.nanoTime();
        GroupDealExpirySweeper.Result result =
                sharding.count() == 1
                        ? sweeper.sweep(settings.batchSize(), leaseValid)
                        : sweeper.sweep(
                                settings.batchSize(), sharding.count(), shardIndex, leaseValid);
        meters.counter(TEAMS, RESULT_TAG, "expired").increment(result.expired());
        meters.counter(TEAMS, RESULT_TAG, "already_resolved").increment(result.alreadyResolved());
        meters.counter(TEAMS, RESULT_TAG, "failed").increment(result.failed());
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        if (result.due() > 0) {
            log.info(
                    "Team expiry sweep: due={} expired={} alreadyResolved={} failed={} tookMs={}"
                            + " shardCount={} shardIndex={}",
                    result.due(),
                    result.expired(),
                    result.alreadyResolved(),
                    result.failed(),
                    tookMs,
                    sharding.count(),
                    shardIndex);
        } else {
            log.debug("Team expiry sweep: nothing due, tookMs={}", tookMs);
        }
    }
}
