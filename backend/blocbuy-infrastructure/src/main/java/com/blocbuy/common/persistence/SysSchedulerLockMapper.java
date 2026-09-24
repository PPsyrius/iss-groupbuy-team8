package com.blocbuy.common.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Lease SQL on {@code sys_scheduler_lock}; each statement is one autocommit round trip. */
@Mapper
public interface SysSchedulerLockMapper {

    /**
     * Inserts the lease row, or takes it over when it has expired or is already ours.
     *
     * @return 1 when acquired, 0 when another owner holds a live lease
     */
    int acquire(
            @Param("lockKey") String lockKey,
            @Param("owner") String owner,
            @Param("leaseSeconds") double leaseSeconds);

    /** Extends a still-live lease owned by this instance; returns zero after expiry or takeover. */
    int renew(
            @Param("lockKey") String lockKey,
            @Param("owner") String owner,
            @Param("leaseSeconds") double leaseSeconds);

    /** Expires the lease now if this owner still holds it; returns the affected row count. */
    int release(@Param("lockKey") String lockKey, @Param("owner") String owner);
}
