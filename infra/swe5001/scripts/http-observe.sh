#!/usr/bin/env bash
# Bounded read-only ECS observations. Redirect stdout to a private JSONL file before load.
# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
[ "$#" -eq 1 ] || die 'usage: http-observe.sh v2|v3'
case "$1" in v2) service=monolith ;; v3) service=commerce-svc ;; *) die 'mode must be v2 or v3' ;; esac
seconds=${OBSERVE_SECONDS:-2100}
if ! [[ "$seconds" =~ ^[0-9]+$ ]] || [ "$seconds" -lt 30 ] || [ "$seconds" -gt 7200 ]; then die 'OBSERVE_SECONDS must be 30..7200'; fi
guard_account
started=$(date +%s)
while [ "$(($(date +%s) - started))" -le "$seconds" ]; do
  at=$(date -u +%FT%TZ)
  observed=$(aws ecs describe-services --cluster "$CLUSTER" --services "$service" \
    --query '{services:services[].{name:serviceName,status:status,desired:desiredCount,running:runningCount,pending:pendingCount,taskDefinition:taskDefinition,deployments:deployments[].{rolloutState:rolloutState},events:events[:5]},failures:failures}' --output json)
  jq -ce --arg at "$at" --arg name "$service" '
    if .failures == [] and (.services | length) == 1 and .services[0].name == $name and .services[0].status == "ACTIVE"
    then . + {observedAt:$at} else error("failed/missing service observation") end' <<< "$observed"
  sleep 15
done
