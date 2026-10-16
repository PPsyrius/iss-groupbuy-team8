#!/usr/bin/env bash
# Fixed, read-only RDS settings report for #458. Starts one billed db-init reporting task,
# not a migration or load workload. No SQL arguments, credentials or state are printed.
# Environment: as env-up.sh; works with either topology because postgres always exists.
# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[ "$#" -eq 0 ] || die "usage: connection-budget.sh"
guard_account
read -r -d '' sql << 'SQL' || true
\set ON_ERROR_STOP on
SELECT json_build_object(
  'maxConnections', current_setting('max_connections')::integer,
  'reservedConnections', current_setting('superuser_reserved_connections')::integer
    + current_setting('reserved_connections')::integer);
\echo 'connection-budget: done'
SQL
command="psql --no-psqlrc -At -f - <<'BUDGET_SQL'
$sql
BUDGET_SQL"
overrides=$(jq -nc --arg command "$command" \
  '{containerOverrides:[{name:"db-init",command:["sh","-c",$command],environment:[
    {name:"PGDATABASE",value:"postgres"},
    {name:"PGOPTIONS",value:"-c default_transaction_read_only=on"}]}]}')
run_db_init_task "$overrides" 'connection-budget: done|ERROR'
[ "$DB_INIT_EXIT_CODE" = 0 ] || die "connection-budget task failed (exit $DB_INIT_EXIT_CODE)"
grep -Fxq 'connection-budget: done' <<< "$DB_INIT_OUTPUT" || die "connection-budget output is incomplete"
# Require exactly one well-formed report; do not mistake a completion marker for data.
report=$(grep '^{' <<< "$DB_INIT_OUTPUT" | jq -sce '
  length == 1 and (.[0] | type == "object" and
    (.maxConnections | type == "number" and . > 0 and . == floor) and
    (.reservedConnections | type == "number" and . >= 0 and . == floor))
  ' > /dev/null && grep '^{' <<< "$DB_INIT_OUTPUT" | jq -c .) || die "invalid connection-budget report"
printf '%s\n' "$report"
