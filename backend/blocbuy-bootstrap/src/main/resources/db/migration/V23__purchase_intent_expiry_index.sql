CREATE INDEX CONCURRENTLY idx_biz_purchase_intent_due_expiry
    ON biz_purchase_intent (quote_valid_until, id)
    WHERE deleted = false AND fulfilled_payment_id IS NULL AND expired_at IS NULL;

CREATE INDEX CONCURRENTLY idx_biz_payment_succeeded_intent
    ON biz_payment (intent_id)
    WHERE deleted = false AND status = 'succeeded';
