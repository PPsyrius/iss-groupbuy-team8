-- V9__order.sql — order domain system of record (schema: domain, PostgreSQL 18).
-- Promotes backend/docs/db/order.sql into Flyway with three Sprint 2 additions (UC10 / US19 / US20):
-- a payment_id dedup key, a per-deal-per-user uniqueness guarantee, and a narrowed status
-- vocabulary (pending -> confirmed | cancelled) matching the team-lifecycle-driven order model.
-- Conventions: no FK / no CHECK — reference validity, allowed status values, and concurrency
-- rules are application-managed (pgsql-ddl conventions); primary keys are native uuid with
-- UUIDv7 via uuidv7() (PG18+); money numeric; instants timestamptz (UTC).

-- One order per paid deal participation; single-item groupbuy model with product snapshot.
CREATE TABLE biz_order (
    id               uuid           NOT NULL DEFAULT uuidv7(),
    order_no         varchar(64)    NOT NULL,
    payment_id       uuid           NOT NULL,
    user_id          uuid           NOT NULL,
    deal_id          uuid           NOT NULL,
    product_id       uuid           NOT NULL,
    product_title    varchar(256)   NOT NULL,
    product_image    varchar(1024),
    quantity         integer        NOT NULL,
    unit_price       numeric(18, 2) NOT NULL,
    total_amount     numeric(18, 2) NOT NULL,
    currency         varchar(4)     NOT NULL,
    status           varchar(16)    NOT NULL,
    receiver_name    varchar(64),
    receiver_phone   varchar(32),
    shipping_address varchar(1024),
    paid_time        timestamptz,
    cancel_time      timestamptz,
    finish_time      timestamptz,
    create_time      timestamptz    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        uuid,
    update_time      timestamptz    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        uuid,
    deleted          boolean        NOT NULL DEFAULT false,
    version          integer        NOT NULL DEFAULT 0,
    CONSTRAINT pk_biz_order PRIMARY KEY (id)
);

COMMENT ON TABLE  biz_order IS 'Buyer orders; PostgreSQL is the source of truth for order state, never Redis.';
COMMENT ON COLUMN biz_order.order_no IS 'Business-facing order number; unique among active rows.';
COMMENT ON COLUMN biz_order.payment_id IS 'Payment that produced this order; exactly one order per payment is enforced by uq_biz_order_payment_id, so a replayed confirmation collides at the database.';
COMMENT ON COLUMN biz_order.user_id IS 'Buyer reference; validity enforced by the application.';
COMMENT ON COLUMN biz_order.deal_id IS 'Group deal this order belongs to; validity enforced by the application.';
COMMENT ON COLUMN biz_order.product_id IS 'Ordered product; the snapshot columns keep history stable so later product edits do not rewrite purchases.';
COMMENT ON COLUMN biz_order.product_title IS 'Snapshot of the product title at order time.';
COMMENT ON COLUMN biz_order.quantity IS 'Number of units purchased; single-item groupbuy model keeps this at 1 for now.';
COMMENT ON COLUMN biz_order.unit_price IS 'Deal unit price at order time, major units, e.g. 12.34.';
COMMENT ON COLUMN biz_order.total_amount IS 'quantity * unit_price, computed and validated by the application.';
COMMENT ON COLUMN biz_order.currency IS 'ISO-4217 code, e.g. sgd.';
COMMENT ON COLUMN biz_order.status IS 'Lifecycle status (pending, confirmed, cancelled); allowed values and transitions enforced by the application.';
COMMENT ON COLUMN biz_order.paid_time IS 'Instant the backing payment succeeded; UTC.';
COMMENT ON COLUMN biz_order.cancel_time IS 'Instant the order was cancelled (team expiry or business rejection); UTC.';
COMMENT ON COLUMN biz_order.finish_time IS 'Reserved for a future fulfilment terminal state; null while status is pending/confirmed/cancelled.';
COMMENT ON COLUMN biz_order.version IS 'Optimistic-lock token; compared and incremented by the application on state transitions.';

CREATE UNIQUE INDEX uq_biz_order_order_no
    ON biz_order (order_no)
    WHERE deleted = false;

-- US19 core guarantee: one order per payment, enforced at the database, not only in application logic.
CREATE UNIQUE INDEX uq_biz_order_payment_id
    ON biz_order (payment_id)
    WHERE deleted = false;

-- Mirrors uq_biz_group_member_deal_user: no duplicate active orders per user per deal.
CREATE UNIQUE INDEX uq_biz_order_deal_user
    ON biz_order (deal_id, user_id)
    WHERE deleted = false;

-- My Orders view: owner-scoped, newest first.
CREATE INDEX idx_biz_order_user_id
    ON biz_order (user_id, create_time DESC)
    WHERE deleted = false;

CREATE INDEX idx_biz_order_product_id
    ON biz_order (product_id)
    WHERE deleted = false;

-- Application obligations:
-- * total_amount = quantity * unit_price validated in application; unit_price comes from the
--   deal at creation time, never re-read on later edits (snapshot semantics).
-- * Create path runs inside the paid-participation transaction (receipt + member + order as one
--   unit of work) and validates user, product, and deal (open, not expired) there; the order is
--   created only after a verified payment, never from a browser call.
-- * Status transitions (pending -> confirmed on TeamSucceeded, pending -> cancelled on TeamExpired)
--   go through domain behavioral methods with the version optimistic-lock compare-and-swap;
--   both transitions are idempotent under event replay (status-guarded updates).
-- * populate biz_group_member.order_id in the same transaction that inserts the order.
-- * receiver_* and shipping_address carry PII: never logged in full, never returned to other users.
-- * Physical purge: orders are financial records — retain; if purge ever required, children
--   (payment, refund, group member) are handled first. Orphan audit on deal_id, user_id, product_id.
