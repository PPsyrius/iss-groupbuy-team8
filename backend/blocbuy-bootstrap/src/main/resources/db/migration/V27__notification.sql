-- V26 is allocated to the outbox in #444 / PR #473; merge it before deploying V27.
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
