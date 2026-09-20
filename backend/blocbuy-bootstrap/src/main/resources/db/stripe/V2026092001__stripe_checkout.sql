-- Opt-in personal-branch migration. NOT in default Flyway locations.
-- Team review must settle order nullability and migration numbering before integration.
-- Existing V1..V8 checksums remain unchanged. Sandbox: one Stripe account and test mode only.
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
CREATE UNIQUE INDEX uq_checkout_request ON biz_payment(user_id,request_key) WHERE request_key IS NOT NULL;
CREATE UNIQUE INDEX uq_checkout_pending_intent ON biz_payment(intent_id) WHERE status='CREATED' AND intent_id IS NOT NULL;
CREATE UNIQUE INDEX uq_checkout_session ON biz_payment(checkout_session_id) WHERE checkout_session_id IS NOT NULL;
CREATE UNIQUE INDEX uq_checkout_payment_no ON biz_payment(payment_no);
CREATE UNIQUE INDEX uq_checkout_provider_intent ON biz_payment(provider,provider_payment_intent_id)
    WHERE provider_payment_intent_id IS NOT NULL;
ALTER TABLE log_payment_webhook_event ADD COLUMN payment_id uuid, ADD COLUMN processing_result varchar(32);
CREATE TABLE biz_refund (
    id uuid PRIMARY KEY DEFAULT uuidv7(), payment_id uuid NOT NULL UNIQUE, intent_id uuid NOT NULL,
    deal_id uuid, amount numeric(18,2) NOT NULL, currency varchar(4) NOT NULL,
    reason varchar(64) NOT NULL, source_event varchar(128) NOT NULL, status varchar(16) NOT NULL,
    create_time timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE log_payment_review (
    id uuid PRIMARY KEY DEFAULT uuidv7(), payment_id uuid NOT NULL, reason varchar(64) NOT NULL,
    operator_id varchar(64) NOT NULL, create_time timestamptz NOT NULL DEFAULT clock_timestamp()
);
