-- V15__purchase_intent.sql — server-owned purchase intents (schema: domain, PostgreSQL 18).
-- The production storage behind PaymentIntentPort / PaymentBusinessPort. A browser never sends a
-- price: it asks for START on a product or JOIN on a team, the server prices the request here
-- under the product or team row lock, and Stripe Checkout is created from this row only. Replaces
-- the test-only stripe_local_intent substitute the payment slice shipped with.
-- Conventions: no FK / no CHECK — reference validity, allowed operation/status values and
-- concurrency rules are application-managed (pgsql-ddl conventions); primary keys native uuid
-- with UUIDv7 via uuidv7() (PG18+); money numeric; instants timestamptz (UTC).

CREATE TABLE biz_purchase_intent (
    id                   uuid           NOT NULL DEFAULT uuidv7(),
    user_id              uuid           NOT NULL,
    operation            varchar(8)     NOT NULL,
    deal_id              uuid           NOT NULL,
    product_id           uuid           NOT NULL,
    product_title        varchar(256)   NOT NULL,
    product_image        varchar(1024),
    amount               numeric(18, 2) NOT NULL,
    currency             varchar(4)     NOT NULL,
    target_size          integer        NOT NULL,
    quote_valid_until    timestamptz    NOT NULL,
    business_deadline    timestamptz    NOT NULL,
    fulfilled_payment_id uuid,
    create_time          timestamptz    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by            uuid,
    update_time          timestamptz    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by            uuid,
    deleted              boolean        NOT NULL DEFAULT false,
    version              integer        NOT NULL DEFAULT 0,
    CONSTRAINT pk_biz_purchase_intent PRIMARY KEY (id)
);

COMMENT ON TABLE  biz_purchase_intent IS 'Server-owned price and term commitments for one checkout attempt; the only source Stripe Checkout is built from, so a browser can never choose an amount.';
COMMENT ON COLUMN biz_purchase_intent.user_id IS 'Buyer the intent was issued to; a checkout by any other user is rejected under the intent lock.';
COMMENT ON COLUMN biz_purchase_intent.operation IS 'START (create a team on product_id) or JOIN (enter the team deal_id); application-validated vocabulary.';
COMMENT ON COLUMN biz_purchase_intent.deal_id IS 'For JOIN the existing team; for START the id the team will be created with, reserved here so the group row lock has a target before the team exists.';
COMMENT ON COLUMN biz_purchase_intent.product_id IS 'Priced product; validity enforced by the application.';
COMMENT ON COLUMN biz_purchase_intent.product_title IS 'Snapshot at quote time; the receipt shows what the buyer saw, not a later edit.';
COMMENT ON COLUMN biz_purchase_intent.amount IS 'Per-buyer major currency units, server-computed from the team price policy (START) or the team deal price (JOIN).';
COMMENT ON COLUMN biz_purchase_intent.target_size IS 'Paid members the team needs; snapshotted so a later product-terms edit cannot move an issued quote.';
COMMENT ON COLUMN biz_purchase_intent.quote_valid_until IS 'Exclusive deadline for starting checkout against this intent; the payment slice also refuses an expired quote at settlement.';
COMMENT ON COLUMN biz_purchase_intent.business_deadline IS 'Exclusive deadline of the team itself: the new expire_time for START, the existing one for JOIN.';
COMMENT ON COLUMN biz_purchase_intent.fulfilled_payment_id IS 'Payment that consumed this intent; null until fulfilment. One intent yields at most one membership and order — a replay of a different payment is rejected, the same payment is answered with the stored result.';

-- One payment per intent, and one intent per payment: a replayed webhook collides at the database
-- rather than creating a second team member or order.
CREATE UNIQUE INDEX uq_biz_purchase_intent_fulfilled_payment
    ON biz_purchase_intent (fulfilled_payment_id)
    WHERE deleted = false AND fulfilled_payment_id IS NOT NULL;

-- Owner-scoped lookup of an unfulfilled intent, the read the checkout path makes.
CREATE INDEX idx_biz_purchase_intent_user
    ON biz_purchase_intent (user_id, create_time DESC)
    WHERE deleted = false;

-- Application obligations:
-- * Lock order is fixed and shared with the payment slice: group (biz_group_deal) first, then the
--   intent, then the payment. Taking them in any other order deadlocks against a concurrent JOIN.
-- * Issuing an intent happens under the product row lock (START, which also reads the latest
--   biz_team_price_policy revision and the product team terms) or the team row lock (JOIN).
-- * quote_valid_until must be strictly before business_deadline for START; for JOIN the quote is
--   capped by the team's existing expire_time.
-- * amount is never accepted from a request. Once written it is immutable: settlement re-reads the
--   locked row and aborts if any quoted field moved.
-- * Fulfilment sets fulfilled_payment_id in the same transaction that inserts the membership and
--   the order, so the three either all exist or none do.
-- * Intents are not financial records; an unfulfilled intent may be purged after its deadline. A
--   fulfilled one is retained as long as its payment.
