#!/usr/bin/env bash
# V4 driver: observe first, seed real expiring teams once, retain source/effect and queue evidence.
# Never publishes synthetic SNS/SQS messages, changes task counts, or purges/receives queue messages.
# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
[ "$#" -eq 0 ] || die 'v4-burst.sh accepts no arguments'
guard_account
umask 077
V4_EXPECTED_COUNT=${V4_EXPECTED_COUNT-2000}
case "$V4_EXPECTED_COUNT" in 2000|20000) ;; *) die 'V4_EXPECTED_COUNT must be 2000 or the explicitly approved 20000' ;; esac
export V4_EXPECTED_COUNT
[ "${CONFIRM_V4_SEED:-}" = "$V4_EXPECTED_COUNT" ] || die "CONFIRM_V4_SEED=$V4_EXPECTED_COUNT is required"
[[ "${V4_RUN_LABEL:-}" =~ ^[a-zA-Z0-9-]{1,24}$ ]] || die 'invalid V4_RUN_LABEL'
[[ "${EXPECTED_NOTIFICATION_DIGEST:-}" =~ ^sha256:[0-9a-f]{64}$ ]] || die 'reviewed EXPECTED_NOTIFICATION_DIGEST is required'
max=${V4_MAX_SECONDS:-1800}
if ! [[ "$max" =~ ^[0-9]+$ ]] || [ "$max" -lt 300 ] || [ "$max" -gt 3600 ]; then die 'V4_MAX_SECONDS must be 300..3600'; fi
min=${V4_MIN_SECONDS-300}
if ! [[ "$min" =~ ^[0-9]+$ ]] || ! [ "$min" -ge 300 ] 2>/dev/null || ! [ "$min" -le "$max" ] 2>/dev/null; then die 'V4_MIN_SECONDS must be 300..V4_MAX_SECONDS'; fi
if [ -z "${V4_RESULTS_DIR:-}" ] || [ ! -d "$V4_RESULTS_DIR" ]; then die 'create a private V4_RESULTS_DIR outside the checkout'; fi
results=$(cd "$V4_RESULTS_DIR" && pwd)
case "$results/" in "$REPO_ROOT/"*) die 'results must be outside the reviewed checkout' ;; esac
[ -z "$(ls -A "$results")" ] || die 'results directory must be empty; preserve previous evidence'
[ -z "$(git -C "$REPO_ROOT" status --porcelain)" ] || die 'use a clean reviewed checkout'

tf_init runtime
messaging=$(tf runtime output -json messaging)
queue=$(jq -er '.queues.notification' <<< "$messaging")
dlq=$(jq -er '.dlqs.notification' <<< "$messaging")
[[ "$queue" = 'https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-notification' ]] || die 'unexpected notification queue'
[[ "$dlq" = 'https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-notification-dlq' ]] || die 'unexpected notification DLQ'
scaling=$(aws application-autoscaling describe-scalable-targets --service-namespace ecs \
  --resource-ids "service/$CLUSTER/notification-svc" --output json)
jq -e '.ScalableTargets | length == 1 and .[0].MinCapacity == 1 and .[0].MaxCapacity == 4 and
  .[0].SuspendedState == {DynamicScalingInSuspended:false,DynamicScalingOutSuspended:false,ScheduledScalingSuspended:false}' \
  <<< "$scaling" > /dev/null || die 'notification scaling must be active at 1..4'
services=$(aws ecs describe-services --cluster "$CLUSTER" --services notification-svc \
  --query '{services:services[].{status:status,taskDefinition:taskDefinition,desiredCount:desiredCount,runningCount:runningCount,pendingCount:pendingCount,deployments:deployments[].{rolloutState:rolloutState}},failures:failures}' --output json)
revision=$(jq -er 'select(.failures == [] and (.services | length) == 1) | .services[0] |
  select(.status == "ACTIVE" and .desiredCount == 1 and .runningCount == 1 and .pendingCount == 0 and
    (.deployments | length) == 1 and .deployments[0].rolloutState == "COMPLETED") | .taskDefinition' \
  <<< "$services") || die 'V4 must begin with one stable notification task'
definition=$(aws ecs describe-task-definition --task-definition "$revision" \
  --query "taskDefinition.containerDefinitions[?name=='notification-svc'].{environment:environment,command:command,entryPoint:entryPoint}" --output json)
jq -e 'length == 1 and ((.[0].command // []) | length) == 0 and ((.[0].entryPoint // []) | length) == 0 and
  ([.[0].environment[] | select(.name == "BLOCBUY_EVENTS_CONSUMER_ENABLED")] | length) == 1 and
  any(.[0].environment[]; .name == "BLOCBUY_EVENTS_CONSUMER_ENABLED" and .value == "true")' \
  <<< "$definition" > /dev/null || die 'the reviewed consumer must be enabled'
tasks=$(aws ecs list-tasks --cluster "$CLUSTER" --service-name notification-svc --desired-status RUNNING --query taskArns --output text)
if [ -z "$tasks" ] || [ "$tasks" = None ]; then die 'missing notification task'; fi
# shellcheck disable=SC2086 # ECS task ARNs contain no whitespace; the live service guard requires exactly one.
observed=$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks $tasks \
  --query '{tasks:tasks[].{lastStatus:lastStatus,taskDefinitionArn:taskDefinitionArn,containers:containers[].{name:name,imageDigest:imageDigest}},failures:failures}' --output json)
jq -e --arg digest "$EXPECTED_NOTIFICATION_DIGEST" --arg revision "$revision" '
  .failures == [] and (.tasks | length) == 1 and .tasks[0].lastStatus == "RUNNING" and .tasks[0].taskDefinitionArn == $revision and
  ([.tasks[0].containers[] | select(.name == "notification-svc" and .imageDigest == $digest)] | length) == 1' \
  <<< "$observed" > /dev/null || die 'notification task digest/revision mismatch'
printf '%s\n' "$scaling" > "$results/scaling-before.json"
printf '%s\n' "$observed" > "$results/notification-before.json"

snapshot() {
  local visible dead service at
  at=$(date -u +%FT%TZ)
  visible=$(aws sqs get-queue-attributes --queue-url "$queue" \
    --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible ApproximateNumberOfMessagesDelayed --output json) || return 1
  dead=$(aws sqs get-queue-attributes --queue-url "$dlq" \
    --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible ApproximateNumberOfMessagesDelayed --output json) || return 1
  service=$(aws ecs describe-services --cluster "$CLUSTER" --services notification-svc \
    --query '{services:services[].{runningCount:runningCount,taskDefinition:taskDefinition},failures:failures}' --output json) || return 1
  jq -cne --arg at "$at" --arg revision "$revision" --argjson queue "$visible" --argjson dlq "$dead" --argjson service "$service" '
    def number: tonumber | if . >= 0 and . == floor then . else error("bad queue count") end;
    $service | select(.failures == [] and (.services | length) == 1 and .services[0].taskDefinition == $revision) |
    {observedAt:$at,notificationTasks:.services[0].runningCount,notification:{
      visible:($queue.Attributes.ApproximateNumberOfMessages | number),
      notVisible:($queue.Attributes.ApproximateNumberOfMessagesNotVisible | number),
      delayed:($queue.Attributes.ApproximateNumberOfMessagesDelayed | number),
      dlqVisible:($dlq.Attributes.ApproximateNumberOfMessages | number),
      dlqNotVisible:($dlq.Attributes.ApproximateNumberOfMessagesNotVisible | number),
      dlqDelayed:($dlq.Attributes.ApproximateNumberOfMessagesDelayed | number),oldestAgeSeconds:null}}'
}
snapshot > "$results/observations.jsonl"
jq -e '.notification | [.visible,.notVisible,.delayed,.dlqVisible,.dlqNotVisible,.dlqDelayed] | all(. == 0)' \
  "$results/observations.jsonl" > /dev/null || die 'queues must begin quiet; never purge or receive business messages'
observer=''
cleanup() { [ -z "$observer" ] || { kill "$observer" 2>/dev/null || true; wait "$observer" 2>/dev/null || true; }; }
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
(
  set -euo pipefail
  until [ -e "$results/stop-observer" ]; do
    sleep 15
    snapshot >> "$results/observations.jsonl"
  done
) 2> "$results/observer.err" &
observer=$!
started=$(date +%s)
bash "$SWE5001_DIR/scripts/v4-expiry-seed.sh" > "$results/seed.json"
# Keep at least five minutes of observations for CloudWatch/task-start lag; a quiet
# queue can precede later expiry batches, so the final database report is mandatory.
reports=0
next_report=0
producer_complete_at=0
collection_complete=false
quiet_samples() {
  tail -n 3 "$results/observations.jsonl" | jq -se --argjson after "$1" '
    length == 3 and all(.[]; .notification.visible == 0 and .notification.notVisible == 0 and .notification.delayed == 0) and
    (if $after == 0 then true else all(.[]; (.observedAt | fromdateiso8601) >= $after) and
      ((.[-1].observedAt | fromdateiso8601) - (.[0].observedAt | fromdateiso8601) >= 30) end)' > /dev/null
}
source_effects_complete() {
  jq -e --argjson expected "$V4_EXPECTED_COUNT" '.expectedCount == $expected and .failedEvents == 0 and
    all([.teams,.expiredTeams,.outboxEvents,.publishedEvents,.notifications,.consumedEvents][]; . == $expected)' "$1" > /dev/null
}
while [ "$(($(date +%s) - started))" -lt "$max" ]; do
  kill -0 "$observer" 2>/dev/null || die 'observer failed; retain diagnostics, do not reseed'
  [ ! -s "$results/observer.err" ] || die 'observer errors invalidate coverage'
  elapsed=$(($(date +%s) - started))
  if [ "$elapsed" -ge "$min" ] && quiet_samples 0; then
    # Preserve the historical 2,000-team collection path exactly. For the revised
    # arrival-paced fixture, quiet queues alone cannot establish producer completion.
    if [ "$V4_EXPECTED_COUNT" = 2000 ]; then break; fi
    if [ "$producer_complete_at" -eq 0 ] && [ "$elapsed" -ge "$next_report" ]; then
      reports=$((reports + 1))
      check="$results/database-report-check-$reports.json"
      # Synchronous, at most one operator DB task; retain every incomplete receipt.
      bash "$SWE5001_DIR/scripts/v4-expiry-report.sh" > "$check"
      if source_effects_complete "$check"; then producer_complete_at=$(date +%s); fi
      next_report=$(($(date +%s) - started + 60))
    fi
    if [ "$producer_complete_at" -gt 0 ] && quiet_samples "$producer_complete_at"; then
      collection_complete=true
      break
    fi
  fi
  sleep 5
done
bash "$SWE5001_DIR/scripts/v4-expiry-report.sh" > "$results/database-report.json"
# A final snapshot brackets the database report, instead of assuming the last poll did.
touch "$results/stop-observer"
wait "$observer"
observer=''
sleep 1 # Keep strictly increasing UTC-second timestamps after the observer's last poll.
snapshot >> "$results/observations.jsonl"
[ ! -s "$results/observer.err" ] || die 'observer errors invalidate coverage'
if [ "$V4_EXPECTED_COUNT" = 20000 ]; then
  [ "$collection_complete" = true ] || die 'revised V4 reached its maximum without complete source/effects followed by a final quiet interval; retain evidence, never reseed'
  source_effects_complete "$results/database-report.json" || die 'final revised V4 source/effect counts do not match; retain evidence, never reseed'
fi
log "V4 evidence saved in $results; analyze before claiming drain/scale-out acceptance"
log 'Oldest-message age is a CloudWatch metric, not an SQS queue attribute; capture that graph separately.'
