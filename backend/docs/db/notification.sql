-- Canonical notification schema (PostgreSQL 18, schema domain), implemented by V27__notification.sql
-- and V28__sys_consumed_message.sql.
-- #215 scope amendment 2026-10-13: immutable event effects and one list API; no unread state.
CREATE TABLE biz_notification (
    id uuid NOT NULL DEFAULT uuidv7(),
    event_id uuid NOT NULL,
    user_id uuid NOT NULL,
    reference_id uuid NOT NULL,
    notification_type varchar(32) NOT NULL,
    title varchar(128) NOT NULL,
    content varchar(1024) NOT NULL,
    create_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by uuid,
    update_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by uuid,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_biz_notification PRIMARY KEY (id),
    CONSTRAINT uq_biz_notification_event_user UNIQUE (event_id, user_id)
);
COMMENT ON TABLE biz_notification IS 'Immutable in-app event notifications; no read-state API in Sprint 4.';
COMMENT ON COLUMN biz_notification.event_id IS 'Integration envelope eventId; never an SQS MessageId. Retained uniqueness includes deleted rows to prevent replay resurrection.';
COMMENT ON COLUMN biz_notification.user_id IS 'Recipient resolved by commerce; no identity query from notification.';
COMMENT ON COLUMN biz_notification.reference_id IS 'Team ID for team events or payment ID for payment events; validity managed by the producer.';
COMMENT ON COLUMN biz_notification.notification_type IS 'TeamSucceeded, TeamExpired or PaymentSucceeded; validated by the domain.';
COMMENT ON COLUMN biz_notification.create_by IS 'Null for system event creation; no interactive write endpoint.';
CREATE INDEX idx_biz_notification_user_time ON biz_notification (user_id, create_time DESC, id DESC) WHERE deleted = false;

-- #446: consumer idempotency ledger. Immutable system log with a reduced column set (no surrogate id,
-- audit actors, update time or soft delete); deleting a row would re-enable replay of that event.
CREATE TABLE sys_consumed_message (
    event_id    uuid        NOT NULL,
    consumer    varchar(64) NOT NULL,
    consumed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_consumed_message PRIMARY KEY (event_id, consumer)
);
COMMENT ON TABLE sys_consumed_message IS 'Integration events already applied by a consumer; a duplicate (event_id, consumer) is a no-op. No retention job yet.';
COMMENT ON COLUMN sys_consumed_message.event_id IS 'Integration envelope eventId (producer outbox UUIDv7); never an SQS MessageId.';
COMMENT ON COLUMN sys_consumed_message.consumer IS 'Logical consumer name, e.g. notification; one ledger per consumer sharing the table.';
COMMENT ON COLUMN sys_consumed_message.consumed_at IS 'Start time of the transaction that first applied the event (CURRENT_TIMESTAMP).';

-- Application obligations:
-- * #446 validates the event envelope and producer-resolved recipients; no cross-service joins.
-- * Store the consumed-message marker and recordBatch effects in one local transaction, then ack.
-- * One SQL insert batch, at most 1000 rows, joins the caller transaction; retries retain old effects.
-- * IDs supplied by the consumer must be generated with UuidV7; uuidv7() is the database default.
-- * A type identifies whether reference_id belongs to a team or payment; no reference lookup here.
-- * No write/update/delete HTTP API; reads carry the session user ID and exclude deleted rows.
-- * Retention and soft deletion are not implemented; event-recipient uniqueness survives deletion.
-- * sys_consumed_message: insert (event_id, consumer) with ON CONFLICT DO NOTHING before the effects;
--   0 rows means already consumed, so the consumer skips its effects and acknowledges the message.
-- * The ledger insert and the effects commit or roll back together; a rollback leaves the event
--   unconsumed so redelivery (and finally the DLQ) handles it. Concurrent first deliveries serialize
--   on the primary key: the second waits for the first transaction and then sees the conflict.
-- * No retention: rows are tiny and must outlive the SQS retention window plus any DLQ redrive.
