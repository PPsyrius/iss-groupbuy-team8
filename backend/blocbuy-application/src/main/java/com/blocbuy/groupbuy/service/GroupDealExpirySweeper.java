package com.blocbuy.groupbuy.service;

import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One sweep over teams whose admission window has closed: one batch read of due ids, then {@link
 * GroupDealExpiryService#expireOne} per team.
 *
 * <p><b>Deliberately not {@code @Transactional}.</b> Each {@code expireOne} is its own short
 * transaction; an outer one would let {@code REQUIRED} join them all, so a single failing team
 * would mark the whole batch rollback-only. Per-team transactions also keep each row lock short,
 * which matters because webhook settlement locks the same rows.
 *
 * <p>The loop issues one lock and one guarded UPDATE per team. That is the unit of work — a write
 * that locks one aggregate — not read amplification: the ids come from a single bounded query.
 * Merging the writes into one statement is rejected in ADR 0002 (rollback blast radius).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupDealExpirySweeper {

    static final Duration RETRY_DELAY = Duration.ofMinutes(5);

    private final PaidGroupDealRepository deals;
    private final GroupDealExpiryService expiry;

    /**
     * Counts of one sweep.
     *
     * @param due teams selected as overdue
     * @param expired teams this sweep resolved
     * @param alreadyResolved teams a concurrent writer resolved first (filled, or another sweep)
     * @param failed teams whose expiry threw; they stay open until their retry backoff elapses
     */
    public record Result(int due, int expired, int alreadyResolved, int failed) {}

    /**
     * Resolves up to {@code limit} overdue teams, oldest deadline first. A failure on one team is
     * logged and counted, never propagated, so it cannot stop the others.
     */
    public Result sweep(int limit) {
        return sweep(limit, () -> true);
    }

    /** Resolves candidates until the batch ends or the caller reports that its lease was lost. */
    public Result sweep(int limit, BooleanSupplier leaseValid) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        // Candidate selection only: expireOne re-reads the clock under the row lock and decides.
        Instant now = deals.currentTime();
        List<UUID> due = deals.dueForExpiry(now, limit);
        return resolve(due, leaseValid);
    }

    /**
     * One bounded partition read, followed by the same independently transactional aggregate
     * writes.
     */
    public Result sweep(int limit, int shardCount, int shardIndex, BooleanSupplier leaseValid) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }
        if (shardCount < 1 || shardCount > 6 || shardIndex < 0 || shardIndex >= shardCount) {
            throw new IllegalArgumentException("invalid team-expiry shard count or index");
        }
        if (shardCount == 1) {
            return sweep(limit, leaseValid);
        }
        Instant now = deals.currentTime();
        return resolve(deals.dueForExpiry(now, limit, shardCount, shardIndex), leaseValid);
    }

    private Result resolve(List<UUID> due, BooleanSupplier leaseValid) {
        int expired = 0;
        int alreadyResolved = 0;
        int failed = 0;
        for (UUID dealId : due) {
            if (!leaseValid.getAsBoolean()) {
                break;
            }
            try {
                if (expiry.expireOne(dealId)) {
                    expired++;
                } else {
                    alreadyResolved++;
                }
            } catch (RuntimeException e) {
                failed++;
                deferRetry(dealId, e);
            }
        }
        return new Result(due.size(), expired, alreadyResolved, failed);
    }

    private void deferRetry(UUID dealId, RuntimeException failure) {
        try {
            deals.deferExpiryRetry(dealId, RETRY_DELAY);
            log.warn("Team {} expiry failed; retry deferred for {}", dealId, RETRY_DELAY, failure);
        } catch (RuntimeException backoffFailure) {
            failure.addSuppressed(backoffFailure);
            throw new IllegalStateException(
                    "Team " + dealId + " expiry failed and retry backoff could not be recorded",
                    failure);
        }
    }
}
