#import "lib-a.typ": *

== Receive Notifications <sec-a-notifications>

A buyer gets one in-app notification when a team they belong to succeeds or expires, or when their own payment succeeds, and can list their notifications newest first. The facts come from other use cases: UC4 (B10, the filling join, @sec-a-uc04), UC5 (success and expiry, @sec-a-uc05) and Pay upfront (the payment, @sec-a-pay-upfront). This part owns everything after the fact: the event record written with it, the relay to the message broker, the broker hop, the consumer in notification-svc and the read API. It is the only use case that crosses services by messages alone.

The use case is designed and built, but it runs only in the distributed topology with the event switches on (`blocbuy.events.enabled` and the relay switch on commerce-svc, `blocbuy.events.consumer.enabled` on notification-svc). The monolith used for development and the SWE5006 EC2 `dev` stack leave them off and run no broker. The whole path except the SNS hop is tested locally and in CI against ElasticMQ. No ECS session has yet been recorded with the relay on, so the SNS hop, the filter policy and dead-letter redrive on AWS are unverified (backend ADR 0007, NFR-59 to NFR-63, AISS-13; evidence in section 2.2). The web application has no notification screen; the API is the only reader.

=== Use Case and Flows of Events <sec-a-notifications-flows>

- *Actors.* Buyer (primary, the recipient). The event feed, a message channel between services, is a system-to-system boundary, not an actor.
- *Trigger.* A team succeeds or expires, or a payment succeeds, in another use case.
- *Services.* commerce-svc records and relays the event; notification-svc receives it, stores the notifications and serves the list.
- *Preconditions.* Notifications are switched on in the environment. For B7, the buyer is signed in (UC2, @sec-a-uc02).
- *Postconditions.* Each recipient has at most one notification per event, however often the event arrives. A failed or malformed event leaves no partial notifications. An event exists if and only if its business change was kept.

#atable(
  [Receive notifications: flows of events. B = basic flow, A = alternate flow, E = exception flow; "At" is the step where the flow branches off.],
  <tbl-a-ntf-flows>,
  columns: (0.35fr, 0.5fr, 5fr),
  header: ([Step], [At], [What happens]),
  [*B1*], [], [A team succeeds or expires, or a payment succeeds. Together with that change, the system records an event that names the fact and who must be told: the team's active members, or the payer.],
  [*B2*], [], [Within about two seconds, the system passes on the recorded events that are due, oldest first and at most 100 at a time, and marks each one passed on.],
  [*B3*], [], [The event feed delivers each event to the notification side, which reads it and checks its type, version and content.],
  [*B4*], [], [The system records that it has handled this event.],
  [*B5*], [], [The system creates one notification per recipient, with a fixed plain-text title and text that hold no names or amounts.],
  [*B6*], [], [The system confirms the event to the feed, which stops delivering it.],
  [*B7*], [], [The buyer asks for their notifications. The system returns only this buyer's notifications, newest first, one page at a time.],
  [*A1*], [B2], [Passing an event on fails. The system tries it again after a growing delay, from 2 seconds doubling up to 5 minutes. After the tenth failure it marks the event failed and alerts an operator, who can send it again after a fix. The other events of the batch are not affected.],
  [*A2*], [B2], [Only one relay passes events on at a time. If another holds the turn, this one does nothing. If it loses the turn midway, the events it has not sent wait for the next turn without counting as a failure.],
  [*A3*], [B3], [The same event arrives again: the system finds it already handled, writes nothing and confirms it.],
  [*A4*], [B3], [The event's type or version is not supported: the system logs it and confirms it without effect, so newer events never block delivery.],
  [*A5*], [B3], [The event is malformed: it is not confirmed and is delivered again. On the fifth delivery the feed sets it aside for an operator, who can send it again after a fix.],
  [*A6*], [B5], [The event names no recipient: the system records it as handled, creates no notification and logs a warning.],
  [*A7*], [B7], [The buyer is not signed in: the request is refused.],
  [*A8*], [B7], [The page number or page size is out of range: the request is refused.],
  [*E1*], [B4–B5], [A failure while recording: nothing of B4 and B5 is kept. The event is not confirmed and arrives again about a minute later; the delivery counts towards the five of A5.],
  [*E2*], [B1], [The event cannot be recorded: the business change itself is undone, so no fact is kept without its event.],
)

=== Analysis Class Diagram <sec-a-notifications-ac>

@fig-a-ntf-ac is the analysis model. The Buyer works through `NotificationListUI`. `EventFeed` is the boundary to the message channel: the relay hands events to it, and it delivers them to the receiving side. Two controls split the work at that boundary. `RelayEventsControl` records an event with the business fact and passes it on; `ReceiveNotificationsControl`, named after the use case, turns a delivered event into notifications and answers the buyer's list. The two controls talk to each other only through `EventFeed`.

`Team`, `Membership`, `Payment` and `Account` are the UC4 entities (@fig-a-uc04-ac), with only the attributes this use case reads. Three entities are new. `EventRecord` is the recorded fact with its delivery state (B1, B2, A1). `ConsumptionRecord` remembers that the receiving side handled an event (B4, A3). `Notification` is the message a recipient reads (B5, B7). `TeamStatus`, `PaymentStatus`, `MemberRole`, `AccountStatus`, `DeliveryStatus` and `NotificationType` are enumerations; the states of `DeliveryStatus` are drawn in @fig-a-ntf-st-outbox.

#afig("a-ntf-ac", [Receive notifications analysis class diagram (UML class notation, BCE stereotypes). The Buyer uses `NotificationListUI`; the two controls meet only through the system boundary `EventFeed`, and every notification is caused by exactly one `EventRecord`.])

=== Analysis Sequence Diagrams <sec-a-notifications-as>

The basic flow is drawn in two parts, split at the event feed. @fig-a-ntf-as-relay records the event with the business fact and passes it on, with A1. Its first lifeline stands for the control of the producing use case: `JoinTeamControl` in UC4, the resolving control of UC5 or the payment control of Pay upfront. @fig-a-ntf-as-receive receives the event and serves the list, with A3 to A7. A2, A8, E1 and E2 need design concepts (a lease, request validation, a transaction) and appear only in the design sequences.

#afig("a-ntf-as-relay", [Receive notifications analysis sequence diagram, basic flow part 1 (B1–B2) with A1. The event is created with the fact and names its recipients; a failed hand-over only counts a failure, and the event stays due.])

#afig("a-ntf-as-receive", [Receive notifications analysis sequence diagram, basic flow part 2 (B3–B7) with A3–A7. The consumption record is checked before any notification exists; only an unreadable event is left unacknowledged.])

=== Transition: Strategies and Mapping <sec-a-notifications-transition>

All nine strategies of @sec-a-strategies change this use case; S9 (@sec-a-s9) carries it.

- *S1 Language and framework (AD-01, AD-18; NFR-05, NFR-26):* impacted `EventRecord`, `ConsumptionRecord`, `Notification`, `EventFeed` → resultant records `OutboxEvent`, `OutboxPublishResult`, `Notification`, the sealed interface `NotificationEvent`, and the contracts `TeamSucceededV1`, `TeamExpiredV1`, `PaymentSucceededV1` in `IntegrationEventEnvelope<T>` (module `blocbuy-client`, shared by both services). The consumer is a plain AWS SDK v2 long-poll loop, because Spring Cloud AWS has no Spring Boot 4.1 line (ADR 0007).
- *S2 Presentation (AD-09; NFR-07, NFR-59):* impacted `NotificationListUI` → resultant `NotificationController` (`GET /api/v1/notifications`) returning `NotificationResponse` records. The web application has no screen for it (not drawn); dynamics @fig-a-ntf-ds-read.
- *S3 Distribution (AD-02, AD-09; NFR-05, NFR-64):* impacted `RelayEventsControl`, `ReceiveNotificationsControl`, `EventFeed` → resultant classes in two services: the writer and relay in commerce-svc, the consumer and read API in notification-svc. No call crosses between them; the ALB routes `/api/v1/notifications*` to notification-svc. Dynamics @fig-a-ntf-ds-relay, @fig-a-ntf-ds-consume.
- *S4 Persistence (AD-03, AD-11; NFR-13, NFR-59, NFR-60):* impacted `EventRecord`, `ConsumptionRecord`, `Notification` → resultant `sys_outbox` in the commerce database (`OutboxMapper`, `OutboxRelayRepository`), `sys_consumed_message` and `biz_notification` in the notification database (`ConsumedMessageRepository`, `NotificationRepository`). The associations from `ConsumptionRecord` and `Notification` to `EventRecord` cross two databases, so they become the copied value `eventId`, with unique keys `(event_id, consumer)` and `(event_id, user_id)`.
- *S5 External interface (AD-04; NFR-61, NFR-63):* impacted `EventFeed` → resultant outbound port `OutboxEventTransport` with `SnsOutboxEventTransport` and `SqsOutboxEventTransport`, and inbound `SqsNotificationEventConsumer` with `IntegrationEventDecoder`. Provider calls have a 5 s attempt and 15 s call timeout; the queue long-polls 20 s with a 60 s visibility timeout. Dynamics @fig-a-ntf-ds-relay, @fig-a-ntf-ds-consume.
- *S6 Security (AD-05, AD-09; NFR-59, NFR-60):* impacted `listNotifications(buyer, page)` → resultant `StpUtil.checkLogin()` and the user id from the shared session, never from the request; `Cache-Control: no-store`. Payloads carry ids only; the queue accepts messages only from the topic and is encrypted at rest. Dynamics @fig-a-ntf-ds-read.
- *S7 Scheduling (AD-16; NFR-61):* impacted `relayPending()` → resultant `OutboxRelayJob` (2 s fixed delay) under `LeasedJobRunner` with the lease `outbox-relay`, so exactly one commerce task relays (A2). The six-hourly retention job is not drawn. Dynamics @fig-a-ntf-ds-relay.
- *S8 Concurrency (AD-02, AD-03; NFR-59, NFR-61):* impacted `wasHandled` and the «create» of `ConsumptionRecord` → resultant `ConsumedMessageRepository.markConsumed`, one insert-if-absent on the primary key: a concurrent copy of the same event waits on the key, then finds it taken (A3). The marker and the notifications commit in one local transaction in the notification database, for the atomicity and single-database reasons of A.2 S8 (@sec-a-s8); no row lock and no lock order are needed. Worker permits bound the consumer; lease checks bound the relay. Dynamics @fig-a-ntf-ds-record.
- *S9 Domain and integration events (AD-04; NFR-59, NFR-60, NFR-61, NFR-63):* impacted `recordEvent`, `passOn`, `eventArrived`, `acknowledge`, `setAside` → resultant `IntegrationOutboxWriter` (`BEFORE_COMMIT`, lowest precedence), the relay, one SNS topic with a notification queue and its dead-letter queue (`maxReceiveCount` 5), the consumer and `NotificationEventHandler`. Dynamics @fig-a-ntf-ds-produce to @fig-a-ntf-ds-record.

@tbl-a-ntf-map shows where each analysis object lands. Every class on the four design class diagrams appears in it.

#atable(
  [Receive notifications analysis → design mapping. Every analysis object of @fig-a-ntf-ac, and every design class of @fig-a-ntf-dc-produce to @fig-a-ntf-dc-record.],
  <tbl-a-ntf-map>,
  columns: (1.15fr, 0.8fr, 3fr, 0.7fr, 0.9fr),
  header: ([Analysis object], [Stereotype], [Design elements (layer, service)], [Strategies], [Figures]),
  [`NotificationListUI`], [boundary], [Adapter: `NotificationController` with the nested record `NotificationResponse` (notification-svc). Frontend: none built (not drawn).], [S2, S6], [@fig-a-ntf-dc-record],
  [`EventFeed`], [boundary], [Outbound (commerce-svc): port `OutboxEventTransport`, abstract `BatchingOutboxEventTransport`, adapters `SnsOutboxEventTransport`, `SqsOutboxEventTransport`, converter `OutboxMessageFactory`. Broker: SNS topic `domain-events`, SQS queues `notification` and `notification-dlq` (Terraform resources, not classes). Inbound (notification-svc): `SqsNotificationEventConsumer` (settings in `NotificationConsumerProperties`, not drawn), converter `IntegrationEventDecoder` with `IntegrationEventDecoder.Decoded` and `MalformedIntegrationEventException`.], [S3, S5, S9], [@fig-a-ntf-dc-relay, @fig-a-ntf-dc-consume],
  [`RelayEventsControl` (record)], [control], [Entry point `IntegrationOutboxWriter.onTeamSucceeded`, `onTeamExpired`, `onPaymentSucceeded` (infrastructure, commerce-svc), inside the producer's transaction.], [S3, S9], [@fig-a-ntf-dc-produce],
  [`RelayEventsControl` (relay)], [control], [Entry point `OutboxRelayJob.run` (bootstrap) → `OutboxRelayService.relayBatch` (application, commerce-svc); `LeasedJobRunner` (S7, @sec-a-s7).], [S7, S9], [@fig-a-ntf-dc-relay],
  [`ReceiveNotificationsControl` (consume)], [control], [Entry point `SqsNotificationEventConsumer.process` → port `NotificationEventHandler` (domain) with `NotificationEventHandler.ConsumeOutcome`, implemented by `NotificationEventAppService` (application); converter `NotificationEventAssembler` (notification-svc).], [S8, S9], [@fig-a-ntf-dc-consume, @fig-a-ntf-dc-record],
  [`ReceiveNotificationsControl` (read)], [control], [Entry point `NotificationController.list` → `NotificationAppService.pageMine`; the same service's `recordBatch` serves the consume phase.], [S2, S6], [@fig-a-ntf-dc-record],
  [`Team`], [entity], [Read only, through the port `IntegrationRecipientRepository` (`IntegrationRecipientRepositoryImpl`, `BizGroupDealMapper`, not drawn); the `GroupDeal` aggregate is UC4's.], [S4], [@fig-a-ntf-dc-produce],
  [`Membership`], [entity], [`biz_group_member` rows read by `findActiveMemberIdsByDealId`.], [S4], [@fig-a-ntf-dc-produce],
  [`Payment`], [entity], [Read only: the payer is `CheckoutQuote.userId` on the domain event `PaymentSucceeded` (not drawn here); the `CheckoutPayment` aggregate is Pay upfront's.], [S9], [@fig-a-ntf-dc-produce],
  [`Account`], [entity], [Only a user id (`recipientUserIds`, `Notification.userId`); notification-svc never calls identity-svc.], [S3], [@fig-a-ntf-dc-produce, @fig-a-ntf-dc-record],
  [`EventRecord`], [entity], [Row `sys_outbox`, written as `OutboxInsert` through `OutboxMapper`, read as `OutboxEvent` and updated with `OutboxPublishResult` through `OutboxRelayRepository` (`OutboxRelayRepositoryImpl`, not drawn). Sent as `IntegrationEventEnvelope` with `TeamSucceededV1`, `TeamExpiredV1` or `PaymentSucceededV1`; received as `NotificationEvent` and its records `NotificationEvent.TeamSucceeded`, `NotificationEvent.TeamExpired`, `NotificationEvent.PaymentSucceeded`.], [S1, S4, S9], [@fig-a-ntf-dc-produce, @fig-a-ntf-dc-relay, @fig-a-ntf-dc-consume],
  [`ConsumptionRecord`], [entity], [Row `sys_consumed_message` through `ConsumedMessageRepository` (`ConsumedMessageRepositoryImpl`, `SysConsumedMessageMapper`, not drawn).], [S4, S8], [@fig-a-ntf-dc-record],
  [`Notification`], [entity], [Aggregate root `Notification` (immutable record, enumeration `NotificationType`), port `NotificationRepository`, adapter `NotificationRepositoryImpl`, mapper `BizNotificationMapper`, table `biz_notification`.], [S1, S4], [@fig-a-ntf-dc-record],
  [(none)], [—], [Introduced by S9, with no analysis counterpart: the domain events `TeamSucceeded`, `TeamExpired`, `PaymentSucceeded` (with the value object `CheckoutQuote`), the parameters of the writer, published by the producing use cases through `ApplicationEventPublisher`. Not drawn here; see @fig-a-uc04-dc-events.], [S9], [—],
)

=== Design Class Diagrams <sec-a-notifications-dc>

The design has 38 classes and interfaces in four slices along the event's path: the writer in commerce-svc (@fig-a-ntf-dc-produce), the relay and its transports in commerce-svc (@fig-a-ntf-dc-relay), the consumer and its anti-corruption layer in notification-svc (@fig-a-ntf-dc-consume), and the transaction that applies an event, with the read API, in notification-svc (@fig-a-ntf-dc-record). The only code the two services share is the contract module `blocbuy-client`. `#` marks a protected member, «static» a static operation and «final» an operation that subclasses cannot override. `SnsClient` and `SqsClient` are AWS SDK types, shown only as attributes. The persistence chain `NotificationRepository` → `NotificationRepositoryImpl` → `BizNotificationMapper` in @fig-a-ntf-dc-record is representative; every other port here is implemented the same way.

#afig("a-ntf-dc-produce", [Receive notifications design class diagram, slice 1: the outbox writer (UML, commerce-svc). `IntegrationOutboxWriter` maps each domain event to a versioned integration event that names its recipients, so the consumer never has to ask commerce whom to tell.])

#afig("a-ntf-dc-relay", [Receive notifications design class diagram, slice 2: the relay and its transports (UML, commerce-svc). `BatchingOutboxEventTransport` holds the final `publish` algorithm; the two adapters supply only `entry` and `send`.])

#afig("a-ntf-dc-consume", [Receive notifications design class diagram, slice 3: the SQS consumer and the anti-corruption layer (UML, notification-svc). `IntegrationEventDecoder` turns commerce's envelope into notification's own `NotificationEvent`; the consumer reaches the use case only through the port `NotificationEventHandler`.])

#afig("a-ntf-dc-record", [Receive notifications design class diagram, slice 4: applying an event once, and the read API (UML, notification-svc). `NotificationEventAppService` writes the consumption marker and the notifications through two ports in one transaction; the controller reads through the same `NotificationAppService`.])

=== Design Sequence Diagrams <sec-a-notifications-ds>

Each design sequence retells part of @tbl-a-ntf-flows; the step tags in the notes link the two. Listener callbacks and calls to the AWS APIs (`PublishBatch`, `ReceiveMessage`, `DeleteMessage`) are synchronous: the caller waits for the answer and uses it. The asynchronous messages are the topic's delivery to the queue and the queue's redrive to the dead-letter queue, which nobody waits for.

*Recording (B1, E2).* @fig-a-ntf-ds-produce shows the TeamExpired path, which no other part draws; UC4 shows the same writer for `TeamSucceeded` and `PaymentSucceeded` (@fig-a-uc04-ds-events). The writer runs last among the before-commit listeners, after the order and refund subscribers of UC5 and UC6, and resolves the recipients in the same transaction, so the list matches the committed team.

#afig("a-ntf-ds-produce", [Receive notifications design sequence diagram, B1 with E2: the outbox record (commerce-svc). The writer runs before commit inside the producer's transaction, so the event and the fact commit or roll back together.])

*Relay (B2–B3, A1, A2).* @fig-a-ntf-ds-relay is one relay run. No transaction spans a provider call: `findDue` and `recordResults` are single statements. The transport checks the lease before each slice of ten and again before sending it; a slice it does not send comes back as skipped and keeps its attempt count (A2). `recordResults` sets the next attempt of a failed row to now + min(300, 2#super[attempts]) seconds and marks the tenth failure failed (A1, @fig-a-ntf-st-outbox).

#afig("a-ntf-ds-relay", [Receive notifications design sequence diagram, B2–B3 with A1 and A2: one relay run (commerce-svc, SNS, SQS). The lease admits one relay at a time; the transport's template method sends slices of ten, and SNS fans each message out to the queue whose filter matches.])

*Consumption (B3–B6, A3–A5, E1).* @fig-a-ntf-ds-consume is the consumer loop. The poller receives only as many messages as it has free workers, so nothing waits in memory. A message is deleted only after the handler's transaction has committed, or when its type or version is unsupported. @fig-a-ntf-ds-record is the handler's transaction. The notifications are rendered before the first write, so an invalid one fails without touching the database. A duplicate stops at the marker. The SQS message id is never the key, because a relay retry or an SNS redelivery creates a new one.

#afig("a-ntf-ds-consume", [Receive notifications design sequence diagram, B3–B6 with A3, A4, A5 and E1: the SQS consumer (notification-svc). Only a committed result or an unsupported type deletes the message; anything else returns to the queue and, on the fifth receive, goes to the dead-letter queue.])

#afig("a-ntf-ds-record", [Receive notifications design sequence diagram, B4–B5 with A3, A6 and E1, inside `NotificationEventAppService.handle` (notification-svc). The consumption marker and the notifications share one transaction; a second copy of the event finds the marker and writes nothing.])

*Reading (B7, A7, A8).* @fig-a-ntf-ds-read shows the list. The owner comes from the session, and the page and the count are read in one repeatable-read snapshot, so a notification inserted between them cannot make the total disagree with the page.

#afig("a-ntf-ds-read", [Receive notifications design sequence diagram, B7 with A7 and A8: list my notifications (notification-svc). The request names no owner; the session decides whose notifications are read.])

=== Operation Specifications <sec-a-notifications-ops>

@tbl-a-ntf-ops specifies the seven operations that carry the rules of this use case. Signatures are those in the code.

#atable(
  [Receive notifications operation specifications. Errors in `code` form are `BizException` error codes unless named as another exception.],
  <tbl-a-ntf-ops>,
  columns: (1.25fr, 1fr, 1fr, 1.2fr, 1fr),
  header: ([Operation; transaction; service], [Purpose; parameters and return], [Preconditions], [Postconditions and effects], [Errors]),
  [`IntegrationOutboxWriter.onTeamExpired(event: TeamExpired): void`. `BEFORE_COMMIT`, lowest precedence, inside the producer's transaction; commerce-svc. `onTeamSucceeded` and `onPaymentSucceeded` are alike.], [Records the integration event of a fact (B1).], [`blocbuy.events.enabled`; the fact's transaction is committing.], [One `sys_outbox` row: type `TeamExpired`, version 1, aggregate = team id, payload `TeamExpiredV1` with the active member ids, and the trace id.], [`IllegalStateException` on serialisation; a database error on insert. Either rolls back the fact (E2).],
  [`OutboxRelayService.relayBatch(leaseValid: BooleanSupplier): int`. No transaction; under the job lease; commerce-svc.], [Relays one batch (B2). Returns the number of rows updated.], [Lease held; transport configured (`sns` or `sqs`), else startup fails.], [Each attempted row is published, or gets attempts + 1 and a retry delay, or is failed at the tenth attempt (ERROR log). Skipped rows are unchanged.], [None thrown: a transport exception becomes a failed result for every row of the batch.],
  [`BatchingOutboxEventTransport.publish(events: List<OutboxEvent>, leaseValid: BooleanSupplier): List<OutboxPublishResult>`, final. No transaction; commerce-svc.], [Sends events in slices of ten through `entry` and `send` (B2, A2). One result per event, in order.], [At most 100 events.], [Serialisation failures isolated per event, provider failures per slice; earlier confirmations kept; unattempted events `skipped`.], [None thrown; failures are results.],
  [`SqsNotificationEventConsumer.process(message: Message): void`. No transaction of its own; one worker thread; notification-svc.], [Handles one received message (B3, B6, A4, A5, E1).], [Message received with a 60 s visibility timeout.], [Deleted after `APPLIED` or `DUPLICATE`, or when unsupported; otherwise left for redelivery. Trace id restored for logs.], [None thrown: `MalformedIntegrationEventException` and every other exception are logged.],
  [`IntegrationEventDecoder.decode(body: String): IntegrationEventDecoder.Decoded`. Pure; notification-svc.], [Anti-corruption layer: envelope → `NotificationEvent` (B3).], [None.], [`Decoded` with an event for a supported type at version 1; without one (`supported()` false) for any other pair.], [`MalformedIntegrationEventException` when the body is not a JSON envelope or does not bind to its `*V1` contract (A5).],
  [`NotificationEventAppService.handle(consumer: String, event: NotificationEvent): NotificationEventHandler.ConsumeOutcome`. `REQUIRED`, `READ COMMITTED`, 10 s; notification-svc.], [Applies an event once for one consumer (B4, B5).], [`consumer` 1–64 characters; event decoded.], [`APPLIED`: marker and one notification per distinct recipient committed (none for A6). `DUPLICATE`: nothing written (A3).], [`NOTIFICATION_INVALID` (`NOTIFICATION_400_001`) for a bad consumer, event or rendered notification; any exception rolls back both (E1).],
  [`NotificationAppService.pageMine(query: PageQuery, userId: UUID): PageResult<Notification>`. Read-only, `REPEATABLE READ`, 3 s; notification-svc.], [Lists the buyer's notifications (B7).], [`userId` from the session; page ≥ 1, size 1–100 (checked by the controller).], [Newest first, id as tie-breaker; page and total from one snapshot.], [`NOTIFICATION_INVALID` if `userId` is null. At the controller: `UNAUTHENTICATED` (`COMMON_401_001`, A7), `VALIDATION_FAILED` (`COMMON_400_002`, A8).],
)

=== State Machines <sec-a-notifications-states>

The outbox event (a `sys_outbox` row, the analysis `EventRecord`) has a lifecycle, and this part owns it (@fig-a-ntf-st-outbox). Its states follow from two timestamps, as `OutboxMapper.recordResults` writes them: Pending (`published_at` and `failed_at` both empty), Published and Failed. A row skipped after a lost lease takes no transition, and a rolled-back business transaction leaves no row at all. The operator replay is the `UPDATE` of the outbox recovery runbook (`backend/docs/operations/outbox-relay-recovery.md`). `Notification` has no lifecycle: it is immutable and has no read flag. The SQS message's states (visible, in flight, in the dead-letter queue) belong to the broker.

#afig("a-ntf-st-outbox", [Outbox event statechart (UML state machine, commerce-svc). An event leaves Pending only by a confirmed send or by its tenth failure; Failed returns to Pending only by an operator's replay.])

=== Patterns Applied <sec-a-notifications-patterns>

Five patterns are in this code; @tbl-a-ntf-patterns maps their roles to classes. Only Template Method is a GoF pattern. The in-process publish–subscribe that starts the chain is described with UC4 (@sec-a-uc04-patterns).

#atable(
  [Patterns in the Receive notifications design: the problem first, then the roles mapped to real classes, then the consequences.],
  <tbl-a-ntf-patterns>,
  columns: (0.75fr, 1fr, 1.2fr, 1fr),
  header: ([Pattern (kind)], [Problem], [Roles → classes], [Consequences]),
  [Transactional Outbox (architectural)], [Publishing to SNS inside the business transaction could announce a fact that later rolls back; publishing after commit loses the event if the task stops in between (a dual write).], [Outbox writer: `IntegrationOutboxWriter`, through `OutboxMapper` into `sys_outbox`, in the producer's transaction. Relay: `OutboxRelayJob`, `OutboxRelayService`, `OutboxRelayRepository`, `OutboxEventTransport`. See @fig-a-ntf-ds-produce, @fig-a-ntf-ds-relay.], [An event exists if and only if its fact committed (NFR-60). Liabilities: delivery is at least once and about two seconds late (AISS-11); a single relay under a lease limits throughput to about 50 events a second.],
  [Template Method (GoF, behavioural)], [Two transports, SNS on AWS and SQS against ElasticMQ, must slice, isolate failures and stop on a lost lease identically; with two copies of the loop, a review fix had to be made twice (SWE5006 report, section 3.5.3).], [AbstractClass: `BatchingOutboxEventTransport<E>` with the final template method `publish`. Primitive operations: abstract `entry(event)` and `send(events, entries)`. ConcreteClasses: `SnsOutboxEventTransport`, `SqsOutboxEventTransport`. See @fig-a-ntf-dc-relay, @fig-a-ntf-ds-relay.], [One tested copy of the delivery rules; a new provider writes two methods. Liability: inheritance ties the subclasses to the base class's slice size and result rules.],
  [Publish–Subscribe Channel with Dead Letter Channel (enterprise integration patterns)], [Commerce must not know which services care about its facts, and one unreadable message must not block the rest.], [Channel: SNS topic `domain-events` with one SQS queue per subscriber and a filter policy on `eventType` (`notification`; `audit` takes the team events). Dead letter channel: `notification-dlq` after five receives, with an alarm. Competing consumers: the worker pool of `SqsNotificationEventConsumer` across one to four notification-svc tasks.], [A new consumer is a queue and a subscription, with no change to commerce (AD-04). Liabilities: no ordering, and filter policies take minutes to apply when the environment is built (AISS-06).],
  [Idempotent Receiver (enterprise integration pattern)], [At-least-once delivery: a relay retry, an SNS redelivery or a failed delete delivers the same event again.], [Receiver: `NotificationEventAppService`. Key: the envelope's `eventId` (never the SQS message id), recorded by `ConsumedMessageRepository.markConsumed` on the primary key of `sys_consumed_message`; second guard: `NotificationRepository.insertIfAbsent` on `(event_id, user_id)`. See @fig-a-ntf-ds-record.], [A duplicate writes nothing and is deleted (A3, NFR-59). Liability: the marker table grows with every event.],
  [Anti-Corruption Layer (DDD, architectural)], [Commerce's contract (`dealId`, the `operation` string) must not leak into the notification model, and a newer contract version must not break the consumer.], [Translator: `IntegrationEventDecoder` (tolerant read, then strict binding to the `*V1` contract). Notification's model: the sealed `NotificationEvent` with `teamId` and `startedTeam`. See @fig-a-ntf-dc-consume, @fig-a-ntf-ds-consume.], [Notification depends only on the contract module. Unknown pairs are acknowledged, so producers can evolve additively (A4). Liability: each new event needs a binder and a record in notification.],
)

=== Flow-to-Design Trace <sec-a-notifications-trace>

@tbl-a-ntf-trace shows that each step of @tbl-a-ntf-flows passes from an analysis message to design operations.

#atable(
  [Receive notifications flow-to-design trace: each flow step, its analysis message and the design operations that realise it.],
  <tbl-a-ntf-trace>,
  columns: (0.35fr, 1fr, 2fr, 0.8fr),
  header: ([Step], [Analysis message], [Design operations], [Figures]),
  [B1], [`recordEvent`, `activeMembers`, `payer`, «create» `EventRecord`], [`ApplicationEventPublisher.publishEvent`; `IntegrationOutboxWriter.onTeamExpired`, `onTeamSucceeded`, `onPaymentSucceeded` → `IntegrationRecipientRepository.findActiveMemberIdsByDealId` (or `CheckoutQuote.userId`); «create» `TeamExpiredV1`, `OutboxInsert`; `OutboxMapper.insert`], [@fig-a-ntf-ds-produce],
  [B2], [`relayPending`, `isDue`, `passOn`, `markPassedOn`], [`OutboxRelayJob.run` → `LeasedJobRunner.run` → `OutboxRelayService.relayBatch` → `OutboxRelayRepository.findDue`; `BatchingOutboxEventTransport.publish` → `entry`, `OutboxMessageFactory.body`, `attributes`, `send` (`PublishBatch`); `OutboxRelayRepository.recordResults`], [@fig-a-ntf-ds-relay],
  [B3], [`eventArrived`], [SNS delivery to the `notification` queue; `SqsNotificationEventConsumer.pollOnce` (`ReceiveMessage`), `process` → `IntegrationEventDecoder.decode` → `NotificationEventHandler.handle`], [@fig-a-ntf-ds-relay, @fig-a-ntf-ds-consume],
  [B4], [`wasHandled`, «create» `ConsumptionRecord`], [`ConsumedMessageRepository.markConsumed` returns true], [@fig-a-ntf-ds-record],
  [B5], [«create» `Notification`], [`NotificationEventAssembler.toNotifications` («create» `Notification`); `NotificationAppService.recordBatch` → `NotificationRepository.insertIfAbsent`], [@fig-a-ntf-ds-record],
  [B6], [`acknowledge`], [`DeleteMessage(receiptHandle)` after `APPLIED`], [@fig-a-ntf-ds-consume],
  [B7], [`listMine`, `listNotifications`, `forRecipient`, `showNotifications`], [`NotificationController.list` → `NotificationAppService.pageMine` → `NotificationRepositoryImpl.pageByUserId` → `BizNotificationMapper.selectPageByUserId`, `countByUserId`], [@fig-a-ntf-ds-read],
  [A1], [`recordFailure`], [Failed `PublishBatch` entry or exception → `OutboxPublishResult.failed`; `recordResults` sets the retry delay or `failed_at`], [@fig-a-ntf-ds-relay, @fig-a-ntf-st-outbox],
  [A2], [—], [`LeasedJobRunner.run` skips; `publish` stops when `leaseValid` is false and returns `skipped` results, which `relayBatch` leaves out of `recordResults`], [@fig-a-ntf-ds-relay],
  [A3], [`wasHandled` = yes, `acknowledge`], [`markConsumed` returns false → `DUPLICATE` → `DeleteMessage`], [@fig-a-ntf-ds-record, @fig-a-ntf-ds-consume],
  [A4], [`acknowledge`], [`decode` returns `Decoded` with `supported()` false → `DeleteMessage`], [@fig-a-ntf-ds-consume],
  [A5], [`setAside`], [`decode` throws `MalformedIntegrationEventException`; no delete; SQS redrive to `notification-dlq` on the fifth receive], [@fig-a-ntf-ds-consume],
  [A6], [loop with no recipient], [`toNotifications` returns an empty list; `markConsumed` true → `APPLIED` with a WARN, no `recordBatch`], [@fig-a-ntf-ds-record],
  [A7], [`showSignInRequired`], [`StpUtil.checkLogin` throws `NotLoginException` → `UNAUTHENTICATED` (401)], [@fig-a-ntf-ds-read],
  [A8], [—], [`@Min` and `@Max` on `page` and `size` → `VALIDATION_FAILED` (400)], [@fig-a-ntf-ds-read],
  [E1], [—], [An exception inside `handle` rolls back marker and notifications; `process` logs it and does not delete; redelivery after the visibility timeout], [@fig-a-ntf-ds-record, @fig-a-ntf-ds-consume],
  [E2], [—], [`IntegrationOutboxWriter` throws before commit; the producer's transaction rolls back], [@fig-a-ntf-ds-produce],
)
