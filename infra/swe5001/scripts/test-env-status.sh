#!/usr/bin/env bash
# Exercise teardown verification without AWS: API errors must never mean "empty".
set -euo pipefail

status_script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env-status.sh"
# check() overrides this for each child; declare it here so static analysis sees it.
SCENARIO=${SCENARIO:-}

aws() {
  local arn_prefix='arn:aws:ec2:ap-southeast-1:402178232802' ecs_prefix='arn:aws:ecs:ap-southeast-1:402178232802'
  case "$1 $2" in
    'sts get-caller-identity') printf '%s\n' 402178232802 ;;
    'resourcegroupstaggingapi get-resources')
      case "$SCENARIO" in
        tag_error) return 41 ;;
        instance_*) printf '%s\n' "$arn_prefix:instance/i-demo" ;;
        ec2_*) printf '%s\n' "$arn_prefix:security-group/sg-demo" ;;
        unknown_type) printf '%s\n' "$arn_prefix:transit-gateway/tgw-demo" ;;
        cluster_*) printf '%s\n' "$ecs_prefix:cluster/blocbuy-swe5001" ;;
        service_*) printf '%s\n' "$ecs_prefix:service/blocbuy-swe5001/monolith" ;;
        task_*) printf '%s\n' "$ecs_prefix:task/blocbuy-swe5001/0123abcd" ;;
        taskdef_only) printf '%s\n' "$ecs_prefix:task-definition/demo:1" ;;
      esac
      ;;
    'ec2 describe-instances')
      case "$SCENARIO" in
        instance_error) denied DescribeInstances ;;
        instance_notfound) not_found InvalidInstanceID.NotFound DescribeInstances ;;
        instance_terminated) printf '%s\n' terminated ;;
        instance_gone) printf '%s\n' None ;;
        *) printf '%s\n' running ;;
      esac
      ;;
    'ec2 describe-security-groups')
      case "$SCENARIO" in
        ec2_error) denied DescribeSecurityGroups ;;
        ec2_notfound) not_found InvalidGroup.NotFound DescribeSecurityGroups ;;
        ec2_empty) printf '%s\n' 0 ;;
        *) printf '%s\n' 1 ;;
      esac
      ;;
    'ecs describe-clusters')
      # Status lookup of a tagged cluster ARN; the later ACTIVE filter prints nothing.
      if [[ "$*" == *'clusters[0].status'* ]]; then
        case "$SCENARIO" in
          cluster_error) denied DescribeClusters ;;
          cluster_inactive) printf '%s\n' INACTIVE ;;
          *) printf '%s\n' ACTIVE ;;
        esac
      elif [ "$SCENARIO" = ecs_error ]; then
        return 43
      fi
      ;;
    'ecs describe-services')
      case "$SCENARIO" in
        service_error) denied DescribeServices ;;
        service_no_cluster) not_found ClusterNotFoundException DescribeServices ;;
        service_inactive) printf '%s\n' INACTIVE ;;
        *) printf '%s\n' ACTIVE ;;
      esac
      ;;
    'ecs describe-tasks')
      case "$SCENARIO" in
        task_error) denied DescribeTasks ;;
        task_expired) printf '%s\n' None ;;
        task_stopped) printf '%s\n' STOPPED ;;
        *) printf '%s\n' RUNNING ;;
      esac
      ;;
    *) printf 'unexpected AWS call: %s\n' "$*" >&2; return 44 ;;
  esac
}
# The AWS CLI prints the error code to stderr and exits 254.
denied() {
  printf 'An error occurred (AccessDenied) when calling the %s operation\n' "$1" >&2
  return 254
}
not_found() {
  printf 'An error occurred (%s) when calling the %s operation: not found\n' "$1" "$2" >&2
  return 254
}
terraform() { :; }
jq() { :; }
curl() { :; }
export -f aws denied not_found terraform jq curl

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
check ecs_error failure 'cannot inspect ECS cluster'
check empty success 'no runtime resource tagged Stack=runtime'
check taskdef_only success 'no runtime resource tagged Stack=runtime'
check instance_error failure 'cannot inspect runtime instance i-demo'
check instance_notfound success 'no runtime resource tagged Stack=runtime'
check instance_terminated success 'no runtime resource tagged Stack=runtime'
check instance_gone success 'no runtime resource tagged Stack=runtime'
check instance_running failure 'runtime resources remain'
check ec2_error failure 'cannot inspect runtime resource sg-demo'
check ec2_notfound success 'no runtime resource tagged Stack=runtime'
check ec2_empty success 'no runtime resource tagged Stack=runtime'
check ec2_live failure 'runtime resources remain'
check unknown_type failure 'runtime resources remain'
check cluster_error failure 'cannot inspect ECS cluster blocbuy-swe5001'
check cluster_inactive success 'no runtime resource tagged Stack=runtime'
check cluster_active failure 'runtime resources remain'
check service_error failure 'cannot inspect ECS service monolith'
check service_no_cluster success 'no runtime resource tagged Stack=runtime'
check service_inactive success 'no runtime resource tagged Stack=runtime'
check service_active failure 'runtime resources remain'
check task_error failure 'cannot inspect ECS task 0123abcd'
check task_expired success 'no runtime resource tagged Stack=runtime'
check task_stopped success 'no runtime resource tagged Stack=runtime'
check task_running failure 'runtime resources remain'
