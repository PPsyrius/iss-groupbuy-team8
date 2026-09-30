ALTER TABLE biz_purchase_intent ADD COLUMN expired_at timestamptz;
COMMENT ON COLUMN biz_purchase_intent.expired_at IS
    'Database-clock soft expiry of an unpaid quote; never deletes the frozen quote';
