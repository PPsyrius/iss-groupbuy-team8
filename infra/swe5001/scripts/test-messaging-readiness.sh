#!/usr/bin/env bash
# Permanent offline regressions: mock AWS/Terraform, real jq and actual shell scripts.
# No AWS credentials, network or real sleeps; API changes persist across subshells.
# shellcheck disable=SC2016 # jq expressions deliberately refer to jq variables, not shell variables
set -euo pipefail
# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
# shellcheck source=./service-control.sh source-path=SCRIPTDIR
source "$SWE5001_DIR/scripts/service-control.sh"
# shellcheck source=./messaging-check.sh source-path=SCRIPTDIR
source "$SWE5001_DIR/scripts/messaging-check.sh"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export TMPDIR="$work" CLOUD_STATE="$work/cloud.json" CLOUD_CALLS="$work/calls"
export AWS_REGION=ap-southeast-1 SWE5001_TF_STATE_BUCKET=test SWE5001_TOPOLOGY=distributed
export TF_VAR_alert_email_addresses='["team@example.invalid"]'
export SCENARIO=normal
PROBE_RECEIPTS="$work/held-messages.json"

arg_value() {
  local key=$1
  shift
  while [ "$#" -gt 0 ]; do
    if [ "$1" = "$key" ]; then printf '%s\n' "$2"; return; fi
    shift
  done
}
save_state() { jq "$@" "$CLOUD_STATE" > "$CLOUD_STATE.next" && mv "$CLOUD_STATE.next" "$CLOUD_STATE"; }
reset() {
  SCENARIO=normal
  : > "$CLOUD_CALLS"
  printf '[]\n' > "$PROBE_RECEIPTS"
  cat > "$CLOUD_STATE" <<'JSON'
{"active":true,"relay":false,"services":{"commerce-svc":{"desired":4,"running":4,"scaling":{"MinCapacity":2,"MaxCapacity":6,"SuspendedState":{"DynamicScalingInSuspended":false,"DynamicScalingOutSuspended":false,"ScheduledScalingSuspended":false}}},"notification-svc":{"desired":2,"running":2,"scaling":{"MinCapacity":1,"MaxCapacity":4,"SuspendedState":{"DynamicScalingInSuspended":true,"DynamicScalingOutSuspended":false,"ScheduledScalingSuspended":true}}},"identity-svc":{"desired":1,"running":1,"scaling":null}},"queues":{"notification":[],"audit":[]}}
JSON
}

aws() {
  printf 'aws %s\n' "$*" >> "$CLOUD_CALLS"
  local service query resource queue body attrs id message enabled
  service=$(arg_value --service "$@")
  [ -n "$service" ] || service=$(arg_value --services "$@")
  query=$(arg_value --query "$@")
  case "$1 $2" in
    'sts get-caller-identity') printf '402178232802\n' ;;
    'ssm get-parameter') printf 'sha256:%064d\n' 0 ;;
    'ecs describe-clusters')
      if [ "$(jq .active "$CLOUD_STATE")" = true ]; then printf '["arn:aws:ecs:ap-southeast-1:402178232802:cluster/blocbuy-swe5001"]\n'; else printf '[]\n'; fi ;;
    'ecs describe-services')
      [ "$SCENARIO" != describe_error ] || return 1
      case "$query" in
        '{services:services,failures:failures}')
          jq --arg s "$service" '{services:[{status:"ACTIVE",desiredCount:.services[$s].desired}],failures:[]}' "$CLOUD_STATE" ;;
        'services[0].desiredCount') jq -r --arg s "$service" '.services[$s].desired' "$CLOUD_STATE" ;;
        *) jq --arg s "$service" '.services[$s] | {desired,running}' "$CLOUD_STATE" ;;
      esac ;;
    'ecs update-service')
      [ "$SCENARIO" != update_error ] || return 1
      id=$(arg_value --desired-count "$@")
      save_state --arg s "$service" --argjson d "$id" '.services[$s].desired=$d | .services[$s].running=$d' ;;
    'ecs wait') [ "$SCENARIO" != wait_error ] ;;
    'ecs run-task') printf 'arn:aws:ecs:ap-southeast-1:402178232802:task/test/init\n' ;;
    'ecs describe-tasks') if [ "$SCENARIO" = db_init_error ]; then printf '1\n'; else printf '0\n'; fi ;;
    'application-autoscaling describe-scalable-targets')
      [ "$SCENARIO" != target_error ] || return 1
      resource=$(arg_value --resource-ids "$@")
      service=${resource##*/}
      jq --arg s "$service" '.services[$s].scaling' "$CLOUD_STATE" ;;
    'application-autoscaling register-scalable-target')
      resource=$(arg_value --resource-id "$@")
      service=${resource##*/}
      attrs=$(arg_value --suspended-state "$@")
      save_state --arg s "$service" --argjson flags "$attrs" \
        --argjson min "$(arg_value --min-capacity "$@")" --argjson max "$(arg_value --max-capacity "$@")" \
        '.services[$s].scaling={MinCapacity:$min,MaxCapacity:$max,SuspendedState:$flags}' ;;
    'sns get-subscription-attributes')
      [ "$SCENARIO" != alert_error ] || return 1
      if [ "$SCENARIO" = pending_email ]; then enabled=true; else enabled=false; fi
      jq -nc --arg pending "$enabled" '{Attributes:{Protocol:"email",PendingConfirmation:$pending}}' ;;
    'sqs get-queue-attributes')
      # CloudWatch's visible-message metric is NOT a valid SQS API attribute.
      if [[ "$*" == *ApproximateNumberOfMessagesVisible* ]]; then
        printf 'InvalidAttributeName: Unknown Attribute ApproximateNumberOfMessagesVisible\n' >&2
        return 1
      fi
      if [ "$SCENARIO" = backlog ]; then enabled=1; else enabled=0; fi
      jq -nc --arg n "$enabled" '{Attributes:{ApproximateNumberOfMessages:$n,ApproximateNumberOfMessagesNotVisible:"0",ApproximateNumberOfMessagesDelayed:"0"}}' ;;
    'sns publish')
      [ "$SCENARIO" != publish_error ] || return 1
      body=$(arg_value --message "$@")
      attrs=$(arg_value --message-attributes "$@")
      id=$(jq -r .eventId <<< "$body")
      message=$(jq -nc --arg body "$body" --arg handle "$id" --argjson attrs "$attrs" '{Body:$body,ReceiptHandle:$handle,MessageAttributes:$attrs}')
      save_state --argjson message "$message" '.queues.notification += [$message]'
      if [ "$(jq -r .eventType <<< "$body")" = TeamSucceeded ] || [ "$SCENARIO" = bad_filter ]; then
        save_state --argjson message "$message" '.queues.audit += [$message]'
      fi
      printf 'test-sns-id\n' ;;
    'sqs receive-message')
      [ "$SCENARIO" != receive_error ] || return 1
      if [ "$SCENARIO" = malformed_response ]; then printf '{"Messages":[{}]}\n'; return; fi
      resource=$(arg_value --queue-url "$@")
      queue=${resource##*/}
      queue=${queue#blocbuy-swe5001-}
      if [ "$SCENARIO" = empty_stdout ] && [ "$(jq --arg q "$queue" '[.queues[$q][] | select(.hidden != true)] | length' "$CLOUD_STATE")" = 0 ]; then
        return 0
      fi
      if [ "$SCENARIO" = missing_delivery ]; then
        printf '{}\n'
      else
        jq --arg q "$queue" '{Messages:[.queues[$q][] | select(.hidden != true)][:10]}' "$CLOUD_STATE"
        save_state --arg q "$queue" '.queues[$q] |= map(.hidden=true)'
      fi ;;
    'sqs delete-message')
      [ "$SCENARIO" != delete_error ] || return 1
      resource=$(arg_value --queue-url "$@")
      queue=${resource##*/}
      queue=${queue#blocbuy-swe5001-}
      id=$(arg_value --receipt-handle "$@")
      save_state --arg q "$queue" --arg id "$id" '.queues[$q] |= map(select(.ReceiptHandle != $id))' ;;
    'sqs change-message-visibility')
      resource=$(arg_value --queue-url "$@")
      queue=${resource##*/}
      queue=${queue#blocbuy-swe5001-}
      id=$(arg_value --receipt-handle "$@")
      save_state --arg q "$queue" --arg id "$id" '.queues[$q] |= map(if .ReceiptHandle == $id then del(.hidden) else . end)' ;;
    *) printf 'unexpected AWS call: %s\n' "$*" >&2; return 1 ;;
  esac
}
sleep() { printf 'sleep %s\n' "$*" >> "$CLOUD_CALLS"; }
openssl() {
  [ "$SCENARIO" != openssl_error ] || return 1
  command openssl "$@"
}
terraform() {
  printf 'terraform %s\n' "$*" >> "$CLOUD_CALLS"
  case "$2" in
    init) : ;;
    output)
      case "${*: -1}" in
        url) printf 'https://example.invalid\n' ;;
        messaging)
          jq -n --argjson relay "$(jq .relay "$CLOUD_STATE")" \
            '{topic_arn:"arn:aws:sns:ap-southeast-1:402178232802:blocbuy-swe5001-domain-events",queues:{notification:"https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-notification",audit:"https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-audit"},alert_subscription_arns:["arn:aws:sns:ap-southeast-1:402178232802:blocbuy-swe5001-alerts:test"],relay_enabled:$relay}' ;;
        db_init_task_definition) printf 'init\n' ;;
        db_init_network) printf '{"subnets":[],"security_groups":[]}\n' ;;
        alb_dns_name) printf 'example-alb.invalid\n' ;;
        *) return 1 ;;
      esac ;;
    apply)
      if [[ "$1" == *'/edge' ]]; then
        [ "$SCENARIO" != edge_error ] || { printf 'Error: test failure\n' >&2; return 1; }
        printf 'Apply complete\n'
      elif [[ "$*" == *services_enabled=false* ]]; then
        save_state '.active=true | .relay=false | .services["notification-svc"].scaling={MinCapacity:0,MaxCapacity:4,SuspendedState:{DynamicScalingInSuspended:true,DynamicScalingOutSuspended:true,ScheduledScalingSuspended:true}}'
      else
        if [ "$SWE5001_TOPOLOGY" = distributed ]; then
          [[ "$*" == *messaging_verified=true* ]] || return 1
          grep -q 'sns publish' "$CLOUD_CALLS" || return 1
        fi
        save_state '.relay=true | .services["notification-svc"].scaling={MinCapacity:1,MaxCapacity:4,SuspendedState:{DynamicScalingInSuspended:false,DynamicScalingOutSuspended:false,ScheduledScalingSuspended:false}}'
      fi ;;
    *) return 1 ;;
  esac
}
curl() {
  local output headers url status=200 body
  output=$(arg_value --output "$@")
  headers=$(arg_value --dump-header "$@")
  url=${*: -1}
  case "$url" in
    *'/health') body='{"status":"UP"}' ;;
    *'/products?'*) body='{"code":"COMMON_200_000","data":{"items":[{}]}}' ;;
    *'/auth/me') body='{"code":"IDENTITY_401_001"}'; status=401 ;;
    *) body='<html>SPA</html>' ;;
  esac
  printf '%s' "$body" > "$output"
  printf 'X-Trace-Id: test\nContent-Type: text/html\n' > "$headers"
  printf '%s' "$status"
}
export -f aws terraform curl sleep openssl arg_value save_state

pass() { printf 'PASS %s\n' "$1"; }
fails() { if "$@" > "$work/output" 2>&1; then cat "$work/output"; return 1; fi; }
targets='{"topic_arn":"arn:aws:sns:ap-southeast-1:402178232802:blocbuy-swe5001-domain-events","queues":{"notification":"https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-notification","audit":"https://sqs.ap-southeast-1.amazonaws.com/402178232802/blocbuy-swe5001-audit"},"alert_subscription_arns":["arn:aws:sns:ap-southeast-1:402178232802:blocbuy-swe5001-alerts:test"]}'

reset
cp "$CLOUD_STATE" "$work/original"
pause_services "$work/snapshot" commerce-svc notification-svc
jq -e '.services["commerce-svc"].desired == 0 and .services["notification-svc"].desired == 0 and .services["commerce-svc"].scaling.SuspendedState.ScheduledScalingSuspended' "$CLOUD_STATE" > /dev/null
restore_services "$work/snapshot"
diff "$work/original" "$CLOUD_STATE" > /dev/null || {
  # jq rewrites formatting; compare canonical JSON, not whitespace.
  [ "$(jq -Sc . "$work/original")" = "$(jq -Sc . "$CLOUD_STATE")" ]
}
pass 'pause and restore both services, scaled counts, bounds and original suspension flags'

reset
save_state '.active=false'
pause_services "$work/snapshot" commerce-svc notification-svc
[ "$(jq length "$work/snapshot")" = 0 ]
if grep -q update-service "$CLOUD_CALLS"; then exit 1; fi
pass 'fresh environment needs no stop or scaling mutation'

for fault in describe_error target_error update_error wait_error; do
  reset
  SCENARIO=$fault
  fails pause_services "$work/snapshot" commerce-svc notification-svc
  pass "pause fails closed on $fault"
done

reset
printf 'invalid-json\n' > "$work/snapshot"
fails restore_services "$work/snapshot"
if grep -q update-service "$CLOUD_CALLS"; then exit 1; fi
pass 'invalid recovery snapshot fails closed without mutating service counts'

reset
fails check_alert_recipients '{"alert_subscription_arns":[]}'
pass 'no configured recipients fails readiness'
reset
fails aws sqs get-queue-attributes --queue-url test --attribute-names ApproximateNumberOfMessagesVisible
grep -q InvalidAttributeName "$work/output"
pass 'mock rejects the CloudWatch metric name as an SQS API attribute'
for fault in pending_email alert_error backlog publish_error receive_error delete_error missing_delivery bad_filter malformed_response openssl_error; do
  reset
  SCENARIO=$fault
  fails check_messaging_routes "$targets"
  if grep -q purge-queue "$CLOUD_CALLS"; then exit 1; fi
  pass "routing/alert readiness rejects $fault without purging queues"
done

reset
check_messaging_routes "$targets" > "$work/output" 2>&1
jq -e '.queues.notification == [] and .queues.audit == []' "$CLOUD_STATE" > /dev/null
[ "$(grep -c 'sqs receive-message.*audit' "$CLOUD_CALLS")" = 4 ]
grep -q 'sleep 900' "$CLOUD_CALLS"
pass 'settle gate, raw fan-out, payment exclusion and probe deletion'

reset
SCENARIO=empty_stdout
check_messaging_routes "$targets" > "$work/output" 2>&1
[ "$(grep -c 'sqs receive-message.*audit' "$CLOUD_CALLS")" = 4 ]
grep -q 'PaymentSucceeded absent from audit in three 20-second long polls' "$work/output"
pass 'AWS CLI successful empty stdout counts as an empty poll, not a failure'

reset
save_state '.queues.notification=[{Body:"{\"real\":true}",ReceiptHandle:"business",MessageAttributes:{}}]'
fails receive_probe "$targets" notification probe TeamSucceeded present
release_held_messages
grep -q change-message-visibility "$CLOUD_CALLS"
if grep -q delete-message "$CLOUD_CALLS"; then exit 1; fi
pass 'unexpected business message is released, never deleted'

reset
save_state '.queues.notification=[{Body:"{\"business\":true}",ReceiptHandle:"business",MessageAttributes:{}}] | .queues.audit=.queues.notification'
publish_probe "$targets" TeamSucceeded probe
receive_probe "$targets" notification probe TeamSucceeded present
receive_probe "$targets" audit probe TeamSucceeded present
release_held_messages
jq -e '.queues.notification[0].ReceiptHandle == "business" and .queues.audit[0].ReceiptHandle == "business" and (.queues.notification | length) == 1 and (.queues.audit | length) == 1' "$CLOUD_STATE" > /dev/null
pass 'unexpected arrival survives defensive receipt handling and visibility restoration'

reset
SCENARIO=delete_error
fails check_messaging_routes "$targets"
jq -e 'all(.queues.notification[]; (.Body | fromjson | .version == 2147483647 and .payload == {}))' "$CLOUD_STATE" > /dev/null
pass 'failed cleanup leaves only unsupported-version probes with no recipient payload'

reset
cp "$CLOUD_STATE" "$work/original"
SCENARIO=receive_error
fails bash "$SWE5001_DIR/scripts/verify-messaging.sh"
[ "$(jq -Sc '.services' "$work/original")" = "$(jq -Sc '.services' "$CLOUD_STATE")" ]
pass 'standalone verification restores both services/scaling after an API failure'

reset
save_state '.active=false | .services |= with_entries(.value.desired=0 | .value.running=0 | .value.scaling=null)'
bash "$SWE5001_DIR/scripts/env-up.sh" > "$work/output" 2>&1 || { cat "$work/output"; exit 1; }
jq -e '.relay == true and all(.services[]; .desired == 1)' "$CLOUD_STATE" > /dev/null
pass 'full fresh bootstrap: stopped provisioning, db-init, readiness then relay/services'

reset
bash "$SWE5001_DIR/scripts/env-up.sh" > "$work/output" 2>&1 || { cat "$work/output"; exit 1; }
jq -e '.services["commerce-svc"].desired == 4 and .services["notification-svc"].desired == 2 and .services["identity-svc"].desired == 1' "$CLOUD_STATE" > /dev/null
pass 'full rerun pauses first and preserves previous scaled counts'

for fault in db_init_error pending_email publish_error edge_error; do
  reset
  SCENARIO=$fault
  fails bash "$SWE5001_DIR/scripts/env-up.sh"
  jq -e 'all(.services[]; .desired == 0 and .running == 0)' "$CLOUD_STATE" > /dev/null
  pass "failed full bootstrap $fault leaves services stopped"
done

for fault in pending_email edge_error; do
  reset
  save_state '.active=false | .services |= with_entries(.value.desired=0 | .value.running=0 | .value.scaling=null)'
  SCENARIO=$fault
  fails bash "$SWE5001_DIR/scripts/env-up.sh"
  jq -e 'all(.services[]; .desired == 0 and .running == 0)' "$CLOUD_STATE" > /dev/null
  pass "failed fresh bootstrap $fault also leaves new services stopped"
done

reset
fails env TF_VAR_alert_email_addresses='[]' bash "$SWE5001_DIR/scripts/env-up.sh"
if grep -q 'terraform.*apply' "$CLOUD_CALLS"; then exit 1; fi
pass 'missing recipients rejected before any apply'
