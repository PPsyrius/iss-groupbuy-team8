-- notification.sql — notification domain DDL (schema: domain, PostgreSQL 18).
-- Conventions: no FK / no CHECK — reference validity, allowed status values, and concurrency
-- rules are application-managed (pgsql-ddl conventions); primary keys are native uuid with
-- UUIDv7 via uuidv7() (PG18+); instants timestamptz (UTC).

-- In-app notifications to users (deal success/failure, payment result, shipment).
CREATE TABLE biz_notification (
    id           uuid         NOT NULL DEFAULT uuidv7(),
    user_id      uuid         NOT NULL,
    type         varchar(32)  NOT NULL,
    title        varchar(128) NOT NULL,
    content      varchar(1024) NOT NULL,
    biz_ref_type varchar(32),
    biz_ref_id   uuid,
    has_read     boolean      NOT NULL DEFAULT false,
    read_time    timestamptz,
    create_time  timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by    uuid,
    update_time  timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    uuid,
    deleted      boolean      NOT NULL DEFAULT false,
    CONSTRAINT pk_biz_notification PRIMARY KEY (id)
);

COMMENT ON TABLE  biz_notification IS 'In-app user notifications; created by domain events, marked read by the user.';
COMMENT ON COLUMN biz_notification.user_id IS 'Recipient user; validity enforced by the application.';
COMMENT ON COLUMN biz_notification.type IS 'Notification type (e.g. deal_succeeded, deal_failed, payment_succeeded); allowed values enforced by the application.';
COMMENT ON COLUMN biz_notification.biz_ref_type IS 'Kind of the referenced business object (e.g. group_deal, order); null when not applicable.';
COMMENT ON COLUMN biz_notification.biz_ref_id IS 'Referenced business object id; no FK, lookup only.';
COMMENT ON COLUMN biz_notification.has_read IS 'Read flag; set by the application together with read_time.';
COMMENT ON COLUMN biz_notification.read_time IS 'Instant the user read the notification; UTC.';

CREATE INDEX idx_biz_notification_user_unread
    ON biz_notification (user_id, create_time DESC)
    WHERE deleted = false AND has_read = false;

CREATE INDEX idx_biz_notification_user_id
    ON biz_notification (user_id, create_time DESC)
    WHERE deleted = false;

CREATE INDEX idx_biz_notification_biz_ref
    ON biz_notification (biz_ref_type, biz_ref_id)
    WHERE deleted = false;

-- Application obligations:
-- * Creation is driven by in-process domain events (no MQ in the stack); the creating use case
--   runs in the same local transaction as the business state change.
-- * Mark-read updates has_read + read_time together; scoped to the owning user (object-level
--   authorization server-side).
-- * Delivery to external channels (email/push), if added later, is a separate idempotent job;
--   this table remains the source of truth.
-- * Retention/purge: application-orchestrated archival of old read rows; soft delete otherwise.
