-- Personal payment branch, enabled only with the opt-in stripe migration location.
CREATE TABLE biz_payment_review_case (
    case_id uuid PRIMARY KEY DEFAULT uuidv7(),
    payment_id uuid NOT NULL UNIQUE,
    reason varchar(64) NOT NULL,
    owner varchar(64),
    status varchar(16) NOT NULL DEFAULT 'OPEN',
    create_time timestamptz NOT NULL DEFAULT clock_timestamp(),
    last_checked_at timestamptz,
    next_check_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    resolved_at timestamptz,
    provider_summary varchar(512)
);
CREATE INDEX ix_payment_review_due ON biz_payment_review_case(next_check_at,case_id)
    WHERE status='OPEN';
-- Retain unresolved historical evidence when upgrading a previously tested local database.
INSERT INTO biz_payment_review_case(payment_id,reason)
SELECT DISTINCT ON (payment_id) payment_id,reason FROM log_payment_review
WHERE reason NOT LIKE 'RECOVERY_%' ORDER BY payment_id,create_time DESC,id DESC;
