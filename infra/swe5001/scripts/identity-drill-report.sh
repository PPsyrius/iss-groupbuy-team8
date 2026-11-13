#!/usr/bin/env bash
# Fixed read-only identity-drill report. One billed db-init task; never arbitrary SQL or a migration.
# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
[ "$#" -eq 0 ] || die 'identity-drill-report.sh accepts no arguments or arbitrary SQL'
[[ "${DRILL_PAYMENT_ID:-}" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]] || die 'DRILL_PAYMENT_ID must be the prepared payment UUID'
event=${DRILL_EVENT_ID:-}
[ -z "$event" ] || [[ "$event" =~ ^evt_[a-zA-Z0-9]{1,120}$ ]] || die 'DRILL_EVENT_ID must be the actual Stripe event ID or unset before delivery'
guard_account
umask 077
sql=$(base64 < "$REPO_ROOT/perf/drills/identity-state.sql" | tr -d '\n')
command="set -eu
printf '%s' '$sql' | base64 -d | PGDATABASE=commerce psql --no-psqlrc -qAt -v ON_ERROR_STOP=1 -v payment_id='$DRILL_PAYMENT_ID' -v event_id='$event'
echo IDENTITY_DRILL_REPORT_COMPLETE"
overrides=$(jq -nc --arg command "$command" '{containerOverrides:[{name:"db-init",command:["sh","-c",$command],environment:[{name:"PGOPTIONS",value:"-c default_transaction_read_only=on"}]}]}')
run_db_init_task "$overrides" 'IDENTITY_DRILL_REPORT_COMPLETE|ERROR'
[ "$DB_INIT_EXIT_CODE" = 0 ] || die 'identity report task failed'
grep -Fxq IDENTITY_DRILL_REPORT_COMPLETE <<< "$DB_INIT_OUTPUT" || die 'identity report completion marker is missing'
report=$(printf '%s\n' "$DB_INIT_OUTPUT" | sed -n '/^{/p' | jq -sce 'select(length == 1) | .[0]') || die 'expected exactly one identity report'
jq -e --arg event "$event" '.schema == 1 and .fixturePayments == 1 and (.observedAt | type == "string") and
  (.paymentState | type == "string") and (.fulfilmentResult == null or (.fulfilmentResult | type == "string")) and
  all([.intentsForActorProduct,.webhookReceiptsForPayment,.participationReceipts,.members,.orders,.refunds,.productStock][];
    type == "number" and . >= 0 and . == floor) and
  .eventIdProvided == ($event != "") and
  (if $event == "" then .webhookReceiptsForEvent == null else (.webhookReceiptsForEvent | type == "number" and . >= 0 and . == floor) end)' <<< "$report" > /dev/null || die 'incomplete/missing-fixture identity report'
printf '%s\n' "$report"
