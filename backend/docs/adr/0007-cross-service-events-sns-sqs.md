# 0007. Cross-service events go through a transactional outbox, one SNS topic and an SQS queue per consumer

Date: 2026-10-11

## Status

Accepted (#443, parent #450) on 2026-11-06 by the review of the pull request that closes #443; that review stands in for the stand-up agreement the proposal asked for. Implemented by #473 (outbox), #485 (relay), #513 (SQS consumer) and #507 (DLQ runbook).

Scope: the SWE5001 ECS topology (`infra/swe5001`). There the relay and consumer are on only when `services_enabled` and `messaging_verified` are both true, and `env-up.sh` sets the latter only after its routing gate; the repository records no ECS session that ran with the relay on. The SWE5006 EC2 `dev` stack keeps relay and consumer off and runs no broker (Sprint 4 decision, 2026-10-11). Local runs and CI use ElasticMQ.

Amends the "no message broker" rule of 2026-08-30 for messaging only; RabbitMQ and Elasticsearch stay out of the stack.

## Context

The notification context is a placeholder (#215): nothing calls it and it consumes no events. The SWE5001 distributed shape extracts it as `notification-svc`, which must react to commerce facts (`TeamSucceeded`, `TeamExpired`, `PaymentSucceeded`) without calling commerce back. The SWE5001 presentation (2026-10-21) is judged on a standard event-driven implementation and on scalability.

Domain events are published in-process today and are consumed before commit (ADR 0002) or after commit (ADR 0003). Publishing them to a broker after commit would be a dual write: a crash between commit and send loses the event, and a send before commit can announce a fact that is rolled back. `TeamSucceeded` and `TeamExpired` carry no member user IDs, so a consumer cannot tell whom to notify.

Cost is a constraint. The SWE5001 ECS runtime is ephemeral and costs about US$0.2 per hour, so it cannot be the only place where messaging is verified. SNS and SQS are priced per request and have no idle cost; at course volume both stay inside the free tier.

## Decision

**Outbox.** A `BEFORE_COMMIT` listener with the lowest precedence maps each relevant domain event to a versioned integration event and inserts it into `sys_outbox` in the same transaction. Recipient user IDs are resolved in that transaction, so integration events are self-contained; domain event records are unchanged. The outbox row ID (UUIDv7) is the event's `eventId`.

**Envelope.** `{eventId, eventType, version, occurredAt, producer, traceId, aggregateId, payload}`. Contracts such as `TeamSucceededV1` live in `blocbuy-client` and evolve additively; a breaking change is a new version. Payloads carry IDs and references only, never tokens, emails or payment-sensitive data. `eventType`, `version` and `traceId` are also sent as message attributes.

**Relay.** A scheduled relay under `LeasedJobRunner` publishes unpublished rows in batches, marks them published, backs off on failure and marks a row failed with an ERROR log after a bounded number of attempts. Published rows are deleted after 7 days.

**Topology.** One SNS topic for domain events. Each consumer owns one standard SQS queue subscribed with raw message delivery and, where it needs only some events, a filter policy on `eventType`. Each queue has a DLQ with `maxReceiveCount = 5`. The first consumer is notification; a filtered audit queue is the second subscriber and demonstrates fan-out. Queue policies accept messages only from the topic.

**Consumer.** Delivery is at-least-once. The handler records `(eventId, consumer)` in `sys_consumed_message` and writes its effects in one transaction, then deletes the message; a duplicate `eventId` is a no-op. SQS `MessageId` is never the idempotency key, because a relay retry or SNS redelivery produces a new one. Unknown `eventType` or `version` is logged and acknowledged for forward compatibility; malformed or failing messages are left for redrive to the DLQ. The consumer restores `traceId` into MDC.

**Transport switch.** `blocbuy.events.transport` selects `sqs` or `sns`, independently of the deployment topology. Local development and CI use `sqs` against ElasticMQ, which implements the SQS API including DLQ redrive. The SWE5001 ECS shape uses `sns` against real SNS and SQS. Raw message delivery makes the consumer's input identical on both paths, so only the relay's send call differs. The relay and the consumer are enabled by property; the SWE5006 EC2 `dev` stack leaves both off and runs no broker (Sprint 4 scope decision, 2026-10-11: notifications serve SWE5001 only).

Review amendment 2026-10-13 (#444 / PR #473): commerce explicitly includes the outbox component, mapper and XML packages in its assembly. Retention uses a domain-side technical port and one SQL delete per run (maximum 500 rows, 3 s transaction timeout, `SKIP LOCKED`); it does not loop until the backlog is drained. The default six-hour interval gives a nominal 2000-row/day budget per continuously running instance, not a cluster-wide cap or drain guarantee; independent task schedules and restarts may add sequential runs. Operators must choose an interval appropriate to the published backlog. A renewable lease provides single-flight execution, not a total run-time bound. Enabled/disabled commerce assembly tests and an injected outbox insertion failure during `BEFORE_COMMIT` verify wiring and rollback of settlement, membership, stock and order effects.

Review amendment 2026-10-13 (#445, relay from PR #477 ported onto `dev`):
- **Batch calls.** The relay sends one tick's due rows (at most 100) as `SendMessageBatch` or `PublishBatch` calls split at the provider's 10-entry limit, so a tick makes at most 10 calls. This is a recorded exception to the backend rule against remote calls in loops: the calls are the provider's batch API, bounded by the tick size, never one call per row.
- **One retry budget.** `OutboxRelayService` owns the budget (10 attempts) and passes it to the single `UPDATE` that records results. That statement opens no transaction of its own, and no transaction spans provider I/O.
- **Region and target.** The Region comes from `blocbuy.events.region` or the AWS SDK default chain (`AWS_REGION` on ECS), never a guessed default. An enabled relay with a blank queue URL or topic ARN, or a transport other than `sqs` or `sns`, fails startup, so committed events are not moved to `failed_at` by a misconfiguration.
- **Ownership.** `com.blocbuy.integration` is part of commerce in `ArchitectureTest`. Identity and notification assemblies start with no relay, transport or writer, even with the switches on.
- **Switching it on for ECS (#447).** Set, on `commerce-svc` only, `BLOCBUY_EVENTS_RELAY_ENABLED=true` and `BLOCBUY_EVENTS_SNS_TOPIC_ARN`, and grant its task role `sns:Publish` on that topic. The commerce profile already selects `sns`.
- **Failure isolation and lease loss (PR #485 follow-up).** A shared infrastructure batch publisher isolates serialization failures per event and provider exceptions per ten-entry batch, preserves earlier confirmations, and continues later batches while the lease remains valid. The scheduler passes its live lease signal through the application port; transports check it before each batch and again before sending prepared entries. Unattempted rows return explicit skipped results and are excluded from the results `UPDATE`, so their attempts and retry deadlines remain unchanged. Both SDK clients use a 5 s attempt timeout and a 15 s total call timeout including SDK retries. Cancellation is cooperative, not provider fencing: a call already in flight may finish after lease loss and overlap a successor, so consumers must still deduplicate by `eventId`. Regression tests cover middle-batch exceptions, invalid rows, skipped updates, lease loss before sending and takeover during an in-flight call; no transaction spans I/O.

Review amendment 2026-10-16 (#446, consumer): Spring Cloud AWS has no Spring Boot 4.1 line, so the consumer is an AWS SDK v2 long-poll loop in notification's infrastructure package, opt-in with `blocbuy.events.consumer.enabled` and scanned only by the monolith and notification-svc. `sys_consumed_message (event_id, consumer)` is V28. Unsupported `eventType`/`version` pairs are deleted without effect; an empty recipient list is marked consumed with a WARN and no notification. Concurrency is bounded by worker permits taken before each receive; shutdown does not interrupt the poller, waits a bounded time and then makes unfinished messages, and anything the last long poll returns, visible again. Details: [notification persistence](../design/notification-persistence.md#sqs-consumer-446).

### Addendum 2026-11-13: bounded producer-only SNS network concurrency (#459)

The producer experiment may overlap SNS `PublishBatch` network calls, not business aggregate transactions: `blocbuy.events.sns.publish-concurrency` accepts 1..8, defaults to 1, and does not change the SQS transport. Each call still carries at most ten entries and each relay tick still selects at most 100 rows. A transport-wide admission lock serializes overlapping `publish` invocations, so reused callers cannot multiply the cap; the parallel path admits only one wave of at most the configured concurrency to an explicitly bounded executor. Its per-invocation executor is joined and closed before returning, including exceptional exits; no shared/common executor or fire-and-forget provider work is used. The sequential default creates no executor.

Entry serialization remains isolated per event, provider exceptions remain isolated per ten-entry batch, and results retain input order and per-entry confirmation. A lease/close signal is checked before preparing a batch, before dispatch, and inside each worker immediately before provider I/O; after invalidation no further batch is admitted or sent once that signal is observed. Calls already in flight are joined without interruption and may finish after lease loss, so this remains cooperative cancellation rather than provider fencing. Unexpected worker failures fence later waves and all admitted work is joined before propagating the failure. SDK attempt/total budgets remain 5 s/15 s, below the renewable 120 s lease; consumer `eventId` deduplication and the existing outbox retry/recovery path remain mandatory.

`blocbuy.events.relay.interval` accepts 100 ms..60 s with the existing 2 s default and unchanged 2 s startup delay. A faster opt-in delay is necessary to remove the fixed-delay ceiling once network time improves; it does not promise a drain rate because database time, provider latency, retries and other jobs still contribute. No database/HTTP pool, lease, retry budget, IAM, queue, consumer, autoscaling policy or business-write concurrency changes are authorized by this addendum. Experimental settings (for example SNS concurrency 4 and a 250 ms delay) require isolated measurement and explicit operator enablement; they are not promoted defaults or evidence that notification scaling will pass. Local controlled-latency transport measurements exclude PostgreSQL and AWS; full aggregate verification and fresh deployment evidence are separate gates.

## Consequences

Business commits and event publication can no longer diverge: an event exists only if its transaction committed, and it is delivered at least once. Notifications lag commits by the relay interval, about 2 s.

The full path except the SNS hop is exercised by ElasticMQ integration tests in CI and by the local Docker Compose stack, at no AWS cost; the SWE5006 EC2 `dev` deployment keeps it disabled. The SNS hop, filter policies, IAM and alarms are verified only in a SWE5001 ECS session, so that session must include a fan-out check and a DLQ drill. DLQ inspection and redrive (`start-message-move-task`) follow the [notification DLQ runbook](../operations/notification-dlq-redrive.md).

Adding a consumer means adding a queue and a subscription; the producer does not change. Consumers scale on queue backlog per task. The relay is a single active instance under its lease; its throughput (about 50 events per second at 100 rows per 2 s tick) exceeds expected load, and the evolution path is lease sharding by `aggregate_id` or change data capture.

The stack gains the AWS SDK v2 (or Spring Cloud AWS if its Boot 4 line is available), two tables, one scheduled relay, one retention job and an ElasticMQ container for local development and CI. The backend-constraints skill and its messaging reference are amended to point here.

## Alternatives rejected

- **Outbox relayed directly to SQS, without SNS.** Simplest and fully emulated by ElasticMQ, but point-to-point: commerce would address notification's queue, and each new consumer would change the producer. It also removes the fan-out and filtering that the presentation needs to show.
- **One SNS topic per event type.** More resources and subscriptions for no benefit at this scale; filter policies on one topic give the same routing.
- **ElasticMQ hosted in AWS.** Not cheaper than SQS, in-memory by default, and emits no CloudWatch metrics for backlog autoscaling or DLQ alarms.
- **AWS Lambda consumers.** A second packaging and deployment path, and it bypasses the ECS backlog-per-task autoscaling to be demonstrated.
- **Redis Streams on the existing Valkey.** No new resource, but acknowledgement, retry, dead-lettering and scaling metrics would be hand-built, and Redis must not be the source of truth.
- **PostgreSQL polling by the consumer, or HTTP push from commerce.** Polling breaks per-service database ownership; push has no buffer, so bursts cannot drive consumer scaling.
- **EventBridge.** Neither cheaper nor simpler than SNS for one producer and two consumers.
- **FIFO topic and queues.** Notifications need no ordering; FIFO costs more and lowers throughput.
- **`AFTER_COMMIT` publish without an outbox.** Loses events when the process stops between commit and send.
