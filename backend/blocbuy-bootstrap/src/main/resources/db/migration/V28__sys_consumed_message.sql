-- #446 / backend ADR 0007: consumer idempotency ledger, written in the same transaction as the
-- consumer's effects. Immutable system log with a reduced column set: no surrogate id, audit actors,
-- update time or soft delete, because a row is never changed and deleting one would re-enable replay.
CREATE TABLE sys_consumed_message (
    event_id    uuid        NOT NULL,
    consumer    varchar(64) NOT NULL,
    consumed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_consumed_message PRIMARY KEY (event_id, consumer)
);
COMMENT ON TABLE sys_consumed_message IS 'Integration events already applied by a consumer; a duplicate (event_id, consumer) is a no-op. No retention job yet.';
COMMENT ON COLUMN sys_consumed_message.event_id IS 'Integration envelope eventId (producer outbox UUIDv7); never an SQS MessageId.';
COMMENT ON COLUMN sys_consumed_message.consumer IS 'Logical consumer name, e.g. notification; one ledger per consumer sharing the table.';
COMMENT ON COLUMN sys_consumed_message.consumed_at IS 'Commit-transaction start time of the first successful application.';
