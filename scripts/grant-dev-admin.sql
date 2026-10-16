-- Grant the admin role to one signed-in account on dev or the local full stack (US34, #222).
-- Manual, one-time, idempotent. Grants never travel through Flyway (#68). On dev (database BlocBuy)
-- run through the SSM 5432 tunnel:
--   psql -h 127.0.0.1 -p 5432 -U <db user> -d BlocBuy -v admin_email='<google email>' -f scripts/grant-dev-admin.sql
-- On the local full stack (database Blocbuy, fullstack-localhost/compose.yaml), from the repository root:
--   docker compose -f fullstack-localhost/compose.yaml exec -T postgres psql -U blocbuy -d Blocbuy -v admin_email='<google email>' -f - < scripts/grant-dev-admin.sql
-- On a SWE5001 distributed session (identity database on the private RDS), as the db-init task:
--   infra/swe5001/scripts/grant-admin.sh '<google email>'
-- The account must have signed in once. First run: granted = 1, already_admin = 0. Rerun:
-- granted = 0, already_admin = 1. A missing or inactive account changes nothing.
-- Revoke (soft delete, keeps the audit row):
--   UPDATE domain.rel_user_role SET deleted = true WHERE id = '<grant row id>';
\set ON_ERROR_STOP on
BEGIN;

SELECT count(*) = 1 AS account_found
FROM domain.sys_user
WHERE email = :'admin_email' AND deleted = false AND status = 'active' \gset
\if :account_found
\else
  \echo 'No single active account with that email; sign in once first.'
  ROLLBACK;
  \quit
\endif

WITH granted AS (
    INSERT INTO domain.rel_user_role (user_id, role_id)
    SELECT u.id, r.id
    FROM domain.sys_user u
    JOIN domain.sys_role r ON r.role_code = 'admin' AND r.deleted = false
    WHERE u.email = :'admin_email' AND u.deleted = false
    ON CONFLICT (user_id, role_id) WHERE deleted = false DO NOTHING
    RETURNING id, create_time
)
SELECT (SELECT count(*) FROM granted) AS granted,
       (SELECT id FROM granted) AS grant_row_id,
       (SELECT create_time FROM granted) AS granted_at,
       (SELECT count(*)
        FROM domain.rel_user_role ur
        JOIN domain.sys_role r ON r.id = ur.role_id AND r.role_code = 'admin'
        JOIN domain.sys_user u ON u.id = ur.user_id
        WHERE u.email = :'admin_email' AND ur.deleted = false) AS already_admin;

COMMIT;
