-- Read-only rollout evidence. No row data, credentials or mutations are emitted.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';
SELECT json_build_object(
    'observed_at', transaction_timestamp(),
    'database', current_database(),
    'pending_checkout_payments', (
        SELECT count(*) FROM domain.biz_payment
        WHERE NOT deleted AND status = 'created' AND checkout_session_id IS NOT NULL
    ),
    'pending_checkout_past_deadline', (
        SELECT count(*) FROM domain.biz_payment
        WHERE NOT deleted AND status = 'created' AND checkout_session_id IS NOT NULL
          AND business_deadline <= transaction_timestamp()
    ),
    'overdue_open_teams', (
        SELECT count(*) FROM domain.biz_group_deal
        WHERE NOT deleted AND status = 'open' AND expire_time <= transaction_timestamp()
    ),
    'unfinished_refunds', (
        SELECT count(*) FROM domain.biz_refund
        WHERE NOT deleted AND status IN ('pending', 'processing')
    ),
    'runnable_refunds', (
        SELECT count(*) FROM domain.biz_refund r
        WHERE NOT r.deleted AND r.status IN ('pending', 'processing')
          AND r.next_attempt_at <= transaction_timestamp()
          AND (r.lease_until IS NULL OR r.lease_until < transaction_timestamp())
          AND EXISTS (SELECT 1 FROM domain.biz_payment p WHERE p.id = r.payment_id AND NOT p.deleted)
    ),
    'leased_unfinished_refunds', (
        SELECT count(*) FROM domain.biz_refund
        WHERE NOT deleted AND status IN ('pending', 'processing')
          AND lease_until >= transaction_timestamp()
    ),
    'review_or_failed_refunds', (
        SELECT count(*) FROM domain.biz_refund
        WHERE NOT deleted AND status IN ('review', 'failed')
    ),
    'unresolved_payment_cases', (
        SELECT count(*) FROM domain.biz_payment_review_case
        WHERE status <> 'resolved'
    )
);
ROLLBACK;
