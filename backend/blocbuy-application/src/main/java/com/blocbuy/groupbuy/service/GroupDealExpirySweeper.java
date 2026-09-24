package com.blocbuy.groupbuy.service;

import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

    private final PaidGroupDealRepository deals;
    private final GroupDealExpiryService expiry;

    /**
     * Counts of one sweep.
     *
     * @param due teams selected as overdue
     * @param expired teams this sweep resolved
     * @param alreadyResolved teams a concurrent writer resolved first (filled, or another sweep)
     * @param failed teams whose expiry threw; they stay open and the next sweep retries them
     */
    public record Result(int due, int expired, int alreadyResolved, int failed) {}

    /**
     * Resolves up to {@code limit} overdue teams, oldest deadline first. A failure on one team is
     * logged and counted, never propagated, so it cannot stop the others.
     */
    public Result sweep(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        // Candidate selection only: expireOne re-reads the clock under the row lock and decides.
        Instant now = deals.currentTime();
        List<UUID> due = deals.dueForExpiry(now, limit);
        int expired = 0;
        int alreadyResolved = 0;
        int failed = 0;
        for (UUID dealId : due) {
            try {
                if (expiry.expireOne(dealId)) {
                    expired++;
                } else {
                    alreadyResolved++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Team {} expiry failed; the next sweep retries it", dealId, e);
            }
        }
        return new Result(due.size(), expired, alreadyResolved, failed);
    }
}
