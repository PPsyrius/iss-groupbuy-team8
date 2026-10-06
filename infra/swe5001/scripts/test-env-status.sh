#!/usr/bin/env bash
# Exercise teardown verification without AWS: API errors must never mean "empty".
set -euo pipefail

status_script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env-status.sh"
# check() overrides this for each child; declare it here so static analysis sees it.
SCENARIO=${SCENARIO:-}

aws() {
  case "$1 $2" in
    'sts get-caller-identity') printf '%s\n' 402178232802 ;;
    'resourcegroupstaggingapi get-resources')
      case "$SCENARIO" in
        tag_error) return 41 ;;
        instance_error | terminated | instance_gone | running) printf '%s\n' 'arn:aws:ec2:ap-southeast-1:402178232802:instance/i-demo' ;;
        ec2_error | ec2_deleted | ec2_live) printf '%s\n' 'arn:aws:ec2:ap-southeast-1:402178232802:security-group/sg-demo' ;;
        cluster_error | cluster_inactive | cluster_active) printf '%s\n' 'arn:aws:ecs:ap-southeast-1:402178232802:cluster/blocbuy-swe5001' ;;
        task_only) printf '%s\n' 'arn:aws:ecs:ap-southeast-1:402178232802:task-definition/demo:1' ;;
      esac
      ;;
    'ec2 describe-instances')
      case "$SCENARIO" in
        instance_error) return 42 ;;
        terminated) printf '%s\n' terminated ;;
        instance_gone) printf '%s\n' None ;;
        *) printf '%s\n' running ;;
      esac
      ;;
    'ec2 describe-tags')
      case "$SCENARIO" in
        ec2_error) return 45 ;;
        ec2_deleted) printf '%s\n' 0 ;;
        *) printf '%s\n' 2 ;;
      esac
      ;;
    'ecs describe-clusters')
      # Status lookup of a tagged cluster ARN; the later ACTIVE filter prints nothing.
      if [[ "$*" == *'clusters[0].status'* ]]; then
        case "$SCENARIO" in
          cluster_error) return 46 ;;
          cluster_inactive) printf '%s\n' INACTIVE ;;
          *) printf '%s\n' ACTIVE ;;
        esac
      elif [ "$SCENARIO" = ecs_error ]; then
        return 43
      fi
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
check instance_gone success 'no runtime resource tagged Stack=runtime'
check running failure 'runtime resources remain'
check ec2_error failure 'cannot inspect runtime resource sg-demo'
check ec2_deleted success 'no runtime resource tagged Stack=runtime'
check ec2_live failure 'runtime resources remain'
check cluster_error failure 'cannot inspect ECS cluster blocbuy-swe5001'
check cluster_inactive success 'no runtime resource tagged Stack=runtime'
check cluster_active failure 'runtime resources remain'
