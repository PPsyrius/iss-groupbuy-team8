#!/usr/bin/env bash
# Read-only report and pre-seed burst guards; never uses credentials or creates AWS resources.
set -euo pipefail
dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo=$(cd "$dir/../../.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/repo/scripts" "$work/results" "$work/repo/backend/blocbuy-bootstrap/src/main/resources/db/seed/load"
cp "$dir/v4-expiry-report.sh" "$dir/v4-burst.sh" "$work/repo/scripts/"
cp "$repo/backend/blocbuy-bootstrap/src/main/resources/db/seed/load/"v4-*-report.sql \
  "$work/repo/backend/blocbuy-bootstrap/src/main/resources/db/seed/load/"
export REPO_ROOT="$work/repo" SWE5001_DIR="$work/repo" CLUSTER=blocbuy-swe5001 FAULT=normal
export V4_RUN_LABEL=fixture CONFIRM_V4_SEED=2000 V4_RESULTS_DIR="$work/results"
EXPECTED_NOTIFICATION_DIGEST="sha256:$(printf 'd%.0s' {1..64})"
export EXPECTED_NOTIFICATION_DIGEST
cat > "$work/repo/scripts/lib.sh" <<'LIB'
set -euo pipefail
guard_account() { :; }
die() { printf '%s\n' "$*" >&2; exit 1; }
tf_init() { :; }
tf() { printf '{"queues":{"notification":"https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-notification"},"dlqs":{"notification":"https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-notification-dlq"}}\n'; }
run_db_init_task() {
  [ "${#1}" -lt 8192 ] || die 'override exceeds ECS limit'
  jq -e '.containerOverrides[0].environment == [{name:"PGOPTIONS",value:"-c default_transaction_read_only=on"}]' <<< "$1" > /dev/null
  command=$(jq -r '.containerOverrides[0].command[2]' <<< "$1")
  [[ "$command" == *'PGDATABASE=commerce psql'* && "$command" == *'PGDATABASE=notification psql'* && "$command" == *V4_REPORT_COMPLETE* ]]
  DB_INIT_EXIT_CODE=0
  [ "$FAULT" != task_failure ] || DB_INIT_EXIT_CODE=1
  DB_INIT_OUTPUT='{"schema":1,"runLabel":"fixture","observedAt":"2026-10-16T00:05:00Z","teams":2000,"expiredTeams":2000,"outboxEvents":2000,"publishedEvents":2000,"failedEvents":0,"notifications":2000,"consumedEvents":2000}'
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
jq -e '.notifications == 2000 and .consumedEvents == 2000' "$work/report.json" > /dev/null
printf 'PASS bounded fixed read-only source/effect report\n'
for fault in task_failure bad_report duplicate_report missing_marker; do
  if FAULT=$fault bash "$work/repo/scripts/v4-expiry-report.sh" > /dev/null 2>&1; then exit 1; fi
  printf 'PASS report rejects %s\n' "$fault"
done
if bash "$work/repo/scripts/v4-expiry-report.sh" arbitrary.sql > /dev/null 2>&1; then exit 1; fi
printf 'PASS report rejects arbitrary SQL/arguments\n'
cat > "$work/repo/scripts/v4-expiry-seed.sh" <<'SEED'
#!/usr/bin/env bash
touch "$REPO_ROOT/seed-called"
exit 77
SEED
for fault in dirty suspended rollout consumer_off denied digest backlog dlq_backlog missing_attribute; do
  # Evidence directories are test-owned and emptied only by exact file names.
  rm -f "$work/results/scaling-before.json" "$work/results/notification-before.json" "$work/results/observations.jsonl"
  if FAULT=$fault bash "$work/repo/scripts/v4-burst.sh" > /dev/null 2>&1; then printf 'FAIL burst accepted %s\n' "$fault"; exit 1; fi
  [ ! -f "$work/repo/seed-called" ]
  printf 'PASS burst refuses %s before seeding\n' "$fault"
done
for assignment in 'CONFIRM_V4_SEED=' 'V4_MAX_SECONDS=0' 'EXPECTED_NOTIFICATION_DIGEST=wrong'; do
  if env "$assignment" bash "$work/repo/scripts/v4-burst.sh" > /dev/null 2>&1; then exit 1; fi
  printf 'PASS burst rejects invalid %s\n' "${assignment%%=*}"
done
