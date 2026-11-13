-- Explicit performance fixture, NOT a migration. The original 2,000-team fixture is unchanged.
-- Only the separately approved 20,000-team revision may extend the default workload.
\set ON_ERROR_STOP on
\if :{?expected_count}
\else
\set expected_count 2000
\endif
\if :{?confirm_count}
\else
\set confirm_count 2000
\endif
BEGIN;
SET LOCAL search_path = domain, public;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
SELECT set_config('blocbuy.v4.profile', :'profile', true),
       set_config('blocbuy.v4.run_label', :'run_label', true),
       set_config('blocbuy.v4.recipient', :'recipient', true),
       set_config('blocbuy.v4.expected_count', :'expected_count', true),
       set_config('blocbuy.v4.confirm_count', :'confirm_count', true);

DO $guard$
BEGIN
    IF current_setting('blocbuy.v4.expected_count') NOT IN ('2000', '20000') OR
       current_setting('blocbuy.v4.confirm_count') <> current_setting('blocbuy.v4.expected_count') THEN
        RAISE EXCEPTION 'V4 count must be 2000 or 20000 and explicitly confirmed';
    END IF;
    IF current_setting('blocbuy.v4.profile') <> 'demo' THEN
        RAISE EXCEPTION 'V4 seed requires the demo profile';
    END IF;
    IF current_setting('blocbuy.v4.run_label') !~ '^[a-zA-Z0-9-]{1,24}$' THEN
        RAISE EXCEPTION 'Invalid V4 run label';
    END IF;
    PERFORM current_setting('blocbuy.v4.recipient')::uuid;
    PERFORM pg_advisory_xact_lock(hashtextextended(
        'blocbuy-v4-' || current_setting('blocbuy.v4.run_label'), 0));
    IF EXISTS (SELECT 1 FROM biz_group_deal
               WHERE deal_no LIKE 'V4-EXPIRY-' || current_setting('blocbuy.v4.run_label') || '-%') THEN
        RAISE EXCEPTION 'V4 run label already used; never reset or overwrite prior evidence';
    END IF;
    IF (SELECT count(*) FROM biz_product
        WHERE product_no = 'DEMO-P-0001' AND status = 'on_sale' AND deleted = false) <> 1 THEN
        RAISE EXCEPTION 'Expected the existing active demo product';
    END IF;
END
$guard$;

-- Unpaid teams only: real expiry/outbox/consumer processing remains entirely application-owned.
INSERT INTO biz_group_deal
    (id, deal_no, product_id, leader_user_id, deal_price, currency, target_size,
     current_size, status, start_time, expire_time, create_by, update_by)
SELECT uuidv7(), 'V4-EXPIRY-' || current_setting('blocbuy.v4.run_label') || '-' || lpad(n::text, greatest(4, length(n::text)), '0'),
       product.id, current_setting('blocbuy.v4.recipient')::uuid, 1.00, product.currency,
       3, 0, 'open', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '60 seconds',
       current_setting('blocbuy.v4.recipient')::uuid, current_setting('blocbuy.v4.recipient')::uuid
FROM generate_series(1, current_setting('blocbuy.v4.expected_count')::integer) n
CROSS JOIN biz_product product
WHERE product.product_no = 'DEMO-P-0001' AND product.status = 'on_sale' AND product.deleted = false;

INSERT INTO biz_group_member
    (id, deal_id, user_id, member_role, status, join_time, create_by, update_by)
SELECT uuidv7(), id, leader_user_id, 'leader', 'joined', CURRENT_TIMESTAMP,
       leader_user_id, leader_user_id
FROM biz_group_deal
WHERE deal_no LIKE 'V4-EXPIRY-' || current_setting('blocbuy.v4.run_label') || '-%';

COMMIT;
SELECT json_build_object('schema', 1, 'runLabel', :'run_label', 'expectedCount', :'expected_count'::integer, 'teams', count(*),
    'members', (SELECT count(*) FROM domain.biz_group_member member
                JOIN domain.biz_group_deal deal ON deal.id = member.deal_id
                WHERE deal.deal_no LIKE 'V4-EXPIRY-' || :'run_label' || '-%'),
    'seededAt', min(start_time), 'expiresAt', max(expire_time), 'evidenceKind', 'demo-expiry-seed')
FROM domain.biz_group_deal
WHERE deal_no LIKE 'V4-EXPIRY-' || :'run_label' || '-%';
