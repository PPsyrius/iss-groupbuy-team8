#!/usr/bin/env bash
# Exercise teardown verification without AWS: API errors must never mean "empty".
set -euo pipefail

status_script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env-status.sh"

aws() {
  case "$1 $2" in
    'sts get-caller-identity') printf '%s\n' 402178232802 ;;
    'resourcegroupstaggingapi get-resources')
      case "$SCENARIO" in
        tag_error) return 41 ;;
        instance_error | terminated | running) printf '%s\n' 'arn:aws:ec2:ap-southeast-1:402178232802:instance/i-demo' ;;
        task_only) printf '%s\n' 'arn:aws:ecs:ap-southeast-1:402178232802:task-definition/demo:1' ;;
      esac
      ;;
    'ec2 describe-instances')
      if [ "$SCENARIO" = instance_error ]; then return 42; fi
      if [ "$SCENARIO" = terminated ]; then printf '%s\n' terminated; else printf '%s\n' running; fi
      ;;
    'ecs describe-clusters')
      if [ "$SCENARIO" = ecs_error ]; then return 43; fi
      ;;
    *) printf 'unexpected AWS call: %s\n' "$*" >&2; return 44 ;;
  esac
}
terraform() { :; }
jq() { :; }
curl() { :; }
export -f aws terraform jq curl

check() {
  local scenario=$1 expected_result=$2 expected_text=$3 output result
  if output=$(AWS_REGION=ap-southeast-1 SWE5001_TF_STATE_BUCKET=test SCENARIO="$scenario" bash "$status_script" 2>&1); then
    result=success
  else
    result=failure
  fi
  if [ "$result" != "$expected_result" ] || [[ "$output" != *"$expected_text"* ]]; then
    printf 'FAIL %s: expected %s containing %s, got %s: %s\n' "$scenario" "$expected_result" "$expected_text" "$result" "$output" >&2
    exit 1
  fi
  printf 'PASS %s\n' "$scenario"
}

check tag_error failure 'cannot list tagged runtime resources'
check instance_error failure 'cannot inspect runtime instance i-demo'
check ecs_error failure 'cannot inspect ECS cluster'
check empty success 'no runtime resource tagged Stack=runtime'
check task_only success 'no runtime resource tagged Stack=runtime'
check terminated success 'no runtime resource tagged Stack=runtime'
check running failure 'runtime resources remain'
