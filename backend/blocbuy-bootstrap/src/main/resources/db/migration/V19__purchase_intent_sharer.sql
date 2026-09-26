-- V19__purchase_intent_sharer.sql — UC7 share attribution, order side (#30; schema: domain).
-- A JOIN intent freezes the sharer its invite reference resolved to, next to the price it froze.
-- Kept apart from V18 so each domain's migrations stay loadable on their own.

-- Nullable without a default: a catalog-only change in PostgreSQL, no table rewrite.
ALTER TABLE biz_purchase_intent ADD COLUMN sharer_user_id uuid;
COMMENT ON COLUMN biz_purchase_intent.sharer_user_id IS 'Sharer resolved from the invite reference at JOIN quote time; null for START or unattributed JOIN. Not a quote term.';

-- Application obligations:
-- * set only at issue time, only for JOIN, never equal to user_id, and only from an invite for
--   this intent's deal_id; never updated afterwards.
-- * not part of the checkout quote: settlement does not compare it, and payment never sees it.
