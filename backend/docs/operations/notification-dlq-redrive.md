# Notification DLQ inspection and redrive

The notification consumer (#446, [backend ADR 0007](../adr/0007-cross-service-events-sns-sqs.md)) deletes a message only after its `(eventId, consumer)` marker and notifications committed, or when its `eventType`/`version` is unsupported. Malformed messages and handling failures stay on the queue; after 5 receives the redrive policy moves them to the DLQ. On the SWE5001 ECS stack the queues are `blocbuy-swe5001-notification` (visibility 60 s, 4-day retention) and `blocbuy-swe5001-notification-dlq` (14-day retention, SSE-SQS, short polling); locally they are ElasticMQ's `notification-events` and `notification-events-dlq` from `backend/elasticmq.conf`.

The log lines quoted below are those of the stage-3 SQS consumer (`SqsNotificationEventConsumer`, `IntegrationEventDecoder`, `NotificationEventAppService`); re-check them against the code if the consumer's log wording changes.

Replay of a valid message is safe: the consumer is idempotent on the envelope `eventId`, never the SQS `MessageId`, so a redriven message whose event was already applied is acknowledged as a duplicate and creates no second notification. A malformed message never succeeds on replay, and redriving it only returns it to the DLQ after another 5 receives. The move task in section 5 cannot pick messages, so decide in section 3 what is in the DLQ before you start it.

## 1. Inspect

Run as an operator in CloudShell in `ap-southeast-1` (or with the session's admin credentials). On the DLQ the operator needs `sqs:GetQueueUrl`, `sqs:GetQueueAttributes`, `sqs:ReceiveMessage`, `sqs:DeleteMessage` and `sqs:ChangeMessageVisibility`; for a move task AWS additionally requires `sqs:StartMessageMoveTask`, `sqs:ListMessageMoveTasks` and `sqs:CancelMessageMoveTask` on the DLQ, and `sqs:SendMessage` on the destination (the source queue, also for the manual fallback). The notification task role has none of the move-task permissions by design.

```bash
DLQ_URL=$(aws sqs get-queue-url --queue-name blocbuy-swe5001-notification-dlq --query QueueUrl --output text)
DLQ_ARN=$(aws sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn --query Attributes.QueueArn --output text)
SRC_URL=$(aws sqs get-queue-url --queue-name blocbuy-swe5001-notification --query QueueUrl --output text)
aws sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible
```

The `blocbuy-swe5001-notification-dlq-not-empty` CloudWatch alarm (#447) watches `ApproximateNumberOfMessagesVisible` on the DLQ and fires on the first message. To look at messages without hiding them, receive with a zero visibility timeout. The DLQ uses short polling, which can return an empty or partial answer even when messages exist, so the command waits up to 20 s for messages:

```bash
aws sqs receive-message --queue-url "$DLQ_URL" --max-number-of-messages 10 --visibility-timeout 0 --wait-time-seconds 20 \
  --attribute-names ApproximateReceiveCount SentTimestamp --message-attribute-names All \
  --query 'Messages[].{id:MessageId,attrs:Attributes,msgAttrs:MessageAttributes,body:Body}'
```

A zero visibility timeout is intended here: the messages stay visible, so the alarm state and a later move task are not disturbed, and the DLQ has no redrive policy of its own, so the extra receives do no harm. The consequence is that every call can return the same messages again and returns at most 10, so with more than 10 messages in the DLQ use the `dlq_take` helper in section 4, which hides one batch while you work on it. `SentTimestamp` is in epoch milliseconds.

Never use `purge-queue` on the DLQ. Delete a DLQ message only after its identifiers are recorded (section 4). Bodies contain only IDs (no names, emails, tokens or amounts), but still copy only `eventId`, `eventType`, `version`, `traceId`, the `MessageId` and the log reason into issues, chats or PRs.

## 2. Diagnose

Take the `MessageId` from the receive output and the envelope `eventId` and `traceId` from the body (or the `traceId` message attribute), then search the consumer log (`/blocbuy/swe5001/notification-svc`, or the monolith log locally). Both consumer WARN lines carry the `messageId`; only the malformed-message line carries an `eventId`, and only when the body had a valid one:

```bash
aws logs tail /blocbuy/swe5001/notification-svc --since 6h --filter-pattern '"<messageId>"'
aws logs tail /blocbuy/swe5001/notification-svc --since 6h --filter-pattern '"<traceId>"'
```

The trace ID is in the MDC prefix of the lines a message worker logs, including the handler's `Integration event applied/already consumed` lines. It is the message's `traceId` attribute if that is valid (1-64 of `A-Za-z0-9-_.`), else the body's `traceId` when the body decodes, else a random UUID generated for that receive, which matches nothing upstream. Lines from the poller thread (`Notification consumer receive failed; retrying in ...`, started, stopped) carry no trace ID. The log group keeps 7 days by default, so older messages cannot be classified from the log.

| Log line (WARN) | Cause | Fix before redrive |
|---|---|---|
| `Malformed integration event left for redrive messageId=... eventId=... receiveCount=... reason=...` | Body is not JSON or not a JSON object, `eventType` is missing, `version` is not an integer, or the envelope or payload does not match its `*V1` contract (the reason ends with the exception class, never with payload values); `eventId` is `null` when the body had none | A producer bug: fix commerce and let the corrected event flow normally. A malformed message never succeeds on replay, so do not redrive it (section 3). The outbox row with `id = eventId` in `domain.sys_outbox` shows what was published, see [outbox relay recovery](outbox-relay-recovery.md), but only for about 7 days: published rows are deleted once `published_at` is more than 7 days old (the retention job runs every 6 hours and removes at most 500 rows per run), while the DLQ keeps a message for 14 days from its original enqueue. Later than that the DLQ body is the only copy of the envelope, so record it in the issue first |
| `Integration event handling failed; left for retry messageId=... receiveCount=...` with an exception | Database outage, lock or transaction timeout, or a consumer bug | Fix the dependency or deploy the consumer fix, then redrive |
| no consumer line for the `messageId` | The consumer never received it (stopped service, wrong queue URL, IAM), or the log has expired | Fix the deployment, then redrive; if only the log is gone, redrive that one message on its own (section 5, manual fallback): it either succeeds or comes back |

Messages with an unsupported `eventType` or `version` are acknowledged by the consumer (`Acknowledging unsupported integration event without effect ...`) and never reach the DLQ.

The same trace ID finds the producing request in the commerce log (`/blocbuy/swe5001/commerce-svc`).

## 3. Decide: delete, redrive or both

Classify every DLQ message with the table above and record `eventId` (if any), `eventType`, `version`, `traceId`, `MessageId` and the log reason in the issue. Then pick one case.

- (a) The DLQ holds only malformed messages: do not redrive. Delete each recorded message (section 4). This is also what returns the alarm to OK: a message left in the DLQ keeps `blocbuy-swe5001-notification-dlq-not-empty` in ALARM, and CloudWatch notifies only on state changes, so later dead letters would send no email. The V7 poison-message drill ends here.
- (b) The DLQ holds only retryable failures and the cause is fixed: redrive with the move task (section 5). This is safe because the consumer is idempotent on the envelope `eventId`.
- (c) The DLQ holds both. Pick one of: run the move task anyway and accept that the malformed messages are received 5 more times (about 5 minutes at the 60 s visibility timeout) and bounce back to the DLQ, keeping their original enqueue time and retention clock, then handle them as (a); or redrive only the retryable messages one by one with the manual fallback in section 5; or record and delete the malformed messages first (section 4) and then use the move task.

## 4. Delete recorded malformed messages

Only after the identifiers are in the issue. `dlq_take` receives one batch with a 120 s visibility timeout, releases every message except the one you ask for, and prints that message as JSON. It needs `jq` (present in CloudShell). If it prints nothing, the message was not in the batch; run it again.

```bash
dlq_take() {  # $1 = MessageId
  local batch rh
  batch=$(aws sqs receive-message --queue-url "$DLQ_URL" --max-number-of-messages 10 --wait-time-seconds 20 \
    --visibility-timeout 120 --message-attribute-names All --output json)
  jq -r --arg id "$1" '.Messages[]? | select(.MessageId != $id) | .ReceiptHandle' <<<"$batch" | while read -r rh; do
    aws sqs change-message-visibility --queue-url "$DLQ_URL" --receipt-handle "$rh" --visibility-timeout 0
  done
  jq -c --arg id "$1" '.Messages[]? | select(.MessageId == $id)' <<<"$batch"
}
MSG=$(dlq_take "<MessageId from the issue>")
aws sqs delete-message --queue-url "$DLQ_URL" --receipt-handle "$(jq -r .ReceiptHandle <<<"$MSG")"
```

Repeat for each recorded message, then check `ApproximateNumberOfMessages` and `ApproximateNumberOfMessagesNotVisible` are both 0 and the alarm returns to OK (the alarm period is 60 s). While a message is hidden by `dlq_take` the Visible count can briefly drop, which may flap the alarm and send one extra email.

## 5. Fix forward, then redrive

Deploy the fix first and confirm the consumer is healthy (no new WARN lines for fresh events). The move task moves every message in the DLQ and cannot select, so use it only for case (b), or for case (c) after you accepted the bounce. Without `--destination-arn` SQS returns each message to the queue it came from:

```bash
aws sqs start-message-move-task --source-arn "$DLQ_ARN" --max-number-of-messages-per-second 10
aws sqs list-message-move-tasks --source-arn "$DLQ_ARN" --max-results 1
```

`list-message-move-tasks` reports `RUNNING`, `COMPLETED`, `CANCELLING`, `CANCELLED` or `FAILED` with moved and total counts. Use `--destination-arn <queue-arn>` only to send to a different queue, for example a scratch queue for analysis. A low rate keeps the redrive within the consumer's concurrency; only one move task per source queue can run at a time. To stop a running task (messages already moved stay moved):

```bash
aws sqs cancel-message-move-task --task-handle "<TaskHandle from start-message-move-task>"
```

To redrive a single message instead (case (c), or an unclassifiable message), copy it to the source queue and delete it from the DLQ only after the send succeeded; a crash in between leaves a duplicate, which the consumer ignores. This uses `dlq_take` from section 4:

```bash
MSG=$(dlq_take "<MessageId from the issue>")
aws sqs send-message --queue-url "$SRC_URL" --message-body "$(jq -r .Body <<<"$MSG")" --message-attributes "$(jq -c .MessageAttributes <<<"$MSG")" \
  && aws sqs delete-message --queue-url "$DLQ_URL" --receipt-handle "$(jq -r .ReceiptHandle <<<"$MSG")"
```

Afterwards check that the DLQ is empty (apart from messages you decided to delete), the source queue drains, and each `eventId` has one row in `sys_consumed_message` and one `biz_notification` row per recipient (notification database, read-only query):

```sql
SELECT consumer, consumed_at FROM domain.sys_consumed_message WHERE event_id = '<event-id>';
SELECT user_id, notification_type FROM domain.biz_notification WHERE event_id = '<event-id>';
```

For standard queues AWS keeps a message's original enqueue time when it is dead-lettered, so a message that waited days in the source queue expires from the DLQ earlier than 14 days after its failure; redrive or record it promptly.

## Local ElasticMQ (Compose)

Checked against `softwaremill/elasticmq-native:1.7.1` with the repository `elasticmq.conf` and AWS CLI 2.18 on 2026-10-16. A message sent to `notification-events-dlq` was received, and `start-message-move-task` without a destination moved it to `notification-events`, with its `traceId` attribute intact and a receive count of 1 there. `list-message-move-tasks` and `cancel-message-move-task` are not usable locally: each made the native server throw an `ExceptionInInitializerError` and exit, so the container has to be restarted and loses its messages. Verify a local move with queue attributes and never call those two commands.

```bash
export AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local AWS_DEFAULT_REGION=us-east-1
EMQ="--endpoint-url http://localhost:9324"
aws $EMQ sqs get-queue-attributes --queue-url http://localhost:9324/000000000000/notification-events-dlq --attribute-names ApproximateNumberOfMessages
aws $EMQ sqs receive-message --queue-url http://localhost:9324/000000000000/notification-events-dlq --visibility-timeout 0 --wait-time-seconds 2 --message-attribute-names All
aws $EMQ sqs start-message-move-task --source-arn arn:aws:sqs:elasticmq:000000000000:notification-events-dlq --max-number-of-messages-per-second 10
aws $EMQ sqs get-queue-attributes --queue-url http://localhost:9324/000000000000/notification-events --attribute-names ApproximateNumberOfMessages
```

The Compose file does not publish ElasticMQ's port, and its `data` network is `internal: true`. Either add `ports: ["9324:9324"]` to the `elasticmq` service locally (do not commit it), or run the CLI in a container on the network Compose creates, `blocbuy-backend_data` (the project name is `blocbuy-backend`, set by `name:` in `backend/docker-compose.yaml`):

```bash
docker run --rm --network blocbuy-backend_data -e AWS_ACCESS_KEY_ID=local -e AWS_SECRET_ACCESS_KEY=local -e AWS_DEFAULT_REGION=us-east-1 -e AWS_ENDPOINT_URL=http://elasticmq:9324 \
  amazon/aws-cli sqs get-queue-attributes --queue-url http://elasticmq:9324/000000000000/notification-events-dlq --attribute-names ApproximateNumberOfMessages
```

Locally the delete and single-message flows of sections 4 and 5 work the same way with `DLQ_URL=http://localhost:9324/000000000000/notification-events-dlq` and `SRC_URL=http://localhost:9324/000000000000/notification-events` (set `AWS_ENDPOINT_URL=http://localhost:9324`); both were exercised on 2026-10-16.
