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
    'logs get-log-events') printf '%s\n' "$LOG_TEXT" ;;
    *) return 1 ;;
  esac
}
terraform() {
  case "$*" in
    *'output -raw db_init_task_definition'*) printf '%s\n' 'arn:aws:ecs:ap-southeast-1:402178232802:task-definition/blocbuy-swe5001-db-init:1' ;;
    *'output -json db_init_network'*) printf '%s\n' '{"subnets":["subnet-a"],"security_groups":["sg-a"]}' ;;
    *) ;;
  esac
}
sleep() { :; }
export -f aws terraform sleep

task='arn:aws:ecs:ap-southeast-1:402178232802:task/blocbuy-swe5001/0123abcd'
tab=$(printf '\t')

# check <name> <expect: ok|fail> <output substring> <script> [args...]; scenario in env.
check() {
  local name=$1 expect=$2 needle=$3 output status=0
  shift 3
  rm -f "$work/overrides.json"
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
  check 'report notes a late log' ok 'no report in the log yet' session-report.sh
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
