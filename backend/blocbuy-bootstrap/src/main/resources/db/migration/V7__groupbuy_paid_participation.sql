-- PostgreSQL 18. Additive groupbuy-owned receipt; no payment-provider tables or callbacks.
ALTER TABLE biz_group_member ADD COLUMN payment_id uuid;
COMMENT ON COLUMN biz_group_member.payment_id IS 'Trusted local payment ID; historical rows may be null. Never reusable after deletion.';
CREATE UNIQUE INDEX uq_biz_group_member_payment_id ON biz_group_member (payment_id);

CREATE TABLE biz_group_participation_receipt (
    id                  uuid NOT NULL DEFAULT uuidv7(),
    payment_id          uuid NOT NULL,
    request_fingerprint varchar(64) NOT NULL,
    deal_id             uuid,
    outcome             varchar(32),
    deal_status         varchar(16),
    current_size        integer,
    reason_code         varchar(32),
    create_time         timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           uuid NOT NULL,
    update_time         timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           uuid NOT NULL,
    deleted             boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_biz_group_participation_receipt PRIMARY KEY (id),
    CONSTRAINT uq_biz_group_participation_receipt_payment UNIQUE (payment_id)
);
CREATE INDEX idx_biz_group_participation_receipt_deal_id
    ON biz_group_participation_receipt (deal_id);
CREATE INDEX idx_biz_group_participation_receipt_compensation
    ON biz_group_participation_receipt (create_time, id)
    WHERE outcome = 'compensation_required';
COMMENT ON TABLE biz_group_participation_receipt IS 'Immutable completed application result per payment; retained across deal/member deletion. Not a refund execution queue.';
COMMENT ON COLUMN biz_group_participation_receipt.request_fingerprint IS 'Versioned canonical SHA-256 request digest; changed-content replay is rejected.';
COMMENT ON COLUMN biz_group_participation_receipt.outcome IS 'Null only inside an uncommitted reservation; terminal applied or compensation_required.';
COMMENT ON COLUMN biz_group_participation_receipt.reason_code IS 'Stable groupbuy rejection code for payment-owner compensation coordination.';

-- Application obligations:
-- * Lock order: reserve payment receipt, lock receipt, lock deal, then clock_timestamp().
-- * Persist receipt/member/deal together; technical failures roll back all three.
-- * Trusted caller validates confirmed payment, active buyer, accepted catalog/inventory
--   snapshot and business intent. No receipt is proof of provider authentication.
-- * Every member/count/deletion writer must use the deal lock and preserve paid counts.
-- * Keep receipts and payment uniqueness indefinitely; no generic soft delete/purge path.
-- * Future orphan reconciliation is groupbuy-owned; payment owner executes any compensation.
-- * Single-tenant schema. On populated deployments, schedule index creation/ALTER with
--   bounded deployment lock_timeout; no data backfill or destructive rewrite is performed.
