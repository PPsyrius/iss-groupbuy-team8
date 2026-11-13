#!/usr/bin/env bash
# Stubbed AWS/task runner: tests guard ordering and fixed command construction, no AWS calls.
set -euo pipefail
dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo=$(cd "$dir/../../.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/scripts" "$work/backend/blocbuy-bootstrap/src/main/resources/db/seed/load" "$work/perf/k6"
cp "$dir/v4-expiry-seed.sh" "$work/scripts/"
cp "$repo/backend/blocbuy-bootstrap/src/main/resources/db/seed/load/v4-expiry.sql" \
  "$work/backend/blocbuy-bootstrap/src/main/resources/db/seed/load/"
cp "$repo/perf/k6/v4-expiry.sql" "$work/perf/k6/"
unset V4_EXPECTED_COUNT
export REPO_ROOT=$work CLUSTER=blocbuy-swe5001 FAULT=normal
export CONFIRM_V4_SEED=2000 V4_RUN_LABEL=fixture V4_RECIPIENT_ID=0199aaaa-0000-7000-8000-000000000001
EXPECTED_IMAGE_DIGEST="sha256:$(printf 'c%.0s' {1..64})"
export EXPECTED_IMAGE_DIGEST
cat > "$work/scripts/lib.sh" <<'LIB'
set -euo pipefail
guard_account() { :; }
die() { printf '%s\n' "$*" >&2; exit 1; }
run_db_init_task() {
  [ "${#1}" -lt 8192 ] || die 'override exceeds ECS limit'
  jq -e '.containerOverrides | length == 1 and .[0].name == "db-init" and .[0].command[0:2] == ["sh","-c"]' <<< "$1" > /dev/null
  jq -r '.containerOverrides[0].command[2]' <<< "$1" > "$REPO_ROOT/task-command"
  grep -Fq 'PGDATABASE=identity psql' "$REPO_ROOT/task-command"
  grep -Fq 'PGDATABASE=commerce psql' "$REPO_ROOT/task-command"
  grep -Fq 'V4_SEED_COMPLETE' "$REPO_ROOT/task-command"
  touch "$REPO_ROOT/task-started"
  DB_INIT_EXIT_CODE=0
  [ "$FAULT" != task_failure ] || DB_INIT_EXIT_CODE=1
  DB_INIT_OUTPUT=$(jq -nc --argjson count "${V4_EXPECTED_COUNT-2000}" '{schema:1,runLabel:"fixture",teams:$count,members:$count,seededAt:"2026-10-16T00:00:00Z",expiresAt:"2026-10-16T00:01:00Z",evidenceKind:"demo-expiry-seed"} +
    (if $count == 20000 then {expectedCount:$count} else {} end)')
  [ "$FAULT" != missing_count ] || DB_INIT_OUTPUT=$(jq -c 'del(.expectedCount)' <<< "$DB_INIT_OUTPUT")
  [ "$FAULT" != wrong_count ] || DB_INIT_OUTPUT=$(jq -c '.expectedCount = 2000' <<< "$DB_INIT_OUTPUT")
  [ "$FAULT" != wrong_teams ] || DB_INIT_OUTPUT=$(jq -c '.teams = 2000' <<< "$DB_INIT_OUTPUT")
  [ "$FAULT" != bad_report ] || DB_INIT_OUTPUT='{"schema":1,"teams":0}'
  [ "$FAULT" != duplicate_report ] || DB_INIT_OUTPUT="$DB_INIT_OUTPUT
$DB_INIT_OUTPUT"
  [ "$FAULT" = missing_marker ] || DB_INIT_OUTPUT="$DB_INIT_OUTPUT
V4_SEED_COMPLETE"
}
LIB
git() {
  if [[ "$*" == *'status --porcelain'* ]]; then [ "$FAULT" != dirty ] || printf ' M local-work\n';
  else printf 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n'; fi
}
aws() {
  case "$1 $2" in
    'ecs describe-services') jq -nc --arg fault "$FAULT" '{failures:[],services:[{status:"ACTIVE",taskDefinition:"revision:1",runningCount:1,desiredCount:1,pendingCount:0,deployments:[{rolloutState:(if $fault=="rollout" then "IN_PROGRESS" else "COMPLETED" end)}]}]}' ;;
    'ecs describe-task-definition') jq -nc --arg fault "$FAULT" '[{command:(if $fault=="command" then ["override"] else null end),entryPoint:null,environment:[
      {name:"SPRING_PROFILES_ACTIVE",value:(if $fault=="no_demo" then "ec2,commerce,aws" else "ec2,commerce,aws,demo" end)},
      {name:"BLOCBUY_SCHEDULING_ENABLED",value:"true"},{name:"BLOCBUY_EVENTS_ENABLED",value:"true"},
      {name:"BLOCBUY_SCHEDULING_TEAM_EXPIRY_BATCH_SIZE",value:"500"},
      {name:"BLOCBUY_SCHEDULING_TEAM_EXPIRY_INTERVAL",value:(if $fault=="slow_expiry" then "PT1M" else "PT1S" end)},
      {name:"BLOCBUY_EVENTS_RELAY_ENABLED",value:(if $fault=="relay_off" then "false" else "true" end)}]}]' ;;
    'ecs list-tasks') if [ "$FAULT" = no_tasks ]; then printf '[]\n'; else printf '["task:1"]\n'; fi ;;
    'ecs describe-tasks')
      [ "$FAULT" != denied ] || return 1
      jq -nc --arg digest "$EXPECTED_IMAGE_DIGEST" --arg fault "$FAULT" '{failures:[],tasks:[{lastStatus:"RUNNING",taskDefinitionArn:"revision:1",containers:[{name:"commerce-svc",imageDigest:(if $fault=="digest" then "other" else $digest end)}]}]}' ;;
    *) printf 'Unexpected AWS operation: %s\n' "$*" >&2; return 1 ;;
  esac
}
export -f aws git
bash "$work/scripts/v4-expiry-seed.sh" > "$work/report.json"
jq -e '.teams == 2000 and (has("expectedCount") | not) and .commerceDigest != null and .toolingSha == "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"' "$work/report.json" > /dev/null
! grep -Fq expected_count "$work/task-command"
printf 'PASS fixed bounded seed command and source/digest receipt\n'
for fault in dirty rollout no_demo command relay_off slow_expiry no_tasks denied digest task_failure bad_report duplicate_report missing_marker; do
  rm -f "$work/task-started"
  if FAULT=$fault bash "$work/scripts/v4-expiry-seed.sh" > /dev/null 2>&1; then printf 'FAIL accepted %s\n' "$fault"; exit 1; fi
  case "$fault" in task_failure|bad_report|duplicate_report|missing_marker) ;; *) [ ! -e "$work/task-started" ] ;; esac
  printf 'PASS rejects %s\n' "$fault"
done
V4_EXPECTED_COUNT=20000 CONFIRM_V4_SEED=20000 bash "$work/scripts/v4-expiry-seed.sh" > "$work/report.json"
jq -e '.teams == 20000 and .members == 20000 and .expectedCount == 20000' "$work/report.json" > /dev/null
grep -Fq -- "-v expected_count='20000' -v confirm_count='20000'" "$work/task-command"
printf 'PASS explicitly confirmed 20000-team fixture and receipt binding\n'
for fault in dirty missing_count wrong_count wrong_teams; do
  rm -f "$work/task-started"
  if V4_EXPECTED_COUNT=20000 CONFIRM_V4_SEED=20000 FAULT=$fault bash "$work/scripts/v4-expiry-seed.sh" > /dev/null 2>&1; then exit 1; fi
  [ "$fault" != dirty ] || [ ! -e "$work/task-started" ]
  printf 'PASS larger fixture rejects %s\n' "$fault"
done
for assignment in 'CONFIRM_V4_SEED=' 'CONFIRM_V4_SEED=20000' 'V4_EXPECTED_COUNT=20000' 'V4_EXPECTED_COUNT=' 'V4_EXPECTED_COUNT=2001' 'V4_EXPECTED_COUNT=20001' 'V4_EXPECTED_COUNT=020000' 'V4_EXPECTED_COUNT=2e4' 'V4_RUN_LABEL=bad_label' 'V4_RECIPIENT_ID=not-a-uuid' 'EXPECTED_IMAGE_DIGEST=wrong'; do
  rm -f "$work/task-started"
  if env "$assignment" bash "$work/scripts/v4-expiry-seed.sh" > /dev/null 2>&1; then printf 'FAIL accepted %s\n' "$assignment"; exit 1; fi
  [ ! -e "$work/task-started" ]
  printf 'PASS rejects invalid %s\n' "${assignment%%=*}"
done
if bash "$work/scripts/v4-expiry-seed.sh" arbitrary.sql > /dev/null 2>&1; then exit 1; fi
printf 'PASS refuses arbitrary arguments/SQL\n'
