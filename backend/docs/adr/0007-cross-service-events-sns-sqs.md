# 0007. Cross-service events go through a transactional outbox, one SNS topic and an SQS queue per consumer

Date: 2026-10-11

## Status

Proposed (#443, parent #450). Becomes Accepted after team agreement at stand-up. Amends the "no message broker" rule of 2026-08-30 for messaging only; RabbitMQ and Elasticsearch stay out of the stack.

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

## Consequences

Business commits and event publication can no longer diverge: an event exists only if its transaction committed, and it is delivered at least once. Notifications lag commits by the relay interval, about 2 s.

The full path except the SNS hop is exercised by ElasticMQ integration tests in CI and by every dev deployment, at no AWS cost. The SNS hop, filter policies, IAM and alarms are verified only in a SWE5001 ECS session, so that session must include a fan-out check and a DLQ drill.

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
