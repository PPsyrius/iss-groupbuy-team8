package com.blocbuy.groupbuy.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.event.TeamExpired;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.model.GroupDeal;
import com.blocbuy.groupbuy.repository.PaidGroupDealRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves one team whose admission window has closed, in one short transaction: lock the deal,
 * read the database clock under that lock, ask the aggregate for its expired snapshot, persist it
 * under a status guard, then publish {@link TeamExpired}.
 *
 * <p>Only one team per call. The sweep over many teams is the scheduler's job, which keeps this
 * class free of loops and testable without one — and keeps the lock held over a single aggregate,
 * so an expiry never contends with webhook settlement for longer than its own row.
 *
 * <p>Subscribers of {@link TeamExpired} run at {@code BEFORE_COMMIT}, inside this transaction: the
 * refund obligation that follows an expiry must live or die with the expiry itself, never in a
 * window where the team is closed and nobody is owed money.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupDealExpiryService {

    private final PaidGroupDealRepository deals;
    private final ApplicationEventPublisher events;

    /**
     * Resolves one open, overdue team as expired.
     *
     * @return true when this call performed the transition; false when the team was already gone or
     *     another writer resolved it first — both are ordinary replays, not failures
     */
    @Transactional(timeout = 10)
    public boolean expireOne(UUID dealId) {
        if (dealId == null) {
            throw new BizException(GroupbuyErrorCode.INVALID_PARTICIPATION);
        }
        var locked = deals.lockDeal(dealId);
        if (locked.isEmpty()) {
            return false;
        }
        GroupDeal before = locked.orElseThrow();
        Instant now = deals.currentTime();
        GroupDeal after;
        try {
            after = before.expire(now);
            deals.expire(before, after, now);
        } catch (BizException resolvedConcurrently) {
            // A webhook filled the team, or another sweeper reached it first. The status guard is
            // the arbiter; losing that race is the expected outcome, not an error to report.
            log.debug("Team {} was already resolved when expiry ran", dealId);
            return false;
        }
        events.publishEvent(new TeamExpired(after.id(), after.productId(), now));
        return true;
    }
}
