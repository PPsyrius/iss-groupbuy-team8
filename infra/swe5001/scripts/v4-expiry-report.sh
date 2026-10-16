#!/usr/bin/env bash
# Fixed read-only V4 source/effect report. Starts one billed db-init task, not a migration.
# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
[ "$#" -eq 0 ] || die 'v4-expiry-report.sh accepts no arguments or arbitrary SQL'
guard_account
[[ "${V4_RUN_LABEL:-}" =~ ^[a-zA-Z0-9-]{1,24}$ ]] || die 'invalid V4_RUN_LABEL'
source_sql=$(base64 < "$REPO_ROOT/backend/blocbuy-bootstrap/src/main/resources/db/seed/load/v4-source-report.sql" | tr -d '\n')
notification_sql=$(base64 < "$REPO_ROOT/backend/blocbuy-bootstrap/src/main/resources/db/seed/load/v4-notification-report.sql" | tr -d '\n')
command="set -eu
source=\$(printf '%s' '$source_sql' | base64 -d | PGDATABASE=commerce psql --no-psqlrc -qAt -v ON_ERROR_STOP=1 -v run_label='$V4_RUN_LABEL')
printf '%s' '$notification_sql' | base64 -d | PGDATABASE=notification psql --no-psqlrc -qAt -v ON_ERROR_STOP=1 -v source=\"\$source\"
echo V4_REPORT_COMPLETE"
overrides=$(jq -nc --arg command "$command" '{containerOverrides:[{name:"db-init",command:["sh","-c",$command],environment:[{name:"PGOPTIONS",value:"-c default_transaction_read_only=on"}]}]}')
run_db_init_task "$overrides" 'V4_REPORT_COMPLETE|ERROR'
[ "$DB_INIT_EXIT_CODE" = 0 ] || die 'V4 report task failed'
grep -Fxq V4_REPORT_COMPLETE <<< "$DB_INIT_OUTPUT" || die 'V4 report completion marker is missing'
report=$(printf '%s\n' "$DB_INIT_OUTPUT" | sed -n '/^{/p' | jq -sce 'select(length == 1) | .[0]') || die 'expected exactly one V4 report'
jq -e --arg run "$V4_RUN_LABEL" '.schema == 1 and .runLabel == $run and (.observedAt | type == "string") and
  all([.teams,.expiredTeams,.outboxEvents,.publishedEvents,.failedEvents,.notifications,.consumedEvents][];
    type == "number" and . >= 0 and . == floor)' <<< "$report" > /dev/null || die 'incomplete V4 report'
printf '%s\n' "$report"
