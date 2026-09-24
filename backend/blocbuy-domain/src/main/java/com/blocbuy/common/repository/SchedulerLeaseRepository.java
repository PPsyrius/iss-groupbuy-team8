package com.blocbuy.common.repository;

import java.time.Duration;

/**
 * Single-flight lease for scheduled jobs; PostgreSQL is the lock of record (V13), never Redis.
 *
 * <p>A lease, not a held lock: the database row is locked only for the acquire statement itself.
 * What keeps other instances out for the length of a run is {@code locked_until}, evaluated on the
 * database clock so instances with skewed clocks agree. Losing the lease mid-run is safe for the
 * callers of this port — every job they run is idempotent under its own status guards.
 */
public interface SchedulerLeaseRepository {

    /**
     * Takes the lease when nobody holds it, when the previous holder's lease has run out, or when
     * this owner already holds it.
     *
     * @return true when this owner now holds {@code lockKey} until database-now + {@code lease};
     *     false when another owner holds a live lease and this run should be skipped
     */
    boolean tryAcquire(String lockKey, String owner, Duration lease);

    /**
     * Ends the lease early so the next run need not wait for it to expire.
     *
     * @return false when the lease was no longer ours — it expired and another owner took it over
     */
    boolean release(String lockKey, String owner);
}
