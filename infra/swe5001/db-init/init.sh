#!/bin/sh
# Database bootstrap for the SWE5001 runtime, run as the one-off ECS task `db-init`
# (infra/swe5001/runtime/db-init.tf). Connects as the RDS master user through the
# standard PG* variables. For each database in $DATABASES it creates an owner role
# (used by Flyway) and an app role (used at runtime), the database owned by the owner,
# and the `domain` schema; the app role gets data access only. Passwords come from SSM
# (<DB>_OWNER_PASSWORD, <DB>_APP_PASSWORD) and are re-applied on every run, so a re-run
# converges instead of failing.
set -eu

for db in $DATABASES; do
  upper=$(printf '%s' "$db" | tr '[:lower:]' '[:upper:]')
  owner_password=$(printenv "${upper}_OWNER_PASSWORD")
  app_password=$(printenv "${upper}_APP_PASSWORD")

  echo "db-init: ${db}"
  psql --no-psqlrc --quiet -v ON_ERROR_STOP=1 \
    -v db="$db" \
    -v owner="blocbuy_${db}_owner" \
    -v app="blocbuy_${db}_app" \
    -v owner_password="$owner_password" \
    -v app_password="$app_password" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', :'owner')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'owner') \gexec
SELECT format('CREATE ROLE %I LOGIN', :'app')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'app') \gexec
SELECT format('ALTER ROLE %I PASSWORD %L', :'owner', :'owner_password') \gexec
SELECT format('ALTER ROLE %I PASSWORD %L', :'app', :'app_password') \gexec

-- The master user must be a member of the owner role to create objects it owns.
SELECT format('GRANT %I TO CURRENT_USER', :'owner') \gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'owner')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'db') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'db') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'db', :'app') \gexec

\connect :db
SELECT format('CREATE SCHEMA IF NOT EXISTS domain AUTHORIZATION %I', :'owner') \gexec
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SELECT format('GRANT USAGE ON SCHEMA domain TO %I', :'app') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA domain GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I', :'owner', :'app') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA domain GRANT USAGE, SELECT ON SEQUENCES TO %I', :'owner', :'app') \gexec
SQL
done

echo "db-init: done"
