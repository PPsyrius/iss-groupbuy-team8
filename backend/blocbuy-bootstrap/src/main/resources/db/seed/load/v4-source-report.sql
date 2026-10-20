-- Fixed operator report; run with PGOPTIONS default_transaction_read_only=on.
\set ON_ERROR_STOP on
WITH teams AS (
    SELECT id, status FROM domain.biz_group_deal
    WHERE deal_no LIKE 'V4-EXPIRY-' || :'run_label' || '-%' AND deleted = false
), events AS (
    SELECT outbox.id, outbox.published_at, outbox.failed_at
    FROM domain.sys_outbox outbox JOIN teams ON teams.id = outbox.aggregate_id
    WHERE outbox.event_type = 'TeamExpired'
)
SELECT jsonb_build_object('schema', 1, 'runLabel', :'run_label',
    'teams', (SELECT count(*) FROM teams),
    'expiredTeams', (SELECT count(*) FROM teams WHERE status = 'expired'),
    'outboxEvents', (SELECT count(*) FROM events),
    'publishedEvents', (SELECT count(*) FROM events WHERE published_at IS NOT NULL),
    'failedEvents', (SELECT count(*) FROM events WHERE failed_at IS NOT NULL),
    'eventIds', (SELECT coalesce(jsonb_agg(id), '[]'::jsonb) FROM (SELECT id FROM events ORDER BY id LIMIT 2001) bounded));
