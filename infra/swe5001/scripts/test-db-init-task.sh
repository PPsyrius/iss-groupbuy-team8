#!/usr/bin/env bash
# Exercise the one-off db-init scripts without AWS (run_db_init_task in lib.sh):
# session-report.sh must stay read-only and fixed, grant-admin.sh must keep its exit codes,
# and both must fail closed when the task does not start or fails.
set -euo pipefail

dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export WORK=$work
# check() overrides these for each child; declare them here so static analysis sees them.
TASK_ARN=${TASK_ARN:-}
EXIT_CODE=${EXIT_CODE:-}
LOG_TEXT=${LOG_TEXT:-}
export LOG_LATE_TEXT=${LOG_LATE_TEXT:-}

# Stub only the AWS operations used by the runner; retain overrides for assertions.
aws() {
  case "$1 $2" in
    'sts get-caller-identity') printf '%s\n' 402178232802 ;;
    'ecs run-task')
      while [ "$#" -gt 0 ]; do
        if [ "$1" = --overrides ]; then printf '%s' "$2" > "$WORK/overrides.json"; fi
        shift
      done
      printf '%s\n' "$TASK_ARN"
      ;;
    'ecs wait') ;;
    'ecs describe-tasks') printf '%s\n' "$EXIT_CODE" ;;
    'logs get-log-events')
      if [ -f "$WORK/log-polled" ] && [ -n "$LOG_LATE_TEXT" ]; then
        printf '%s\n' "$LOG_LATE_TEXT"
      else
        touch "$WORK/log-polled"
        printf '%s\n' "$LOG_TEXT"
      fi
      ;;
    *) return 1 ;;
  esac
}
# Supply the existing db-init task and private network without reading Terraform state.
terraform() {
  case "$*" in
    *'output -raw db_init_task_definition'*) printf '%s\n' 'arn:aws:ecs:ap-southeast-1:402178232802:task-definition/blocbuy-swe5001-db-init:1' ;;
    *'output -json db_init_network'*) printf '%s\n' '{"subnets":["subnet-a"],"security_groups":["sg-a"]}' ;;
    *) ;;
  esac
}
# Keep bounded CloudWatch retry tests offline and instantaneous.
sleep() { :; }
export -f aws terraform sleep

task='arn:aws:ecs:ap-southeast-1:402178232802:task/blocbuy-swe5001/0123abcd'
tab=$(printf '\t')

# check <name> <expect: ok|fail> <output substring> <script> [args...]; scenario in env.
check() {
  local name=$1 expect=$2 needle=$3 output status=0
  shift 3
  rm -f "$work/overrides.json" "$work/log-polled"
  output=$(AWS_REGION=ap-southeast-1 SWE5001_TF_STATE_BUCKET=test bash "$dir/$1" "${@:2}" 2>&1) || status=$?
  if { [ "$expect" = ok ] && [ "$status" -ne 0 ]; } || { [ "$expect" = fail ] && [ "$status" -eq 0 ]; }; then
    printf 'FAIL %s: exit %s\n%s\n' "$name" "$status" "$output" >&2
    exit 1
  fi
  [[ "$output" == *"$needle"* ]] || {
    printf 'FAIL %s: no "%s" in\n%s\n' "$name" "$needle" "$output" >&2
    exit 1
  }
  printf 'PASS %s\n' "$name"
}

# session-report.sh
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT="lock_key | owner${tab}team-expiry | ip-10-0-1-5/0199${tab}session-report: done" \
  check 'report prints the task output' ok 'team-expiry | ip-10-0-1-5/0199' session-report.sh
overrides=$(cat "$work/overrides.json")
[ "${#overrides}" -lt 8192 ] || {
  printf 'FAIL report overrides are %s bytes\n' "${#overrides}" >&2
  exit 1
}
command=$(jq -r '.containerOverrides[0].command[2]' <<< "$overrides")
for database in identity notification commerce; do
  [[ "$command" == *"\\connect $database"* ]] || {
    printf 'FAIL report does not read database %s\n' "$database" >&2
    exit 1
  }
done
jq -e '.containerOverrides[0].environment[] | select(.name == "PGOPTIONS" and .value == "-c default_transaction_read_only=on")' \
  <<< "$overrides" > /dev/null || {
  printf 'FAIL report connections are not read-only\n' >&2
  exit 1
}
if grep -Eiq '\b(insert|update|delete|truncate|drop|alter|create|grant|revoke)\b' <<< "$command"; then
  printf 'FAIL report SQL writes:\n%s\n' "$command" >&2
  exit 1
fi
printf 'PASS report reads three databases, read-only, under the 8 KiB override limit\n'

TASK_ARN=$task EXIT_CODE=0 LOG_TEXT='' \
  check 'report fails on an empty log' fail 'report output is incomplete' session-report.sh
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT='None' \
  check 'report fails on an undelivered log' fail 'report output is incomplete' session-report.sh
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT='identity | 0 | 0' \
  check 'report fails on partial output' fail 'report output is incomplete' session-report.sh
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT='identity | 0 | 0' LOG_LATE_TEXT="commerce | 10 | 1${tab}session-report: done" \
  check 'report waits for the completion marker' ok 'commerce | 10 | 1' session-report.sh
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT='not session-report: done' \
  check 'report rejects an embedded marker' fail 'report output is incomplete' session-report.sh
TASK_ARN=$task EXIT_CODE=1 LOG_TEXT='session-report: done' \
  check 'completion marker cannot override task failure' fail "report task exited with '1'" session-report.sh
TASK_ARN=$task EXIT_CODE=1 LOG_TEXT='ERROR:  relation "domain.biz_product" does not exist' \
  check 'report fails when the task fails' fail "report task exited with '1'" session-report.sh
TASK_ARN=None EXIT_CODE=0 LOG_TEXT='' \
  check 'report fails when the task does not start' fail 'db-init task did not start' session-report.sh
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT='' \
  check 'report takes no arguments' fail 'usage: session-report.sh' session-report.sh "'; drop table x; --"

# grant-admin.sh on the shared helper
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT="BEGIN${tab}granted | 1${tab}COMMIT" \
  check 'grant prints the result' ok 'granted | 1' grant-admin.sh admin@example.com
jq -e '.containerOverrides[0].environment[] | select(.name == "PGDATABASE" and .value == "identity")' \
  "$work/overrides.json" > /dev/null || {
  printf 'FAIL grant does not target the identity database\n' >&2
  exit 1
}
printf 'PASS grant targets the identity database\n'
TASK_ARN=$task EXIT_CODE=3 LOG_TEXT="No single active account with that email; sign in once first.${tab}ROLLBACK" \
  check 'grant explains a missing account' fail 'sign in once through the session first' grant-admin.sh admin@example.com
TASK_ARN=$task EXIT_CODE=2 LOG_TEXT='ERROR:  boom' \
  check 'grant fails when the task fails' fail "grant task exited with '2'" grant-admin.sh admin@example.com
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT='' \
  check 'grant rejects a non-email' fail 'not an email address' grant-admin.sh "x' or '1'='1"

# connection-budget.sh reads fixed settings through the same private reporting task.
budget='{"maxConnections":150,"reservedConnections":3}'
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT="$budget${tab}connection-budget: done" \
  check 'budget prints observed JSON only' ok "$budget" connection-budget.sh
command=$(jq -r '.containerOverrides[0].command[2]' "$work/overrides.json")
[[ "$command" == *"current_setting('max_connections')"* && "$command" == *"current_setting('reserved_connections')"* ]]
if grep -Eiq '\b(insert|update|delete|truncate|drop|alter|create|grant|revoke)\b' <<< "$command"; then
  printf 'FAIL budget SQL must be read-only\n' >&2
  exit 1
fi
jq -e '.containerOverrides[0].environment | any(.name == "PGOPTIONS" and .value == "-c default_transaction_read_only=on")' \
  "$work/overrides.json" > /dev/null
printf 'PASS budget uses fixed read-only settings SQL\n'
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT="$budget" \
  check 'budget rejects partial output' fail 'output is incomplete' connection-budget.sh
TASK_ARN=$task EXIT_CODE=1 LOG_TEXT="$budget${tab}connection-budget: done" \
  check 'budget completion cannot hide task failure' fail 'task failed' connection-budget.sh
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT='connection-budget: done' \
  check 'budget requires data as well as completion' fail 'invalid connection-budget report' connection-budget.sh
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT="{}${tab}connection-budget: done" \
  check 'budget rejects missing settings' fail 'invalid connection-budget report' connection-budget.sh
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT="$budget${tab}$budget${tab}connection-budget: done" \
  check 'budget rejects duplicate reports' fail 'invalid connection-budget report' connection-budget.sh
TASK_ARN=$task EXIT_CODE=0 LOG_TEXT='' \
  check 'budget accepts no SQL arguments' fail 'usage: connection-budget.sh' connection-budget.sh 'injected SQL'
