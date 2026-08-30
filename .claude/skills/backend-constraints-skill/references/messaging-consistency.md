# RabbitMQ, Transactional Consistency, and Idempotency — DEFERRED

> **No message broker is in the v1 stack (user decision 2026-08-30).** Read this file only if the user explicitly approves introducing RabbitMQ. Until then: no MQ producers/consumers, no Outbox tables for messaging, no event contracts. Cross-aggregate consistency uses in-process calls within local transactions; deferred/compensation work uses idempotent scheduled jobs with database state as the backstop. The idempotency, state-machine, and scheduled-job sections below still apply to HTTP write endpoints, future payment callbacks, and batch jobs — adapt "duplicate message" to "duplicate request/job run".

Read this when working with event publishing, consumption, scheduled compensation, cross-resource consistency, or callbacks.

## Message Contracts

- Event names state facts that have happened, e.g. `OrderCreatedV1`; commands state desired actions. Contracts carry `eventId`, `eventType`, `version`, `occurredAt`, `producer`, `traceId`, the business aggregate ID, and a minimal payload.
- Contracts evolve backward-compatibly; new fields are optional with default semantics, and breaking changes bump the version/routing. Never serialize internal aggregates or DOs directly as messages.
- Messages carry no passwords, tokens, or full payment-sensitive data; large payloads go to object storage with a controlled reference passed instead.

## Publishing Reliability

- Exchanges/queues are durable and messages persistent; declare DLQ/DLX, expiry, and queue capacity policies.
- Enable publisher confirms and returns/mandatory; distinguish broker-unconfirmed from unroutable; record correlation/eventId and handle nack/return.
- Business database writes and message publishing use a Transactional Outbox: the same PostgreSQL local transaction writes business data and the outbox; a separate publisher sends and marks on confirmation. "Commit the DB then fire-and-forget the send" is forbidden.
- Outbox publishing may duplicate, so consumers must be idempotent. The publisher uses bounded backoff, state/lock leases, failure alerting, and replay; cleaning published records preserves an audit window.
- Do not claim RabbitMQ transactions plus database transactions form XA; if you choose best-effort, the loss/duplication window and compensation must be explicit.

## Consumption Reliability

- Default to at-least-once semantics. Consumers complete the idempotency check and business transaction before ack; failures nack/reject by type. Never ack before processing.
- Build an inbox/consumption-record unique constraint on `eventId + consumerName`, or achieve idempotency with a natural business unique key; "check-then-insert" must be backstopped by a unique constraint.
- Recoverable transient errors go to a retry queue with bounded attempts and exponential backoff; unrecoverable format/business errors go straight to DLQ with an alert. Infinite immediate requeue hot-loops are forbidden.
- Every retry must be safe; external side effects use the counterparty's idempotency key. After retries are exhausted, provide manual inspection, repair, and replay processes.
- When one aggregate requires ordering, use consistent routing/single-active consumption or business-version rejection of out-of-order messages; do not assume global ordering across queues.
- Consumers set sensible prefetch, concurrency, processing timeout, and graceful shutdown; in-flight messages finish or requeue. Prefer backpressure over unbounded memory buildup.

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
