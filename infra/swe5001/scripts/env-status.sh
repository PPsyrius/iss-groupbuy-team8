#!/usr/bin/env bash
# Report what exists of the SWE5001 runtime environment (AWS plan, section 7).
#
# Usage: env-status.sh [--expect-up]
#   default      exit 0 when no runtime resource exists, 1 otherwise (after env-down.sh)
#   --expect-up  exit 0 when runtime resources exist, 1 otherwise (after env-up.sh)
#
# Environment: as env-up.sh.

# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

expect_up=false
case "${1:-}" in
  "") ;;
  --expect-up) expect_up=true ;;
  *) die "usage: env-status.sh [--expect-up]" ;;
esac

guard_account

# Deregistered task definitions stay tagged but cost nothing and cannot be deleted while
# referenced, so they never count as left over. The tagging API, and EC2's own tag index,
# keep listing deleted resources for over an hour after a destroy, so EC2, ECS and messaging ARNs
# is checked with its own describe call: a resource counts only while that call returns it.
# Not-found errors mean deleted; any other error fails closed. Other types always count.

# Runs a describe call and prints its output, or None when the resource (or its ECS
# cluster) no longer exists.
lookup() {
  local what=$1 out
  shift
  if out=$(aws "$@" 2>&1); then
    printf '%s\n' "$out"
    return
  fi
  case "$out" in
    *.NotFound* | *ClusterNotFoundException* | *'(NotFound)'* | *'(QueueDoesNotExist)'* | *AWS.SimpleQueueService.NonExistentQueue*) printf '%s\n' None ;;
    *) die "cannot inspect $what" ;;
  esac
}

# Prints how many of the EC2 resource still exist (0 or 1); unknown types print 1, so
# they are reported rather than assumed deleted.
ec2_count() {
  local type=$1 id=$2 what="runtime resource $2"
  case "$type" in
    vpc) lookup "$what" ec2 describe-vpcs --vpc-ids "$id" --query 'length(Vpcs)' --output text ;;
    subnet) lookup "$what" ec2 describe-subnets --subnet-ids "$id" --query 'length(Subnets)' --output text ;;
    route-table) lookup "$what" ec2 describe-route-tables --route-table-ids "$id" --query 'length(RouteTables)' --output text ;;
    internet-gateway) lookup "$what" ec2 describe-internet-gateways --internet-gateway-ids "$id" --query 'length(InternetGateways)' --output text ;;
    security-group) lookup "$what" ec2 describe-security-groups --group-ids "$id" --query 'length(SecurityGroups)' --output text ;;
    security-group-rule) lookup "$what" ec2 describe-security-group-rules --security-group-rule-ids "$id" --query 'length(SecurityGroupRules)' --output text ;;
    network-interface) lookup "$what" ec2 describe-network-interfaces --network-interface-ids "$id" --query 'length(NetworkInterfaces)' --output text ;;
    volume) lookup "$what" ec2 describe-volumes --volume-ids "$id" --query 'length(Volumes)' --output text ;;
    elastic-ip) lookup "$what" ec2 describe-addresses --allocation-ids "$id" --query 'length(Addresses)' --output text ;;
    launch-template) lookup "$what" ec2 describe-launch-templates --launch-template-ids "$id" --query 'length(LaunchTemplates)' --output text ;;
    vpc-endpoint) lookup "$what" ec2 describe-vpc-endpoints --vpc-endpoint-ids "$id" --query "length(VpcEndpoints[?State!='deleted' && State!='Deleted'])" --output text ;;
    natgateway) lookup "$what" ec2 describe-nat-gateways --nat-gateway-ids "$id" --query "length(NatGateways[?State!='deleted'])" --output text ;;
    *) printf '%s\n' 1 ;;
  esac
}

all_arns=$(aws resourcegroupstaggingapi get-resources \
  --tag-filters Key=Course,Values=swe5001 Key=Stack,Values=runtime \
  --query 'ResourceTagMappingList[].ResourceARN' --output text) ||
  die "cannot list tagged runtime resources"
arns=$(printf '%s\n' "$all_arns" | tr '\t' '\n' | { grep -v ':task-definition/' || true; })

remaining=""
for arn in $arns; do
  id=${arn##*/}
  case "$arn" in
    arn:aws:ec2:*:instance/*)
      # Terminated instances stay listed for a while.
      state=$(lookup "runtime instance $id" ec2 describe-instances --instance-ids "$id" \
        --query 'Reservations[0].Instances[0].State.Name' --output text) || exit 1
      case "$state" in terminated | None) continue ;; esac
      ;;
    arn:aws:ec2:*)
      resource=${arn##*:}
      count=$(ec2_count "${resource%%/*}" "$id") || exit 1
      case "$count" in 0 | None) continue ;; esac
      ;;
    arn:aws:ecs:*:cluster/*)
      cluster_status=$(lookup "ECS cluster $id" ecs describe-clusters --clusters "$arn" \
        --query 'clusters[0].status' --output text) || exit 1
      case "$cluster_status" in INACTIVE | None) continue ;; esac
      ;;
    arn:aws:ecs:*:service/*)
      cluster=${arn#*:service/}
      service_status=$(lookup "ECS service $id" ecs describe-services --cluster "${cluster%%/*}" \
        --services "$id" --query 'services[0].status' --output text) || exit 1
      case "$service_status" in INACTIVE | None) continue ;; esac
      ;;
    arn:aws:ecs:*:task/*)
      # Stopped tasks cost nothing and expire on their own.
      cluster=${arn#*:task/}
      task_status=$(lookup "ECS task $id" ecs describe-tasks --cluster "${cluster%%/*}" \
        --tasks "$id" --query 'tasks[0].lastStatus' --output text) || exit 1
      case "$task_status" in STOPPED | None) continue ;; esac
      ;;
    arn:aws:sqs:*)
      queue_name=${arn##*:}
      queue_url=$(lookup "SQS queue $queue_name" sqs get-queue-url --queue-name "$queue_name" \
        --query QueueUrl --output text) || exit 1
      case "$queue_url" in None) continue ;; esac
      ;;
    arn:aws:sns:*)
      topic=$(lookup "SNS topic $arn" sns get-topic-attributes --topic-arn "$arn" \
        --query Attributes.TopicArn --output text) || exit 1
      case "$topic" in None) continue ;; esac
      ;;
    arn:aws:cloudwatch:*:alarm:*)
      alarm_name=${arn#*:alarm:}
      count=$(lookup "CloudWatch alarm $alarm_name" cloudwatch describe-alarms --alarm-names "$alarm_name" \
        --query 'length(MetricAlarms)' --output text) || exit 1
      case "$count" in 0) continue ;; esac
      ;;
  esac
  remaining="$remaining$arn"$'\n'
done

if [ -n "$remaining" ]; then
  count=$(printf '%s' "$remaining" | grep -c .)
  log "$count runtime resource(s) tagged Stack=runtime:"
  printf '%s' "$remaining"
else
  log "no runtime resource tagged Stack=runtime"
fi

# shellcheck disable=SC2016 # JMESPath literal, not a shell expansion
active_clusters=$(aws ecs describe-clusters --clusters "$CLUSTER" \
  --query 'clusters[?status==`ACTIVE`].clusterArn' --output text) ||
  die "cannot inspect ECS cluster $CLUSTER"
if [ -n "$active_clusters" ] && [ "$active_clusters" != None ]; then
  service_arns=$(aws ecs list-services --cluster "$CLUSTER" --query 'serviceArns' --output text)
  if [ -n "$service_arns" ] && [ "$service_arns" != None ]; then
    # shellcheck disable=SC2086 # one word per service ARN
    aws ecs describe-services --cluster "$CLUSTER" --services $service_arns \
      --query 'services[].[serviceName, desiredCount, runningCount]' --output table
  fi
fi

if [ "$expect_up" = true ]; then
  [ -n "$remaining" ] || die "expected a running environment, found none"
else
  [ -z "$remaining" ] || die "runtime resources remain; run env-down.sh again or check the console"
fi
