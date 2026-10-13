# shellcheck shell=bash
# Routing probes use an unsupported envelope version, never real recipient IDs.
# ADR 0007 requires consumers to acknowledge unknown versions without effects, so
# a rare delayed/duplicate probe remains harmless after stopped services resume.

check_alert_recipients() {
  local targets=$1 arn attributes
  jq -e '.alert_subscription_arns | type == "array" and length > 0' <<< "$targets" > /dev/null || {
    log "no alarm recipients configured; set TF_VAR_alert_email_addresses / SWE5001_ALERT_EMAIL_ADDRESSES"
    return 1
  }
  while IFS= read -r arn; do
    attributes=$(aws sns get-subscription-attributes --subscription-arn "$arn" --output json) || return 1
    jq -e '.Attributes | .PendingConfirmation == "false" and .Protocol == "email"' <<< "$attributes" > /dev/null || {
      log "an alarm subscription is not confirmed; confirm the SNS email before continuing"
      return 1
    }
  done < <(jq -r '.alert_subscription_arns[]' <<< "$targets")
}

check_queues_empty() {
  local targets=$1 queue attributes
  # A readiness drill must not spend a business message's redrive budget. The
  # receipt guard below is defence in depth for arrivals not yet reflected here.
  for queue in notification audit; do
    attributes=$(aws sqs get-queue-attributes --queue-url "$(jq -r ".queues.$queue" <<< "$targets")" \
      --attribute-names ApproximateNumberOfMessagesVisible ApproximateNumberOfMessagesNotVisible ApproximateNumberOfMessagesDelayed --output json) || return 1
    jq -e '.Attributes | .ApproximateNumberOfMessagesVisible == "0" and .ApproximateNumberOfMessagesNotVisible == "0" and .ApproximateNumberOfMessagesDelayed == "0"' <<< "$attributes" > /dev/null || {
      log "$queue has retained messages; defer the readiness drill, never purge or spend their receive budget"
      return 1
    }
  done
}

release_held_messages() {
  local entry failed=0
  while IFS= read -r entry; do
    aws sqs change-message-visibility --queue-url "$(jq -r .queue <<< "$entry")" \
      --receipt-handle "$(jq -r .handle <<< "$entry")" --visibility-timeout 0 || failed=1
  done < <(jq -c '.[]' "$PROBE_RECEIPTS")
  return "$failed"
}

publish_probe() {
  local targets=$1 event_type=$2 event_id=$3 body
  body=$(jq -nc --arg id "$event_id" --arg type "$event_type" --arg now "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{eventId:$id,eventType:$type,version:2147483647,occurredAt:$now,producer:"commerce-svc",traceId:"routing-readiness-probe",aggregateId:$id,payload:{}}') || return 1
  aws sns publish --topic-arn "$(jq -r .topic_arn <<< "$targets")" --message "$body" \
    --message-attributes "$(jq -nc --arg type "$event_type" '{eventType:{DataType:"String",StringValue:$type},version:{DataType:"String",StringValue:"2147483647"},traceId:{DataType:"String",StringValue:"routing-readiness-probe"}}')" \
    --query MessageId --output text > /dev/null
}

receive_probe() {
  local targets=$1 queue=$2 id=$3 event_type=$4 expected=$5 response message body handle batches=0 empty_polls=0 found=false deadline url
  deadline=$(($(date +%s) + 180))
  url=$(jq -r ".queues.$queue" <<< "$targets")
  while [ "$batches" -lt 200 ] && [ "$empty_polls" -lt 3 ] && [ "$(date +%s)" -lt "$deadline" ]; do
    response=$(aws sqs receive-message --queue-url "$(jq -r ".queues.$queue" <<< "$targets")" \
      --wait-time-seconds 20 --visibility-timeout 300 --max-number-of-messages 10 --message-attribute-names All --output json) || return 1
    # AWS returns {} on an empty receive. Reject malformed mock/API output.
    jq -e 'type == "object" and ((.Messages // []) | type == "array" and all(.[]; (.Body | type == "string") and (.ReceiptHandle | type == "string" and length > 0)))' <<< "$response" > /dev/null || return 1
    while IFS= read -r message; do
      body=$(jq -r .Body <<< "$message")
      handle=$(jq -r .ReceiptHandle <<< "$message")
      if jq -e --arg id "$id" --arg type "$event_type" \
        '.eventId == $id and .eventType == $type and .version == 2147483647 and .payload == {}' <<< "$body" > /dev/null 2>&1 && \
        jq -e --arg type "$event_type" '.MessageAttributes.eventType.StringValue == $type' <<< "$message" > /dev/null; then
        aws sqs delete-message --queue-url "$(jq -r ".queues.$queue" <<< "$targets")" --receipt-handle "$handle" || return 1
        found=true
      else
        # Hold unrelated messages only while searching for the unique probe. The
        # EXIT handler releases every receipt before services resume; never purge.
        jq --arg queue "$url" --arg handle "$handle" '. + [{queue:$queue,handle:$handle}]' \
          "$PROBE_RECEIPTS" > "$PROBE_RECEIPTS.next" || return 1
        mv "$PROBE_RECEIPTS.next" "$PROBE_RECEIPTS"
      fi
    done < <(jq -c '.Messages[]?' <<< "$response")
    if [ "$found" = true ]; then
      [ "$expected" = present ] || { log "payment probe was misrouted to audit"; return 1; }
      log "$event_type reached $queue as a raw envelope with its routing attribute"
      return 0
    fi
    if [ "$(jq '(.Messages // []) | length' <<< "$response")" = 0 ]; then
      empty_polls=$((empty_polls + 1))
    fi
    batches=$((batches + 1))
  done
  [ "$expected" = absent ] && [ "$empty_polls" -ge 3 ] || { log "$event_type/$queue probe was inconclusive within the bounded receive budget"; return 1; }
  log "$event_type absent from $queue in three 20-second long polls (bounded observation)"
}

check_messaging_routes() {
  local targets=$1 team_id payment_id suffix
  # Always wait the documented upper propagation bound, including on reruns that
  # may have changed filters. Reading GetSubscriptionAttributes is not a settle gate.
  log "waiting 900 seconds for SNS filter propagation; confirm the alarm emails now"
  sleep 900
  check_alert_recipients "$targets" || return 1
  check_queues_empty "$targets" || return 1
  suffix=$(openssl rand -hex 6) || return 1
  [[ "$suffix" =~ ^[0-9a-f]{12}$ ]] || return 1
  team_id="00000000-0000-7000-8000-$suffix"
  suffix=$(openssl rand -hex 6) || return 1
  [[ "$suffix" =~ ^[0-9a-f]{12}$ ]] || return 1
  payment_id="00000000-0000-7000-8000-$suffix"
  publish_probe "$targets" TeamSucceeded "$team_id" || return 1
  receive_probe "$targets" notification "$team_id" TeamSucceeded present || return 1
  receive_probe "$targets" audit "$team_id" TeamSucceeded present || return 1
  publish_probe "$targets" PaymentSucceeded "$payment_id" || return 1
  receive_probe "$targets" notification "$payment_id" PaymentSucceeded present || return 1
  receive_probe "$targets" audit "$payment_id" PaymentSucceeded absent || return 1
}
