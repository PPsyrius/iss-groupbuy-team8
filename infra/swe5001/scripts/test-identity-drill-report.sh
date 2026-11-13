#!/usr/bin/env bash
# All cloud operations are stubbed. No credentials, resources or real SQL/task writes.
set -euo pipefail
dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo=$(cd "$dir/../../.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/scripts" "$work/perf/drills"
cp "$dir/identity-drill-report.sh" "$work/scripts/"
cp "$repo/perf/drills/identity-state.sql" "$work/perf/drills/"
export REPO_ROOT=$work FAULT=normal DRILL_PAYMENT_ID=0199aaaa-0000-7000-8000-000000000001
cat > "$work/scripts/lib.sh" <<'LIB'
set -euo pipefail
guard_account() { :; }
die() { printf '%s\n' "$*" >&2; exit 1; }
run_db_init_task() {
  touch "$REPO_ROOT/task-called"
  [ "${#1}" -lt 8192 ] || die 'override too large'
  jq -e '.containerOverrides[0].environment == [{name:"PGOPTIONS",value:"-c default_transaction_read_only=on"}]' <<< "$1" > /dev/null
  command=$(jq -r '.containerOverrides[0].command[2]' <<< "$1")
  [[ "$command" == *'PGDATABASE=commerce psql'* && "$command" == *IDENTITY_DRILL_REPORT_COMPLETE* ]]
  DB_INIT_EXIT_CODE=0
  [ "$FAULT" != failed_task ] || DB_INIT_EXIT_CODE=1
  DB_INIT_OUTPUT=$(jq -nc --arg fault "$FAULT" --arg event "${DRILL_EVENT_ID:-}" '{schema:1,observedAt:"2026-11-13T00:00:00Z",fixturePayments:(if $fault=="missing_fixture" then 0 else 1 end),paymentState:"created",fulfilmentResult:null,intentsForActorProduct:1,webhookReceiptsForPayment:0,eventIdProvided:($event!=""),webhookReceiptsForEvent:(if $event=="" then null else 0 end),participationReceipts:0,members:0,orders:0,refunds:0,productStock:10}')
  [ "$FAULT" != malformed ] || DB_INIT_OUTPUT='{"schema":1}'
  [ "$FAULT" != duplicate ] || DB_INIT_OUTPUT="$DB_INIT_OUTPUT
$DB_INIT_OUTPUT"
  [ "$FAULT" = missing_marker ] || DB_INIT_OUTPUT="$DB_INIT_OUTPUT
IDENTITY_DRILL_REPORT_COMPLETE"
}
LIB
bash "$work/scripts/identity-drill-report.sh" > "$work/report.json"
jq -e '.fixturePayments == 1 and .webhookReceiptsForEvent == null' "$work/report.json" > /dev/null
printf 'PASS fixed read-only report preserves unknown event count before delivery\n'
DRILL_EVENT_ID=evt_fixture123 bash "$work/scripts/identity-drill-report.sh" > "$work/report.json"
jq -e '.eventIdProvided and .webhookReceiptsForEvent == 0' "$work/report.json" > /dev/null
printf 'PASS event-scoped receipt count is explicit after provider delivery\n'
for fault in failed_task missing_fixture malformed duplicate missing_marker; do
  if FAULT=$fault bash "$work/scripts/identity-drill-report.sh" > /dev/null 2>&1; then exit 1; fi
  printf 'PASS report rejects %s\n' "$fault"
done
for assignment in 'DRILL_PAYMENT_ID=' 'DRILL_PAYMENT_ID=unsafe' 'DRILL_EVENT_ID=unsafe'; do
  rm -f "$work/task-called"
  if env "$assignment" bash "$work/scripts/identity-drill-report.sh" > /dev/null 2>&1; then exit 1; fi
  [ ! -f "$work/task-called" ]
  printf 'PASS invalid %s refused before task start\n' "${assignment%%=*}"
done
if bash "$work/scripts/identity-drill-report.sh" arbitrary.sql > /dev/null 2>&1; then exit 1; fi
printf 'PASS arbitrary arguments/SQL refused\n'
