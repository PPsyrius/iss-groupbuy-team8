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
-- biz_order is no longer created here: V12__order.sql owns the real table since the UC10 order
-- slice merged (2026-09-22). This fixture's inserts and reads target that table; the columns it
-- does not supply (product_image, receiver_*, shipping_address, cancel_time, finish_time,
-- create_by, update_by) are all nullable there.
CREATE TABLE stripe_local_expiry_request (
 payment_id uuid PRIMARY KEY, requested_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 completed_at timestamptz, attempts integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
