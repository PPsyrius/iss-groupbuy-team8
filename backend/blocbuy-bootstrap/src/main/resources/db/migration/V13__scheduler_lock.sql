-- V13__scheduler_lock.sql — database-backed single-flight lock for scheduled jobs
-- (schema: domain, PostgreSQL 18). Sprint 2 US10 / US21: two application instances run behind
-- the ALB; the expiry sweeper must execute on exactly one instance per run. The lock is a lease
-- row acquired with SELECT ... FOR UPDATE NOWAIT — a second instance fails fast and skips.
-- Redis is deliberately NOT used: a lock that decides whether money is refunded is business
-- state, and PostgreSQL is the only system of record.
-- Conventions: no FK / no CHECK; primary keys are native uuid with UUIDv7 via uuidv7() (PG18+).

-- One lease row per scheduled job.
CREATE TABLE sys_scheduler_lock (
    id           uuid         NOT NULL DEFAULT uuidv7(),
    lock_key     varchar(64)  NOT NULL,
    owner        varchar(128) NOT NULL,
    locked_until timestamptz  NOT NULL,
    create_time  timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by    uuid,
    update_time  timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    uuid,
    deleted      boolean      NOT NULL DEFAULT false,
    version      integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_sys_scheduler_lock PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_scheduler_lock IS 'Database-backed lease rows giving scheduled jobs single-flight execution across instances; PostgreSQL is the lock of record, never Redis.';
COMMENT ON COLUMN sys_scheduler_lock.lock_key IS 'Job identifier, e.g. team-expiry-sweep; exactly one active lease per key.';
COMMENT ON COLUMN sys_scheduler_lock.owner IS 'Identifier of the instance holding the lease (hostname or instance id).';
COMMENT ON COLUMN sys_scheduler_lock.locked_until IS 'Lease expiry instant; a crashed holder cannot block the job past this point; UTC.';
COMMENT ON COLUMN sys_scheduler_lock.version IS 'Optimistic-lock token for lease renewal and release.';

CREATE UNIQUE INDEX uq_sys_scheduler_lock_key
    ON sys_scheduler_lock (lock_key)
    WHERE deleted = false;

-- Application obligations:
-- * Acquire: one short transaction doing SELECT ... FOR UPDATE NOWAIT on the lock_key row; a
--   lock-not-available error means another instance is sweeping — skip this run.
-- * Take-over: if locked_until is in the past, the lease is stale and may be claimed by the
--   acquiring instance (update owner and locked_until under the same row lock).
-- * Release or renew by extending locked_until at the end of each sweep; one log line per run
--   with the number of deals resolved.
-- * The unique index on lock_key is the backstop against two holders; the unique index on
--   biz_refund.payment_id (V9) is the backstop against duplicate refund obligations if the
--   lock is ever lost.
