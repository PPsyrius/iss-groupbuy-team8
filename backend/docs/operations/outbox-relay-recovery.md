# Outbox relay recovery

The relay is at-least-once. A crash after the broker accepts an event but before PostgreSQL records `published_at` can cause a redelivery; event consumers must deduplicate by `eventId`.

## Inspect a failed event

1. Find the `Outbox relay` ERROR log and record its `eventId` and `traceId`.
2. Inspect the row without changing it:

   ```sql
   SELECT id, event_type, attempts, next_attempt_at, failed_at, published_at, trace_id
   FROM domain.sys_outbox
   WHERE id = '<event-id>';
   ```

3. Check the configured SQS queue/SNS topic, IAM permissions, and relay logs. Do not edit the event payload or publish it manually while the relay is enabled.

## Replay one terminal failure

After fixing the cause and confirming the row is still unpublished, an operator may requeue only that event:

```sql
UPDATE domain.sys_outbox
SET attempts = 0,
    next_attempt_at = clock_timestamp(),
    failed_at = NULL
WHERE id = '<event-id>'
  AND published_at IS NULL
  AND failed_at IS NOT NULL;
```

Require exactly one updated row. If zero or more than one row is reported, stop and investigate rather than broadening the update. The normal relay will retry it; verify the row becomes published and correlate the result using `eventId`/`traceId`. Record who replayed it, when, the cause, and the outcome in the incident/ticket. Never replay by deleting the row.

For outages that have not reached the terminal retry limit, the relay retries with backoff automatically; do not reset those rows pre-emptively. AWS resource creation and IAM policy changes are outside this runbook and belong to the deployment/resource owner.
