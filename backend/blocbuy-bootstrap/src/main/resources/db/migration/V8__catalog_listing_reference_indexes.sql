-- UC9 checks ALL historical references, not only active rows. No table/data rewrite or purge.
-- Run at the planned deployment window for populated tables: transactional CREATE INDEX can
-- block writers. Bounds below fail safely rather than waiting indefinitely; use a separately
-- reviewed concurrent-index rollout before deploying to a large live dataset.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

CREATE INDEX idx_biz_group_deal_product_history ON biz_group_deal (product_id);
CREATE INDEX idx_biz_team_price_quote_product_history ON biz_team_price_quote (product_id);
CREATE INDEX idx_biz_product_admin_page ON biz_product (create_time DESC, id DESC)
    WHERE deleted = false;
CREATE INDEX idx_biz_product_admin_status_page ON biz_product (status, create_time DESC, id DESC)
    WHERE deleted = false;

-- Policy history already has the non-partial (product_id, version) unique index.
-- Application obligations: hold the product lock for status/content/delete and new-reference
-- validation; retain every historical row; no physical purge. Future order persistence must
-- expose an owning-domain reference query and join this retention protocol before activation.
