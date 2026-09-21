-- V9__stripe_checkout.sql — Stripe checkout slice (schema: domain, PostgreSQL 18).
-- Conventions follow V1..V8: no FK / no CHECK — reference validity, allowed status values, and
-- concurrency rules are application-managed; lifecycle status codes are lowercase
-- (created/succeeded/failed/canceled) matching the V2 comment on biz_payment.status.
-- biz_payment rows for the checkout flow own no order at insert time, hence order_id becomes
-- nullable (validity enforced by the application, as documented in V2).

ALTER TABLE biz_payment ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE biz_payment
    ADD COLUMN intent_id uuid,
    ADD COLUMN operation varchar(8),
    ADD COLUMN business_id uuid,
    ADD COLUMN request_key varchar(128),
    ADD COLUMN quote_valid_until timestamptz,
    ADD COLUMN business_deadline timestamptz,
    ADD COLUMN session_expires_at timestamptz,
    ADD COLUMN checkout_session_id varchar(255),
    ADD COLUMN checkout_url text,
    ADD COLUMN success_url text,
    ADD COLUMN cancel_url text,
    ADD COLUMN fulfilment_result varchar(32),
    ADD COLUMN fulfilled_deal_id uuid;

COMMENT ON COLUMN biz_payment.intent_id IS 'Local checkout intent id issued by this application; distinct from provider_payment_intent_id (the Stripe pi_... reference).';
COMMENT ON COLUMN biz_payment.fulfilment_result IS 'Post-payment fulfilment outcome code: FULFILLED or REFUND_REQUIRED (uppercase result codes — distinct from the lowercase lifecycle statuses).';

-- One in-flight checkout per idempotency key; V2 already covers payment_no and the provider
-- intent pair (uq_biz_payment_payment_no / uq_biz_payment_provider_intent) — no duplicates here.
CREATE UNIQUE INDEX uq_biz_payment_request_key
    ON biz_payment (user_id, request_key)
    WHERE request_key IS NOT NULL AND deleted = false;
CREATE UNIQUE INDEX uq_biz_payment_pending_intent
    ON biz_payment (intent_id)
    WHERE status = 'created' AND intent_id IS NOT NULL AND deleted = false;
CREATE UNIQUE INDEX uq_biz_payment_checkout_session
    ON biz_payment (checkout_session_id)
    WHERE checkout_session_id IS NOT NULL AND deleted = false;

ALTER TABLE log_payment_webhook_event ADD COLUMN payment_id uuid, ADD COLUMN processing_result varchar(32);

CREATE TABLE biz_refund (
    id uuid PRIMARY KEY DEFAULT uuidv7(), payment_id uuid NOT NULL UNIQUE, intent_id uuid NOT NULL,
    deal_id uuid, amount numeric(18,2) NOT NULL, currency varchar(4) NOT NULL,
    reason varchar(64) NOT NULL, source_event varchar(128) NOT NULL, status varchar(16) NOT NULL,
    create_time timestamptz NOT NULL DEFAULT clock_timestamp()
);
COMMENT ON TABLE  biz_refund IS 'Refund obligations queued after expired deals or provider-mandated refunds; execution columns land in V11.';
COMMENT ON COLUMN biz_refund.status IS 'Lifecycle status, lowercase (pending/processing/succeeded/failed/review); allowed values enforced by the application.';

CREATE TABLE log_payment_review (
    id uuid PRIMARY KEY DEFAULT uuidv7(), payment_id uuid NOT NULL, reason varchar(64) NOT NULL,
    operator_id varchar(64) NOT NULL, create_time timestamptz NOT NULL DEFAULT clock_timestamp()
);
