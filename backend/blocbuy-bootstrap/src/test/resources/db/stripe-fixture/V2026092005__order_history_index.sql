-- Test-only fixture index; no application migration owns the team's order table here.
CREATE INDEX idx_biz_order_buyer_history ON biz_order(user_id,create_time DESC,id DESC)
    WHERE NOT deleted;
