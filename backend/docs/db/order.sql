-- order.sql — order domain DDL (schema: domain, PostgreSQL 18).
-- Conventions: no FK / no CHECK — reference validity, allowed status values, and concurrency
-- rules are application-managed (pgsql-ddl conventions); primary keys are native uuid with
-- UUIDv7 via uuidv7() (PG18+); money numeric; instants timestamptz (UTC).

-- One order per deal participation; single-item groupbuy model with product snapshot.
CREATE TABLE biz_order (
    id               uuid           NOT NULL DEFAULT uuidv7(),
    order_no         varchar(64)    NOT NULL,
    user_id          uuid           NOT NULL,
    deal_id          uuid,
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
COMMENT ON COLUMN biz_order.user_id IS 'Buyer reference; validity enforced by the application.';
COMMENT ON COLUMN biz_order.deal_id IS 'Group deal this order belongs to; null for non-deal orders if ever introduced.';
COMMENT ON COLUMN biz_order.product_id IS 'Ordered product; the row below keeps a snapshot so later product edits do not rewrite history.';
COMMENT ON COLUMN biz_order.product_title IS 'Snapshot of the product title at order time.';
COMMENT ON COLUMN biz_order.unit_price IS 'Deal unit price at order time, major units, e.g. 12.34.';
COMMENT ON COLUMN biz_order.total_amount IS 'quantity * unit_price, computed and validated by the application.';
COMMENT ON COLUMN biz_order.currency IS 'ISO-4217 code, e.g. sgd.';
COMMENT ON COLUMN biz_order.status IS 'Lifecycle status (e.g. created, paid, shipped, finished, canceled, refunded); allowed values enforced by the application.';
COMMENT ON COLUMN biz_order.paid_time IS 'Instant payment succeeded; UTC.';
COMMENT ON COLUMN biz_order.version IS 'Optimistic-lock token; compared and incremented by the application on state transitions.';

CREATE UNIQUE INDEX uq_biz_order_order_no
    ON biz_order (order_no)
    WHERE deleted = false;

CREATE INDEX idx_biz_order_user_id
    ON biz_order (user_id, create_time DESC)
    WHERE deleted = false;

CREATE INDEX idx_biz_order_deal_id
    ON biz_order (deal_id)
    WHERE deleted = false;

CREATE INDEX idx_biz_order_product_id
    ON biz_order (product_id)
    WHERE deleted = false;

CREATE INDEX idx_biz_order_status
    ON biz_order (status)
    WHERE deleted = false;

-- Application obligations:
-- * total_amount = quantity * unit_price validated in application; unit_price comes from the
--   deal at creation time, never re-read on later edits (snapshot semantics).
-- * Create path validates user, product (on_sale, stock), and deal (open, not expired) in one
--   transaction; stock reservation uses the catalog concurrency protocol.
-- * Status transitions (created -> paid -> shipped -> finished, cancel/refund branches) go
--   through domain behavioral methods with the version optimistic-lock compare-and-swap;
--   payment/cancel/refund callbacks are idempotent.
-- * receiver_* and shipping_address carry PII: never logged in full, never returned to other users.
-- * Physical purge: orders are financial records — retain; if purge ever required, children
--   (payment, group member) are handled first. Orphan audit on deal_id, user_id, product_id.
