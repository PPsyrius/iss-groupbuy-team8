package com.blocbuy.common.persistence;

import com.blocbuy.common.repository.SchedulerLeaseRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL lease on {@code sys_scheduler_lock}. Deviates from V13's {@code FOR UPDATE NOWAIT}
 * wording by design (ADR 0002 addendum): one upsert with a 3 s statement timeout gives the same
 * guarantees in a single autocommit statement.
 */
@Repository
@RequiredArgsConstructor
public class SchedulerLeaseRepositoryImpl implements SchedulerLeaseRepository {
    /** V13 {@code lock_key varchar(64)}. */
    static final int MAX_KEY_LENGTH = 64;

    /** V13 {@code owner varchar(128)}. */
    static final int MAX_OWNER_LENGTH = 128;

    private final SysSchedulerLockMapper mapper;

    @Override
    public boolean tryAcquire(String lockKey, String owner, Duration lease) {
        requireText(lockKey, MAX_KEY_LENGTH, "lockKey");
        requireText(owner, MAX_OWNER_LENGTH, "owner");
        if (lease == null || lease.toMillis() <= 0) {
            throw new IllegalArgumentException("lease must be at least one millisecond");
        }
        return oneOrZero(mapper.acquire(lockKey, owner, lease.toMillis() / 1000.0));
    }

    @Override
    public boolean release(String lockKey, String owner) {
        requireText(lockKey, MAX_KEY_LENGTH, "lockKey");
        requireText(owner, MAX_OWNER_LENGTH, "owner");
        return oneOrZero(mapper.release(lockKey, owner));
    }

    /** The partial unique index allows one live row per key; anything else is a broken schema. */
    private static boolean oneOrZero(int rows) {
        if (rows != 0 && rows != 1) {
            throw new IllegalStateException("Unexpected scheduler lease row count: " + rows);
        }
        return rows == 1;
    }

    private static void requireText(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    name + " must be non-blank and at most " + maxLength + " characters");
        }
    }
}
