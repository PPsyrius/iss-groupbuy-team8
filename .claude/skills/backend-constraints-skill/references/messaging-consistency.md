# Event Messaging, Transactional Consistency, and Idempotency

> **Messaging is limited to [backend ADR 0007](../../../../backend/docs/adr/0007-cross-service-events-sns-sqs.md) (2026-10-11):** transactional outbox → one SNS topic → one SQS queue per consumer with a DLQ; ElasticMQ replaces SNS/SQS locally and in CI (`blocbuy.events.transport`); the EC2 `dev` stack runs no broker and keeps the relay and consumer off. RabbitMQ stays out of the stack (decision 2026-08-30). Within one service, cross-aggregate consistency still uses in-process calls within local transactions, and deferred/compensation work uses idempotent scheduled jobs with database state as the backstop. The idempotency, state-machine, and scheduled-job sections below also apply to HTTP write endpoints, payment callbacks, and batch jobs — adapt "duplicate message" to "duplicate request/job run".

Read this when working with event publishing, consumption, scheduled compensation, cross-resource consistency, or callbacks.

## Message Contracts

- Event names state facts that have happened, e.g. `OrderCreatedV1`; commands state desired actions. Contracts carry `eventId`, `eventType`, `version`, `occurredAt`, `producer`, `traceId`, the business aggregate ID, and a minimal payload.
- Contracts evolve backward-compatibly; new fields are optional with default semantics, and breaking changes bump the version/routing. Never serialize internal aggregates or DOs directly as messages.
- Messages carry no passwords, tokens, or full payment-sensitive data; large payloads go to object storage with a controlled reference passed instead.

## Publishing Reliability

- Every queue declares a DLQ (`maxReceiveCount`), retention, visibility timeout and server-side encryption; queue policies accept messages only from the owning topic.
- The relay marks a row published only after a successful SNS/SQS response; batch calls report per-entry failures, which are retried individually by `eventId`.
- Business database writes and message publishing use a Transactional Outbox: the same PostgreSQL local transaction writes business data and the outbox; a separate publisher sends and marks on confirmation. "Commit the DB then fire-and-forget the send" is forbidden.
- Outbox publishing may duplicate, so consumers must be idempotent. The publisher uses bounded backoff, state/lock leases, failure alerting, and replay; cleaning published records preserves an audit window.
- SNS/SQS and PostgreSQL never form one transaction; if you choose best-effort anywhere, the loss/duplication window and compensation must be explicit.

## Consumption Reliability

- Default to at-least-once semantics. Consumers complete the idempotency check and business transaction before deleting the message; never delete before processing.
- Build an inbox/consumption-record unique constraint on `eventId + consumerName` (envelope `eventId`, never the SQS `MessageId`), or achieve idempotency with a natural business unique key; "check-then-insert" must be backstopped by a unique constraint.
- Failed messages are left undeleted so the visibility timeout spaces retries and redrive moves them to the DLQ after `maxReceiveCount` receives; the DLQ is alarmed. Unknown event types or versions are logged and deleted for forward compatibility. Immediate requeue hot-loops are forbidden.
- Every retry must be safe; external side effects use the counterparty's idempotency key. After retries are exhausted, provide manual inspection, repair, and replay processes.
- When one aggregate requires ordering, use consistent routing/single-active consumption or business-version rejection of out-of-order messages; do not assume global ordering across queues.
- Consumers set sensible batch size, concurrency, visibility timeout, and graceful shutdown; in-flight messages finish or become visible again. Prefer backpressure over unbounded memory buildup.

## BlocBuy Consistency Rules

- Order placement is idempotent by business request number; orders, inventory reservations, and payment orders each have database unique/state constraints.
- Inventory deduction uses atomic conditional updates (e.g. `available >= quantity`) or version control; duplicate MQ consumption must not deduct twice.
- Payment/refund callbacks deduplicate on channel event ID, channel transaction number, and business order number; the state machine only allows legal one-way transitions; amount/currency mismatches must be rejected and alerted.
- Group completion, timeout closing, inventory release, and refund are all re-executable; scheduled scans and messages may run concurrently, and the database state condition decides the final outcome.
- Sagas/compensation for long cross-aggregate flows record steps, idempotency keys, retry state, and manual intervention points. Compensation is not a database rollback - its business meaning must be defined.

## Scheduled Jobs

- Multi-instance jobs use database preemption, sharding, or a mature scheduler, and stay idempotent; Redis locks coordinate only, with database state/unique constraints still backstopping.
- Scans use keyset/sharded batching with a fixed batch size and timeout; never load a whole table at once. Record the scan watermark, success/failure counts, and retries.
- Every compensation job has a stop condition, maximum retries, alerting, and a manual recovery entry point.
