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
# referenced; terminated instances stay listed for a while. Neither counts as left over.
all_arns=$(aws resourcegroupstaggingapi get-resources \
  --tag-filters Key=Course,Values=swe5001 Key=Stack,Values=runtime \
  --query 'ResourceTagMappingList[].ResourceARN' --output text) ||
  die "cannot list tagged runtime resources"
arns=$(printf '%s\n' "$all_arns" | tr '\t' '\n' | { grep -v ':task-definition/' || true; })

remaining=""
for arn in $arns; do
  case "$arn" in
    arn:aws:ec2:*:instance/*)
      state=$(aws ec2 describe-instances --instance-ids "${arn##*/}" \
        --query 'Reservations[0].Instances[0].State.Name' --output text) ||
        die "cannot inspect runtime instance ${arn##*/}"
      [ "$state" = terminated ] && continue
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
