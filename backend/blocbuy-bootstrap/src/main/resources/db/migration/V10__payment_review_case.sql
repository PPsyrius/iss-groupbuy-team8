-- V10__payment_review_case.sql — operator review work queue for payments needing human inspection.
-- Status codes lowercase (open/resolved); allowed values enforced by the application.

CREATE TABLE biz_payment_review_case (
    case_id uuid PRIMARY KEY DEFAULT uuidv7(),
    payment_id uuid NOT NULL UNIQUE,
    reason varchar(64) NOT NULL,
    owner varchar(64),
    status varchar(16) NOT NULL DEFAULT 'open',
    create_time timestamptz NOT NULL DEFAULT clock_timestamp(),
    last_checked_at timestamptz,
    next_check_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    resolved_at timestamptz,
    provider_summary varchar(512)
);
CREATE INDEX ix_biz_payment_review_case_due ON biz_payment_review_case(next_check_at,case_id)
    WHERE status='open';
