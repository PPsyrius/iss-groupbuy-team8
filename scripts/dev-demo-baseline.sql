-- Read-only baseline of demo data on dev before and after seeding (US34, #222). Prints no personal
-- data: counts, fixed demo numbers and ids only. Run through the SSM 5432 tunnel:
--   psql -h 127.0.0.1 -p 15432 -U <db user> -d BlocBuy -f scripts/dev-demo-baseline.sql
\set ON_ERROR_STOP on
BEGIN READ ONLY;

\echo '== Flyway: did the local-only demo seed run here?'
SELECT script, installed_on, success
FROM domain.flyway_schema_history
WHERE script LIKE 'R__%';

\echo '== Listings by product number family'
SELECT regexp_replace(product_no, '-?[0-9]+$', '') AS family,
       status, count(*)
FROM domain.biz_product
WHERE deleted = false
GROUP BY 1, 2
ORDER BY 1, 2;

\echo '== Deals on DEMO-P-* listings, with their member rows'
SELECT d.deal_no, d.status, d.current_size, d.target_size, d.expire_time,
       (SELECT count(*) FROM domain.biz_group_member m
        WHERE m.deal_id = d.id AND m.deleted = false) AS member_rows
FROM domain.biz_group_deal d
JOIN domain.biz_product p ON p.id = d.product_id
WHERE p.product_no LIKE 'DEMO-P-%' AND d.deleted = false
ORDER BY d.deal_no;

\echo '== US34 listings and their team terms'
SELECT product_no, status, team_target_size, team_lifetime_seconds, version
FROM domain.biz_product
WHERE product_no LIKE 'US34-%' AND deleted = false
ORDER BY product_no;

\echo '== Active admin grants (count only)'
SELECT count(*) AS admin_accounts
FROM domain.rel_user_role ur
JOIN domain.sys_role r ON r.id = ur.role_id AND r.role_code = 'admin'
WHERE ur.deleted = false;

ROLLBACK;
