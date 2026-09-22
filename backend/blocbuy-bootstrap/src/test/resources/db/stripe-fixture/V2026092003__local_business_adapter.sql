-- Test-only fixture: excluded from the application artifact.
CREATE TABLE stripe_local_intent (
 id uuid PRIMARY KEY, user_id uuid NOT NULL, operation varchar(8) NOT NULL,
 deal_id uuid NOT NULL, product_id uuid NOT NULL, product_title varchar(256) NOT NULL,
 amount numeric(18,2) NOT NULL, currency varchar(4) NOT NULL,
 target_size integer NOT NULL, quote_until timestamptz NOT NULL, deadline timestamptz NOT NULL,
 fulfilled_payment_id uuid UNIQUE, create_time timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE stripe_local_product_terms (
 product_id uuid PRIMARY KEY, target_size integer NOT NULL, lifetime_seconds integer NOT NULL
);
-- Minimal order persistence with the team's documented names; no shipping/PII functionality.
CREATE TABLE biz_order (
 id uuid PRIMARY KEY, order_no varchar(64) NOT NULL UNIQUE, user_id uuid NOT NULL,
 deal_id uuid NOT NULL, product_id uuid NOT NULL, product_title varchar(256) NOT NULL,
 quantity integer NOT NULL, unit_price numeric(18,2) NOT NULL, total_amount numeric(18,2) NOT NULL,
 currency varchar(4) NOT NULL, status varchar(16) NOT NULL, payment_id uuid NOT NULL UNIQUE,
 paid_time timestamptz NOT NULL, create_time timestamptz NOT NULL DEFAULT clock_timestamp(),
 update_time timestamptz NOT NULL DEFAULT clock_timestamp(), deleted boolean NOT NULL DEFAULT false,
 version integer NOT NULL DEFAULT 0
);
CREATE TABLE stripe_local_expiry_request (
 payment_id uuid PRIMARY KEY, requested_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 completed_at timestamptz, attempts integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
