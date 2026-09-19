-- V10__payment_refund.sql — refund obligation records + payment-order lifecycle fix
-- (schema: domain, PostgreSQL 18). Sprint 2 UC6 / UC10: team expiry must leave a durable,
-- reconcilable refund obligation (biz_refund), and the intent flow creates a Payment in CREATED
-- state before any Order exists, so biz_payment.order_id can no longer be NOT NULL.
-- Conventions: no FK / no CHECK — reference validity, allowed status values, and concurrency
-- rules are application-managed (pgsql-ddl conventions); primary keys are native uuid with
-- UUIDv7 via uuidv7() (PG18+); money numeric; instants timestamptz (UTC).

-- One refund obligation per affected payment; the Sprint 3 refund job consumes this queue.
CREATE TABLE biz_refund (
    id           uuid           NOT NULL DEFAULT uuidv7(),
    payment_id   uuid           NOT NULL,
    order_id     uuid,
    deal_id      uuid           NOT NULL,
    user_id      uuid           NOT NULL,
    amount       numeric(18, 2) NOT NULL,
    currency     varchar(4)     NOT NULL,
    status       varchar(16)    NOT NULL,
    create_time  timestamptz    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by    uuid,
    update_time  timestamptz    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    uuid,
    deleted      boolean        NOT NULL DEFAULT false,
    version      integer        NOT NULL DEFAULT 0,
    CONSTRAINT pk_biz_refund PRIMARY KEY (id)
);

COMMENT ON TABLE  biz_refund IS 'Refund obligations for paid members of expired teams; a durable queue the Sprint 3 refund job consumes, never lost in the gap between sprints.';
COMMENT ON COLUMN biz_refund.payment_id IS 'Payment to be refunded; exactly one obligation per payment is enforced by uq_biz_refund_payment_id, so a repeated sweep collides at the database.';
COMMENT ON COLUMN biz_refund.order_id IS 'Cancelled order the payment backed; validity enforced by the application.';
COMMENT ON COLUMN biz_refund.deal_id IS 'Expired deal that triggered the obligation; validity enforced by the application.';
COMMENT ON COLUMN biz_refund.user_id IS 'Payer to be refunded; validity enforced by the application.';
COMMENT ON COLUMN biz_refund.amount IS 'Amount to refund, major units in the given currency; mirrors the payment amount at obligation-creation time.';
COMMENT ON COLUMN biz_refund.currency IS 'ISO-4217 code, e.g. sgd.';
COMMENT ON COLUMN biz_refund.status IS 'Lifecycle status (pending, done); allowed values enforced by the application.';
COMMENT ON COLUMN biz_refund.version IS 'Optimistic-lock token; compared and incremented by the application on state transitions.';

-- Deduplication backstop: a repeated expiry sweep (or replayed TeamExpired event) inserts a
-- duplicate-key violation that the application treats as "already recorded".
CREATE UNIQUE INDEX uq_biz_refund_payment_id
    ON biz_refund (payment_id)
    WHERE deleted = false;

-- The Sprint 3 refund job drains the queue by status.
CREATE INDEX idx_biz_refund_status
    ON biz_refund (status)
    WHERE deleted = false;

CREATE INDEX idx_biz_refund_deal_id
    ON biz_refund (deal_id)
    WHERE deleted = false;

-- The join/initiate intent endpoint persists a Payment in CREATED state before any Order exists;
-- the paid-participation transaction backfills order_id afterwards.
ALTER TABLE biz_payment ALTER COLUMN order_id DROP NOT NULL;

COMMENT ON COLUMN biz_payment.order_id IS 'Owning order reference; null while checkout is pending, filled by the paid-participation transaction; validity enforced by the application.';

-- Application obligations:
-- * RefundRequiredRecorder subscribes to TeamExpired and writes one row per paid member inside
--   the sweep transaction; a duplicate-key violation is treated as already-recorded (idempotent).
-- * The actual provider refund call belongs to Sprint 3; this table is the obligation of record.
-- * Validate deal_id / order_id / user_id references in the same logical operation that inserts
--   the obligation; the expired deal row is locked (SELECT ... FOR UPDATE) by the sweeper.
-- * No provider (Stripe) call inside any database transaction.
-- * Physical purge: refund obligations are financial records — retain; orphan audit on payment_id.
