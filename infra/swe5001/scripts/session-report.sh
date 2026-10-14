#!/usr/bin/env bash
# Read-only report on the databases of a running distributed session (#456): where the demo
# catalogue seed landed, how many accounts hold the admin role, and who holds each scheduler
# lease. Runs fixed SQL once as the db-init task (run_db_init_task in lib.sh) in read-only
# transactions; it takes no arguments, so nothing from the command line reaches SQL.
#
# Usage: session-report.sh
#   Expect: catalogue rows and one seed migration in commerce only; admin_accounts 1 after
#   grant-admin.sh; one owner per lease key, each a commerce task's host name, with version
#   growing between two reports (one runner per job across tasks).
#
# Environment: as env-up.sh. The report (no emails or tokens) is also in the db-init log group.

# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[ "$#" -eq 0 ] || die "usage: session-report.sh"

guard_account

# The three databases of the distributed map (runtime/services.tf); every one runs the full
# migration stream, so the catalogue table exists everywhere and only its rows differ.
read -r -d '' report_sql << 'SQL' || true
\set ON_ERROR_STOP on
\pset footer off
\connect identity
SELECT current_database() AS database,
       (SELECT count(*) FROM domain.biz_product) AS catalogue_products,
       (SELECT count(*) FROM domain.flyway_schema_history
        WHERE script = 'R__demo_catalog_seed.sql' AND success) AS seed_migrations;
SELECT count(DISTINCT ur.user_id) AS admin_accounts
FROM domain.rel_user_role ur
JOIN domain.sys_role r ON r.id = ur.role_id AND r.role_code = 'admin' AND NOT r.deleted
WHERE NOT ur.deleted;
\connect notification
SELECT current_database() AS database,
       (SELECT count(*) FROM domain.biz_product) AS catalogue_products,
       (SELECT count(*) FROM domain.flyway_schema_history
        WHERE script = 'R__demo_catalog_seed.sql' AND success) AS seed_migrations;
\connect commerce
SELECT current_database() AS database,
       (SELECT count(*) FROM domain.biz_product) AS catalogue_products,
       (SELECT count(*) FROM domain.flyway_schema_history
        WHERE script = 'R__demo_catalog_seed.sql' AND success) AS seed_migrations;
SELECT lock_key, owner, locked_until, version
FROM domain.sys_scheduler_lock
WHERE NOT deleted
ORDER BY lock_key;
\echo 'session-report: done'
SQL

# The SQL travels inline in a quoted here-document, so the shell expands nothing in it.
# PGOPTIONS makes every connection, including each \connect, read-only.
command="psql --no-psqlrc -f - <<'REPORT_SQL'
$report_sql
REPORT_SQL"
overrides=$(jq -nc --arg command "$command" \
  '{containerOverrides: [{name: "db-init", command: ["sh", "-c", $command],
    environment: [{name: "PGDATABASE", value: "commerce"},
                  {name: "PGOPTIONS", value: "-c default_transaction_read_only=on"}]}]}')

step "session report"
run_db_init_task "$overrides" 'session-report: done|ERROR'
if [ "$DB_INIT_EXIT_CODE" != 0 ]; then
  [ -z "$DB_INIT_OUTPUT" ] || printf '%s\n' "$DB_INIT_OUTPUT" >&2
  die "report task exited with '$DB_INIT_EXIT_CODE'; see log group /blocbuy/swe5001/db-init"
fi
# CloudWatch can deliver the rows in parts; never pass off a partial report as complete.
case "$DB_INIT_OUTPUT" in
  *'session-report: done'*) printf '%s\n' "$DB_INIT_OUTPUT" ;;
  *)
    [ -z "$DB_INIT_OUTPUT" ] || printf '%s\n' "$DB_INIT_OUTPUT" >&2
    die "report incomplete in the log so far; read the full output in log group /blocbuy/swe5001/db-init"
    ;;
esac
step_done
