-- payment.sql — payment domain DDL (schema: domain, PostgreSQL 18).
-- Mirrors migrations V2__payment.sql and V10__payment_refund.sql (db/migration); those
-- migrations are the applied source of truth — keep this document in sync with them.
-- Conventions: no FK / no CHECK — reference validity, allowed status values, and concurrency
-- rules are application-managed (pgsql-ddl conventions); money numeric; instants timestamptz
-- (UTC); primary keys native uuid with UUIDv7 via uuidv7() (PG18+).

-- One row per internal payment attempt against an order.
CREATE TABLE biz_payment (
    id                        uuid           NOT NULL DEFAULT uuidv7(),
    payment_no                varchar(64)    NOT NULL,
    order_id                  uuid,
    user_id                   uuid           NOT NULL,
    provider                  varchar(16)    NOT NULL,
    provider_payment_intent_id varchar(128),
    amount                    numeric(18, 2) NOT NULL,
    currency                  varchar(4)     NOT NULL,
    status                    varchar(16)    NOT NULL,
    failure_code              varchar(64),
    failure_message           varchar(1024),
    paid_time                 timestamptz,
    create_time               timestamptz    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by                 uuid,
    update_time               timestamptz    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by                 uuid,
    deleted                   boolean        NOT NULL DEFAULT false,
    version                   integer        NOT NULL DEFAULT 0,
    CONSTRAINT pk_biz_payment PRIMARY KEY (id)
);

COMMENT ON TABLE  biz_payment IS 'Payment attempts against orders; PostgreSQL is the source of truth, provider state is mirrored here only.';
COMMENT ON COLUMN biz_payment.payment_no IS 'Business-facing payment number; unique among active rows.';
COMMENT ON COLUMN biz_payment.order_id IS 'Owning order reference; null while checkout is pending (V10 relaxed NOT NULL), filled by the paid-participation transaction; validity enforced by the application.';
COMMENT ON COLUMN biz_payment.user_id IS 'Payer reference; validity enforced by the application.';
COMMENT ON COLUMN biz_payment.provider IS 'Payment provider code, e.g. stripe.';
COMMENT ON COLUMN biz_payment.provider_payment_intent_id IS 'Provider-side intent id (e.g. Stripe pi_...); null until the intent is created.';
COMMENT ON COLUMN biz_payment.amount IS 'Major units in the given currency, e.g. 12.34.';
COMMENT ON COLUMN biz_payment.currency IS 'ISO-4217 code, e.g. sgd.';
COMMENT ON COLUMN biz_payment.status IS 'Lifecycle status (e.g. created, succeeded, failed, canceled); allowed values enforced by the application.';
COMMENT ON COLUMN biz_payment.paid_time IS 'Instant the provider confirmed success; UTC.';
COMMENT ON COLUMN biz_payment.version IS 'Optimistic-lock token; compared and incremented by the application on state transitions.';

CREATE UNIQUE INDEX uq_biz_payment_payment_no
    ON biz_payment (payment_no)
    WHERE deleted = false;

CREATE UNIQUE INDEX uq_biz_payment_provider_intent
    ON biz_payment (provider, provider_payment_intent_id)
    WHERE deleted = false AND provider_payment_intent_id IS NOT NULL;

CREATE INDEX idx_biz_payment_order_id
    ON biz_payment (order_id)
    WHERE deleted = false;

CREATE INDEX idx_biz_payment_user_id
    ON biz_payment (user_id)
    WHERE deleted = false;

-- Immutable webhook receipt log; deduplication backstop for provider retries.
CREATE TABLE log_payment_webhook_event (
    id                         uuid         NOT NULL DEFAULT uuidv7(),
    event_id                   varchar(128) NOT NULL,
    event_type                 varchar(64)  NOT NULL,
    provider_payment_intent_id varchar(128),
    payload                    jsonb,
    create_time                timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_log_payment_webhook_event PRIMARY KEY (id)
);

COMMENT ON TABLE  log_payment_webhook_event IS 'Received provider webhook events; insert-before-process makes replay idempotent.';
COMMENT ON COLUMN log_payment_webhook_event.event_id IS 'Unique provider event id; duplicate inserts are rejected and treated as already processed.';
COMMENT ON COLUMN log_payment_webhook_event.payload IS 'Raw event body for reconciliation; never returned to clients.';

CREATE UNIQUE INDEX uq_log_payment_webhook_event_event_id
    ON log_payment_webhook_event (event_id);

CREATE INDEX idx_log_payment_webhook_event_intent
    ON log_payment_webhook_event (provider_payment_intent_id);

-- Refund obligation per affected payment of an expired team; durable queue for the Sprint 3
-- refund job (added by V10__payment_refund.sql).
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

CREATE UNIQUE INDEX uq_biz_refund_payment_id
    ON biz_refund (payment_id)
    WHERE deleted = false;

CREATE INDEX idx_biz_refund_status
    ON biz_refund (status)
    WHERE deleted = false;

CREATE INDEX idx_biz_refund_deal_id
    ON biz_refund (deal_id)
    WHERE deleted = false;

-- Application obligations:
-- * Payment row persisted before any provider call; provider minor-unit conversion happens
--   only in the infrastructure gateway; domain/application use BigDecimal major units.
-- * Webhook dedup: insert event into log_payment_webhook_event first; unique violation on
--   event_id means already processed — skip. Gateway verifies the provider signature.
-- * Status transitions via domain behavioral methods with the version optimistic lock; one
--   active payment per order enforced by the application (no DB constraint by convention).
-- * Success path adjusts order status, deal current_size, and stock under the documented
--   concurrency protocols; all callbacks idempotent.
-- * Refund obligations (V10): RefundRequiredRecorder subscribes to TeamExpired and writes one
--   biz_refund row per paid member; a duplicate-key violation on uq_biz_refund_payment_id is
--   treated as already-recorded (idempotent). No provider call inside any transaction.
