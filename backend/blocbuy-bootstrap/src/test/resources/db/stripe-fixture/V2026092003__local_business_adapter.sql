-- Test-only fixture: excluded from the application artifact.
--
-- 2026-09-22: stripe_local_intent and stripe_local_product_terms are gone. They were the storage
-- for a test-only stand-in of the business ports, which has been replaced by the real
-- implementations: biz_purchase_intent (V15) holds intents, and biz_product.team_target_size /
-- team_lifetime_seconds (V14) hold the group terms. biz_order was likewise dropped from here when
-- V12__order.sql took ownership of the real table.
--
-- What remains is the one thing with no production counterpart: a durable queue for the local
-- session-expiry sweeper, which stands in for a scheduler this slice does not ship.
CREATE TABLE stripe_local_expiry_request (
 payment_id uuid PRIMARY KEY, requested_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 completed_at timestamptz, attempts integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
