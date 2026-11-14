-- Revised fixture report, read-only. Keep one surplus event ID so excess work fails exact matching.
\set ON_ERROR_STOP on
\if :{?expected_count}
\else
\set expected_count 2000
\endif
-- Fail before the query for unsupported counts, without emitting another result row.
SELECT 1 / ((:'expected_count' IN ('2000', '20000', '40000', '80000')) AND
    (:'run_label' ~ '^[a-zA-Z0-9-]{1,24}$'))::integer AS valid_scope \gset
WITH teams AS (
    SELECT id, status FROM domain.biz_group_deal
    WHERE deal_no ~ ('^V4-EXPIRY-' || :'run_label' || '-[0-9]+$') AND deleted = false
), events AS (
    SELECT outbox.id, outbox.published_at, outbox.failed_at
    FROM domain.sys_outbox outbox JOIN teams ON teams.id = outbox.aggregate_id
    WHERE outbox.event_type = 'TeamExpired'
)
SELECT jsonb_build_object('schema', 1, 'runLabel', :'run_label', 'expectedCount', :'expected_count'::integer,
    'teams', (SELECT count(*) FROM teams),
    'expiredTeams', (SELECT count(*) FROM teams WHERE status = 'expired'),
    'outboxEvents', (SELECT count(*) FROM events),
    'publishedEvents', (SELECT count(*) FROM events WHERE published_at IS NOT NULL),
    'failedEvents', (SELECT count(*) FROM events WHERE failed_at IS NOT NULL),
    'eventIds', (SELECT coalesce(jsonb_agg(id), '[]'::jsonb) FROM
                (SELECT id FROM events ORDER BY id LIMIT (:'expected_count'::integer + 1)) bounded));
