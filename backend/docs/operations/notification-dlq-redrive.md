# Notification DLQ inspection and redrive

The notification consumer (#446, [backend ADR 0007](../adr/0007-cross-service-events-sns-sqs.md)) deletes a message only after its `(eventId, consumer)` marker and notifications committed, or when its `eventType`/`version` is unsupported. Malformed messages and handling failures stay on the queue; after 5 receives the redrive policy moves them to the DLQ. On the SWE5001 ECS stack the queues are `blocbuy-swe5001-notification` (visibility 60 s, 4-day retention) and `blocbuy-swe5001-notification-dlq` (14-day retention, SSE-SQS); locally they are ElasticMQ's `notification-events` and `notification-events-dlq` from `backend/elasticmq.conf`.

Replay is safe: the consumer is idempotent on the envelope `eventId`, never the SQS `MessageId`, so a redriven message whose event was already applied is acknowledged as a duplicate and creates no second notification. Redrive only after the cause is fixed, otherwise the messages return to the DLQ after another 5 receives.

## 1. Inspect

Run as an operator in CloudShell in `ap-southeast-1` (or with the session's admin credentials). The SQS operations need `sqs:GetQueueUrl`, `sqs:GetQueueAttributes`, `sqs:ReceiveMessage` on the DLQ and, for redrive, `sqs:StartMessageMoveTask`, `sqs:ListMessageMoveTasks`, `sqs:CancelMessageMoveTask` plus `sqs:SendMessage` on the destination; the notification task role has none of the move-task permissions by design.

```bash
DLQ_URL=$(aws sqs get-queue-url --queue-name blocbuy-swe5001-notification-dlq --query QueueUrl --output text)
DLQ_ARN=$(aws sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn --query Attributes.QueueArn --output text)
aws sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible
```

The `ApproximateNumberOfMessagesVisible` alarm on the DLQ (#447) fires on the first message. To look at messages without removing them, receive with a zero visibility timeout so each one stays visible for the move task; the DLQ has no redrive policy, so the extra receives do no harm:

```bash
aws sqs receive-message --queue-url "$DLQ_URL" --max-number-of-messages 10 --visibility-timeout 0 \
  --attribute-names ApproximateReceiveCount SentTimestamp --message-attribute-names All \
  --query 'Messages[].{id:MessageId,attrs:MessageAttributes,body:Body}'
```

Never use `purge-queue` or `delete-message` on the DLQ to make an alarm go away. Bodies contain only IDs (no names, emails, tokens or amounts), but still copy only `eventId`, `eventType`, `version` and `traceId` into issues, chats or PRs.

## 2. Diagnose

Take the envelope `eventId` and `traceId` from the body (or the `traceId` message attribute) and search the consumer log (`/blocbuy/swe5001/notification-svc`, or the monolith log locally); every consumer line carries the trace ID in its MDC prefix:

```bash
aws logs tail /blocbuy/swe5001/notification-svc --since 6h --filter-pattern '"<eventId>"'
aws logs tail /blocbuy/swe5001/notification-svc --since 6h --filter-pattern '"<traceId>"'
```

| Log line (WARN) | Cause | Fix before redrive |
|---|---|---|
| `Malformed integration event left for redrive ... reason=...` | Body is not JSON, an envelope field is missing, or the payload breaks its `*V1` contract; the reason names the failed check | A producer bug: fix commerce (the outbox row with `id = eventId` in `domain.sys_outbox` shows what was published, see [outbox relay recovery](outbox-relay-recovery.md)) and let the corrected event flow normally. A malformed message never succeeds on replay, so do not redrive it; keep it in the DLQ as evidence until retention expires |
| `Integration event handling failed; left for retry ...` with an exception | Database outage, lock or transaction timeout, or a consumer bug | Fix the dependency or deploy the consumer fix, then redrive |
| no consumer line for the `eventId` | The consumer never received it (stopped service, wrong queue URL, IAM) | Fix the deployment, then redrive |

The same trace ID finds the producing request in the commerce log (`/blocbuy/swe5001/commerce-svc`).

## 3. Fix forward, then redrive

Deploy the fix first and confirm the consumer is healthy (no new WARN lines for fresh events). Then move the DLQ back to its source queue; without `--destination-arn` SQS returns each message to the queue it came from:

```bash
aws sqs start-message-move-task --source-arn "$DLQ_ARN" --max-number-of-messages-per-second 10
aws sqs list-message-move-tasks --source-arn "$DLQ_ARN" --max-results 1
```

`list-message-move-tasks` reports `RUNNING`, `COMPLETED`, `CANCELLED` or `FAILED` with moved and total counts. Use `--destination-arn <queue-arn>` only to send to a different queue, for example a scratch queue for analysis. A low rate keeps the redrive within the consumer's concurrency; only one move task per source queue can run at a time. To stop a running task (messages already moved stay moved):

```bash
aws sqs cancel-message-move-task --task-handle "<TaskHandle from start-message-move-task>"
```

Afterwards check that the DLQ is empty, the source queue drains, and each `eventId` has one row in `sys_consumed_message` and one `biz_notification` row per recipient (notification database, read-only query):

```sql
SELECT consumer, consumed_at FROM domain.sys_consumed_message WHERE event_id = '<event-id>';
SELECT user_id, notification_type FROM domain.biz_notification WHERE event_id = '<event-id>';
```

For standard queues AWS keeps a message's original enqueue time when it is dead-lettered, so a message that waited days in the source queue expires from the DLQ earlier than 14 days after its failure; redrive or record it promptly.

## Local ElasticMQ (Compose)

Checked against `softwaremill/elasticmq-native:1.7.1` with the repository `elasticmq.conf` on 2026-10-16: a message received 5 times moved to `notification-events-dlq`, and `start-message-move-task` without a destination moved it back to `notification-events`. In the same check `list-message-move-tasks` made the native server return HTTP 500 and stop, so locally verify with queue attributes instead and do not call it. `cancel-message-move-task` was not checked locally.

```bash
export AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local AWS_DEFAULT_REGION=us-east-1
EMQ="--endpoint-url http://localhost:9324"
aws $EMQ sqs get-queue-attributes --queue-url http://localhost:9324/000000000000/notification-events-dlq --attribute-names ApproximateNumberOfMessages
aws $EMQ sqs receive-message --queue-url http://localhost:9324/000000000000/notification-events-dlq --visibility-timeout 0 --message-attribute-names All
aws $EMQ sqs start-message-move-task --source-arn arn:aws:sqs:elasticmq:000000000000:notification-events-dlq --max-number-of-messages-per-second 10
aws $EMQ sqs get-queue-attributes --queue-url http://localhost:9324/000000000000/notification-events --attribute-names ApproximateNumberOfMessages
```

The Compose file does not publish ElasticMQ's port; for these commands add `ports: ["9324:9324"]` to the `elasticmq` service locally (do not commit it) or run the CLI in a container on the `data` network with `--endpoint-url http://elasticmq:9324`. If a move task is unavailable, the manual fallback is to receive each DLQ message, `send-message` its body and `eventType`/`version`/`traceId` attributes to `notification-events`, and only then `delete-message` it from the DLQ; a crash in between leaves a duplicate, which the consumer ignores.
