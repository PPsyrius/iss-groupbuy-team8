-- V14__product_team_terms.sql — per-product terms for starting a team (schema: domain, PostgreSQL 18).
-- Until now nothing in production said how many paid members a new team needs or how long it may
-- stay open: biz_group_deal carries target_size and expire_time only for teams that already exist,
-- so a START checkout had no server-owned source for either. The Stripe slice worked around this
-- with a test-only stripe_local_product_terms table; these two columns replace that substitute.
-- Conventions: no FK / no CHECK — allowed ranges are application-managed (pgsql-ddl conventions).

ALTER TABLE biz_product
    ADD COLUMN team_target_size      integer,
    ADD COLUMN team_lifetime_seconds integer;

COMMENT ON COLUMN biz_product.team_target_size IS 'Paid members a team started on this product needs to succeed; null means the product cannot start a team. Application requires >= 2.';
COMMENT ON COLUMN biz_product.team_lifetime_seconds IS 'Seconds a team started on this product stays open, from the START checkout. Null means the product cannot start a team. Application requires 2400..86400, so the deadline always outlives the 2400s quote.';

-- Application obligations:
-- * Both columns are read under the biz_product row lock that issues a START quote, together with
--   the latest biz_team_price_policy revision; a product with either column null is not startable
--   and the checkout is rejected before any provider call.
-- * Ranges (target_size >= 2, lifetime 2400..86400) are validated in the application, not by a
--   CHECK: the same bounds live in the domain so the rejection carries a business error code.
-- * Existing rows keep null, so this migration starts no team on its own; the catalog admin path
--   fills them in per product.
-- * Changing either value never alters a team already started: biz_group_deal snapshots
--   target_size and expire_time at creation.
