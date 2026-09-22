-- V16__refund_due_index.sql — narrow the refund due index to unfinished work (schema: domain).
-- idx_biz_refund_due (V11) indexed status IN ('pending','processing','succeeded') to support a
-- claim filter that re-read succeeded refunds from Stripe for 31 days. With the worker about to be
-- put on a schedule, that cost roughly 744 provider reads and 1,488 audit rows per refund and
-- buried real failures in the log, so RefundMapper.claimNext now claims only unfinished work
-- (issue #133). The index predicate follows it: a partial index whose predicate is wider than
-- every query against it is dead weight in the planner and on every write to the table.
-- DROP + CREATE without CONCURRENTLY on purpose: Flyway runs each migration inside a transaction
-- and CONCURRENTLY cannot run there. biz_refund holds a handful of rows at this stage, so the
-- brief ACCESS EXCLUSIVE lock is not worth splitting the migration out of the transaction for.
-- Conventions: no FK / no CHECK — allowed status values stay application-managed (pgsql-ddl).

DROP INDEX IF EXISTS idx_biz_refund_due;

CREATE INDEX idx_biz_refund_due ON biz_refund (next_attempt_at, id)
    WHERE NOT deleted AND status IN ('pending', 'processing');

COMMENT ON INDEX idx_biz_refund_due IS
    'Due queue for the refund worker; terminal refunds are deliberately outside the predicate';
