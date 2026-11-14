#!/usr/bin/env bash
# Read-only report and pre-seed burst guards; never uses credentials or creates AWS resources.
set -euo pipefail
dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo=$(cd "$dir/../../.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/repo/scripts" "$work/results" "$work/repo/backend/blocbuy-bootstrap/src/main/resources/db/seed/load" "$work/repo/perf/k6"
cp "$dir/v4-expiry-report.sh" "$dir/v4-burst.sh" "$work/repo/scripts/"
cp "$repo/backend/blocbuy-bootstrap/src/main/resources/db/seed/load/"v4-*-report.sql \
  "$work/repo/backend/blocbuy-bootstrap/src/main/resources/db/seed/load/"
cp "$repo/perf/k6/v4-source-report.sql" "$work/repo/perf/k6/"
unset V4_EXPECTED_COUNT
export REPO_ROOT="$work/repo" SWE5001_DIR="$work/repo" CLUSTER=blocbuy-swe5001 FAULT=normal
export V4_RUN_LABEL=fixture CONFIRM_V4_SEED=2000 V4_RESULTS_DIR="$work/results"
EXPECTED_NOTIFICATION_DIGEST="sha256:$(printf 'd%.0s' {1..64})"
export EXPECTED_NOTIFICATION_DIGEST
cat > "$work/repo/scripts/lib.sh" <<'LIB'
set -euo pipefail
guard_account() { :; }
die() { printf '%s\n' "$*" >&2; exit 1; }
log() { printf '%s\n' "$*" >&2; }
tf_init() { :; }
tf() { printf '{"queues":{"notification":"https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-notification"},"dlqs":{"notification":"https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-notification-dlq"}}\n'; }
run_db_init_task() {
  [ "${#1}" -lt 8192 ] || die 'override exceeds ECS limit'
  jq -e '.containerOverrides[0].environment == [{name:"PGOPTIONS",value:"-c default_transaction_read_only=on"}]' <<< "$1" > /dev/null
  command=$(jq -r '.containerOverrides[0].command[2]' <<< "$1")
  printf '%s\n' "$command" > "$REPO_ROOT/task-command"
  [[ "$command" == *'PGDATABASE=commerce psql'* && "$command" == *'PGDATABASE=notification psql'* && "$command" == *V4_REPORT_COMPLETE* ]]
  DB_INIT_EXIT_CODE=0
  if [ "$FAULT" = stream_handoff ]; then
    DB_INIT_OUTPUT=$(PATH="$REPO_ROOT/bin:$PATH" sh "$REPO_ROOT/task-command")
    return
  fi
  [ "$FAULT" != task_failure ] || DB_INIT_EXIT_CODE=1
  DB_INIT_OUTPUT=$(jq -nc --argjson count "${V4_EXPECTED_COUNT-2000}" '{schema:1,runLabel:"fixture",expectedCount:$count,observedAt:"2026-10-16T00:05:00Z",teams:$count,expiredTeams:$count,outboxEvents:$count,publishedEvents:$count,failedEvents:0,notifications:$count,consumedEvents:$count}')
  [ "$FAULT" != missing_count ] || DB_INIT_OUTPUT=$(jq -c 'del(.expectedCount)' <<< "$DB_INIT_OUTPUT")
  [ "$FAULT" != wrong_count ] || DB_INIT_OUTPUT=$(jq -c '.expectedCount = 2000' <<< "$DB_INIT_OUTPUT")
  if [ "$FAULT" = collector ] || [ "$FAULT" = collector_incomplete ]; then
    local calls=0
    [ ! -f "$REPO_ROOT/report-calls" ] || calls=$(cat "$REPO_ROOT/report-calls")
    calls=$((calls + 1))
    printf '%s\n' "$calls" > "$REPO_ROOT/report-calls"
    [ "$calls" != 1 ] || cat "$REPO_ROOT/clock" > "$REPO_ROOT/report-first-time"
    [ "$calls" != 2 ] || cat "$REPO_ROOT/clock" > "$REPO_ROOT/report-complete-time"
    if [ "$calls" = 1 ] || [ "$FAULT" = collector_incomplete ]; then
      DB_INIT_OUTPUT=$(jq -c '.expiredTeams -= 1 | .publishedEvents -= 1 | .notifications -= 1 | .consumedEvents -= 1' <<< "$DB_INIT_OUTPUT")
    fi
  fi
  [ "$FAULT" != bad_report ] || DB_INIT_OUTPUT='{"schema":1,"runLabel":"fixture"}'
  [ "$FAULT" != duplicate_report ] || DB_INIT_OUTPUT="$DB_INIT_OUTPUT
$DB_INIT_OUTPUT"
  [ "$FAULT" = missing_marker ] || DB_INIT_OUTPUT="$DB_INIT_OUTPUT
V4_REPORT_COMPLETE"
}
LIB
git() { if [[ "$*" == *'status --porcelain'* ]]; then [ "$FAULT" != dirty ] || printf ' M local\n'; else printf 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n'; fi; }
aws() {
  case "$1 $2" in
    'application-autoscaling describe-scalable-targets') jq -nc --arg fault "$FAULT" '{ScalableTargets:[{MinCapacity:1,MaxCapacity:4,SuspendedState:{DynamicScalingInSuspended:($fault=="suspended"),DynamicScalingOutSuspended:false,ScheduledScalingSuspended:false}}]}' ;;
    'ecs describe-services')
      [ "$FAULT" != denied ] || return 1
      jq -nc --arg fault "$FAULT" '{failures:[],services:[{status:"ACTIVE",taskDefinition:"revision:1",desiredCount:1,runningCount:1,pendingCount:0,deployments:[{rolloutState:(if $fault=="rollout" then "IN_PROGRESS" else "COMPLETED" end)}]}]}' ;;
    'ecs describe-task-definition') jq -nc --arg fault "$FAULT" '[{command:null,entryPoint:null,environment:[{name:"BLOCBUY_EVENTS_CONSUMER_ENABLED",value:(if $fault=="consumer_off" then "false" else "true" end)}]}]' ;;
    'ecs list-tasks') printf 'task:1\n' ;;
    'ecs describe-tasks') jq -nc --arg fault "$FAULT" --arg digest "$EXPECTED_NOTIFICATION_DIGEST" '{failures:[],tasks:[{lastStatus:"RUNNING",taskDefinitionArn:"revision:1",containers:[{name:"notification-svc",imageDigest:(if $fault=="digest" then "wrong" else $digest end)}]}]}' ;;
    'sqs get-queue-attributes')
      if [ "$FAULT" = missing_attribute ]; then printf '{"Attributes":{}}\n';
      else
        local visible=0
        if [ "$FAULT" = backlog ] || { [ "$FAULT" = dlq_backlog ] && [[ "$*" == *notification-dlq* ]]; }; then visible=1; fi
        jq -nc --arg count "$visible" '{Attributes:{ApproximateNumberOfMessages:$count,ApproximateNumberOfMessagesNotVisible:"0",ApproximateNumberOfMessagesDelayed:"0"}}'
      fi ;;
    *) printf 'Unexpected AWS call: %s\n' "$*" >&2; return 1 ;;
  esac
}
export -f git aws
bash "$work/repo/scripts/v4-expiry-report.sh" > "$work/report.json"
jq -e '.notifications == 2000 and .consumedEvents == 2000 and .expectedCount == 2000' "$work/report.json" > /dev/null
grep -Fq -- '-v source=' "$work/repo/task-command"
printf 'PASS bounded fixed read-only source/effect report\n'
for fault in task_failure bad_report duplicate_report missing_marker; do
  if FAULT=$fault bash "$work/repo/scripts/v4-expiry-report.sh" > /dev/null 2>&1; then exit 1; fi
  printf 'PASS report rejects %s\n' "$fault"
done
if bash "$work/repo/scripts/v4-expiry-report.sh" arbitrary.sql > /dev/null 2>&1; then exit 1; fi
printf 'PASS report rejects arbitrary SQL/arguments\n'
for count in 20000 40000 80000; do
  V4_EXPECTED_COUNT=$count bash "$work/repo/scripts/v4-expiry-report.sh" > "$work/report.json"
  jq -e --argjson count "$count" '.expectedCount == $count and .notifications == $count and .consumedEvents == $count' "$work/report.json" > /dev/null
  ! grep -Fq -- '-v source=' "$work/repo/task-command"
  grep -Fq 'set source' "$work/repo/task-command"
  printf 'PASS fixed %s report binds count and streams event IDs through stdin\n' "$count"
done
mkdir -p "$work/repo/bin"
cat > "$work/repo/bin/psql" <<'PSQL'
#!/usr/bin/env bash
set -euo pipefail
if [ "$PGDATABASE" = commerce ]; then
  cat > /dev/null
  jq -nc --argjson count "$V4_EXPECTED_COUNT" '{schema:1,runLabel:"fixture",expectedCount:$count,teams:$count,expiredTeams:$count,outboxEvents:$count,publishedEvents:$count,failedEvents:0,eventIds:[range($count) | "0199aaaa-0000-7000-8000-000000000001"]}'
else
  [[ "$*" != *'-v source='* ]]
  IFS= read -r binding
  prefix="\set source '"
  [[ "$binding" == "$prefix"*"'" && "${#binding}" -gt 131072 ]]
  source=${binding#"$prefix"}
  source=${source%"'"}
  cat > /dev/null
  jq -ce --argjson count "$V4_EXPECTED_COUNT" 'select(.eventIds | length == $count) | del(.eventIds) + {observedAt:"2026-10-16T00:05:00Z",notifications:$count,consumedEvents:$count}' <<< "$source"
fi
PSQL
chmod +x "$work/repo/bin/psql"
for count in 20000 40000 80000; do
  V4_EXPECTED_COUNT=$count FAULT=stream_handoff bash "$work/repo/scripts/v4-expiry-report.sh" > "$work/report.json"
  jq -e --argjson count "$count" '.expectedCount == $count and .consumedEvents == $count and (has("eventIds") | not)' "$work/report.json" > /dev/null
  printf 'PASS generated POSIX task command streams all %s event IDs without argv overflow\n' "$count"
done
for fault in missing_count wrong_count; do
  if V4_EXPECTED_COUNT=20000 FAULT=$fault bash "$work/repo/scripts/v4-expiry-report.sh" > /dev/null 2>&1; then exit 1; fi
  printf 'PASS larger report rejects %s\n' "$fault"
done
for value in '' 2001 20001 80001 100000 020000 2e4; do
  if V4_EXPECTED_COUNT=$value bash "$work/repo/scripts/v4-expiry-report.sh" > /dev/null 2>&1; then exit 1; fi
  printf 'PASS report rejects unsupported count %s\n' "$value"
done
cat > "$work/repo/scripts/v4-expiry-seed.sh" <<'SEED'
#!/usr/bin/env bash
printf '%s %s %s\n' "$V4_EXPECTED_COUNT" "$CONFIRM_V4_SEED" "${V4_MIN_SECONDS-300}" > "$REPO_ROOT/seed-called"
if [ "$FAULT" = collector ] || [ "$FAULT" = collector_incomplete ]; then
  cat "$REPO_ROOT/clock" > "$REPO_ROOT/seed-time"
  printf '{"schema":1,"expectedCount":%s}\n' "$V4_EXPECTED_COUNT"
  exit 0
fi
exit 77
SEED
for fault in dirty suspended rollout consumer_off denied digest backlog dlq_backlog missing_attribute; do
  # Evidence directories are test-owned and emptied only by exact file names.
  rm -f "$work/results/scaling-before.json" "$work/results/notification-before.json" "$work/results/observations.jsonl"
  if FAULT=$fault bash "$work/repo/scripts/v4-burst.sh" > /dev/null 2>&1; then printf 'FAIL burst accepted %s\n' "$fault"; exit 1; fi
  [ ! -f "$work/repo/seed-called" ]
  printf 'PASS burst refuses %s before seeding\n' "$fault"
done
for assignment in 'CONFIRM_V4_SEED=' 'CONFIRM_V4_SEED=20000' 'V4_EXPECTED_COUNT=20000' 'V4_EXPECTED_COUNT=40000' 'V4_EXPECTED_COUNT=80000' 'V4_EXPECTED_COUNT=' 'V4_EXPECTED_COUNT=20001' 'V4_EXPECTED_COUNT=80001' 'V4_MAX_SECONDS=0' 'V4_MAX_SECONDS=999999999999999999999' 'V4_MIN_SECONDS=' 'V4_MIN_SECONDS=299' 'V4_MIN_SECONDS=1800' 'V4_MIN_SECONDS=1801' 'V4_MIN_SECONDS=999999999999999999999' 'EXPECTED_NOTIFICATION_DIGEST=wrong'; do
  if env "$assignment" bash "$work/repo/scripts/v4-burst.sh" > /dev/null 2>&1; then exit 1; fi
  printf 'PASS burst rejects invalid %s\n' "${assignment%%=*}"
done
for count in 20000 40000 80000; do
  for min in 300 599 1800; do
    if V4_EXPECTED_COUNT=$count CONFIRM_V4_SEED=$count V4_MIN_SECONDS=$min bash "$work/repo/scripts/v4-burst.sh" > /dev/null 2>&1; then exit 1; fi
    [ ! -f "$work/repo/seed-called" ]
    printf 'PASS revised %s burst rejects minimum %s before seeding\n' "$count" "$min"
  done
done
rm -f "$work/results/scaling-before.json" "$work/results/notification-before.json" "$work/results/observations.jsonl"
if V4_EXPECTED_COUNT=20000 CONFIRM_V4_SEED=20000 FAULT=dirty bash "$work/repo/scripts/v4-burst.sh" > /dev/null 2>&1; then exit 1; fi
[ ! -f "$work/repo/seed-called" ]
printf 'PASS larger burst still requires a clean reviewed checkout\n'
# Accelerated local clock: only foreground +%s calls advance it; observer reads it.
# No wall-clock wait, credentials, AWS calls or database tasks are involved.
date() {
  if [ "${FAULT:-}" != collector ] && [ "${FAULT:-}" != collector_incomplete ]; then command date "$@"; return; fi
  local now
  now=$(cat "$REPO_ROOT/clock")
  if [ "$1" = +%s ]; then
    now=$((now + 5))
    printf '%s\n' "$now" > "$REPO_ROOT/clock.tmp"
    mv "$REPO_ROOT/clock.tmp" "$REPO_ROOT/clock"
    printf '%s\n' "$now"
  else command date -u -d "@$now" +%FT%TZ; fi
}
sleep() { case "$1" in 15) command sleep 0.15 ;; *) command sleep 0.05 ;; esac; }
export -f date sleep
for case in '20000 collector' '40000 collector' '80000 collector' '80000 collector_incomplete'; do
  read -r count scenario <<< "$case"
  target="$work/results-$count-$scenario"
  mkdir "$target"
  rm -f "$work/repo/report-calls" "$work/repo/report-first-time" "$work/repo/report-complete-time" "$work/repo/seed-time" "$work/repo/seed-called"
  printf '1800000000\n' > "$work/repo/clock"
  status=0
  V4_EXPECTED_COUNT=$count CONFIRM_V4_SEED=$count V4_MAX_SECONDS=1200 \
    V4_RESULTS_DIR="$target" FAULT=$scenario bash "$work/repo/scripts/v4-burst.sh" > /dev/null 2> "$work/$scenario.err" || status=$?
  [ "$(cat "$work/repo/seed-called")" = "$count $count 600" ]
  [ "$(($(cat "$work/repo/report-first-time") - $(cat "$work/repo/seed-time")))" -ge 600 ]
  [ "$(cat "$work/repo/report-calls")" -ge 3 ]
  jq -e --argjson count "$count" '.publishedEvents == ($count - 1)' "$target/database-report-check-1.json" > /dev/null
  [ -s "$target/observations.jsonl" ]
  [ -s "$target/database-report.json" ]
  if [ "$scenario" = collector ]; then
    [ "$status" = 0 ] || { cat "$work/$scenario.err" >&2; exit 1; }
    jq -e --argjson count "$count" '.publishedEvents == $count and .notifications == $count' "$target/database-report.json" > /dev/null
    jq -se --argjson after "$(cat "$work/repo/report-complete-time")" '
      map(select((.observedAt | fromdateiso8601) >= $after)) | length >= 3 and
      ((.[-1].observedAt | fromdateiso8601) - (.[0].observedAt | fromdateiso8601) >= 30)' "$target/observations.jsonl" > /dev/null
    printf 'PASS revised %s collector preserves incomplete reports and waits for exact completion plus post-report quiet\n' "$count"
  else
    [ "$status" != 0 ]
    grep -Fq 'reached its maximum' "$work/$scenario.err"
    printf 'PASS revised collector fails closed at maximum and retains partial reports/observations\n'
  fi
done
