-- Durable per-item retry backoff for bounded scheduled jobs (PostgreSQL 18).
-- The retry row is independent of the business aggregate so it can commit after that aggregate's
-- transaction rolls back. No FK is used; the owning job ignores rows for resolved/deleted items.

CREATE TABLE sys_scheduled_item_retry (
    id            uuid        NOT NULL DEFAULT uuidv7(),
    job_key       varchar(64) NOT NULL,
    item_id       uuid        NOT NULL,
    failure_count integer     NOT NULL DEFAULT 1,
    retry_after   timestamptz NOT NULL,
    create_time   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     uuid,
    update_time   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by     uuid,
    deleted       boolean     NOT NULL DEFAULT false,
    version       integer     NOT NULL DEFAULT 0,
    CONSTRAINT pk_sys_scheduled_item_retry PRIMARY KEY (id)
);

COMMENT ON TABLE sys_scheduled_item_retry IS 'Durable backoff state for failed items in bounded scheduled jobs.';
COMMENT ON COLUMN sys_scheduled_item_retry.job_key IS 'Scheduled job identifier, such as team-expiry-sweep.';
COMMENT ON COLUMN sys_scheduled_item_retry.item_id IS 'Application-owned item deferred by the named job; no database foreign key.';
COMMENT ON COLUMN sys_scheduled_item_retry.failure_count IS 'Number of failed attempts observed for this job and item.';
COMMENT ON COLUMN sys_scheduled_item_retry.retry_after IS 'Database-clock instant after which the item becomes eligible again.';

CREATE UNIQUE INDEX uq_sys_scheduled_item_retry_job_item
    ON sys_scheduled_item_retry (job_key, item_id)
    WHERE deleted = false;

-- Application obligations:
-- * record backoff only after the item's failed transaction has rolled back;
-- * candidate queries exclude a row only while retry_after is in the future;
-- * resolved or deleted business items may leave harmless retry history behind;
-- * validate job keys and item ownership in the application; no FK/CHECK is intentional.
