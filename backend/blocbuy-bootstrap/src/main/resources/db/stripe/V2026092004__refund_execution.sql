-- Opt-in migration; existing team migrations and deployment profiles remain untouched.
ALTER TABLE biz_refund
    ADD COLUMN provider_refund_id varchar(256),
    ADD COLUMN first_attempt_at timestamptz,
    ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN lease_until timestamptz,
    ADD COLUMN version integer NOT NULL DEFAULT 0,
    ADD COLUMN last_result varchar(64),
    ADD COLUMN update_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN create_by uuid,
    ADD COLUMN update_by uuid,
    ADD COLUMN deleted boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX uq_biz_refund_provider_id ON biz_refund(provider_refund_id)
    WHERE provider_refund_id IS NOT NULL;
CREATE INDEX idx_biz_refund_due ON biz_refund(next_attempt_at,id)
    WHERE NOT deleted AND status IN ('PENDING','PROCESSING','SUCCEEDED');
COMMENT ON COLUMN biz_refund.first_attempt_at IS 'Immutable start of provider idempotency retry window';
COMMENT ON COLUMN biz_refund.version IS 'Fencing token: only the latest lease may persist a provider result';
CREATE TABLE log_refund_attempt (
    id uuid PRIMARY KEY DEFAULT uuidv7(), refund_id uuid NOT NULL,
    version integer NOT NULL, result varchar(64) NOT NULL,
    create_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_log_refund_attempt_refund ON log_refund_attempt(refund_id,create_time);
COMMENT ON TABLE log_refund_attempt IS 'Immutable refund processing audit; no customer or card details';
