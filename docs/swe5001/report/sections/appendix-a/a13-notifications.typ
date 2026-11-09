#import "lib-a.typ": *

== Receive Notifications <sec-a-notifications>

A buyer gets one in-app notification when a team they belong to succeeds or expires, or when their own payment succeeds, and can list their notifications newest first. The facts come from other use cases: UC4 (B10, the filling join, @sec-a-uc04), UC5 (success and expiry, @sec-a-uc05) and Pay upfront (the payment, @sec-a-pay-upfront). This part owns everything after the fact: the outbox row written with it, the relay to the message broker, the broker hop, the consumer in notification-svc and the read API. Its outbox writer is drawn only here; UC4 and UC5 show the published event and refer to @fig-a-ntf-ds-produce. It is the only use case that crosses services by messages alone.

The use case is designed and built, but it runs only in the distributed topology with the event switches on (`blocbuy.events.enabled` and the relay switch on commerce-svc, `blocbuy.events.consumer.enabled` on notification-svc). The monolith used for development and the SWE5006 EC2 `dev` stack leave them off and run no broker. The whole path except the SNS hop is tested locally and in CI against ElasticMQ. No ECS session has yet been recorded with the relay on, so the SNS hop, the filter policy and dead-letter redrive on AWS are unverified (backend ADR 0007, NFR-59 to NFR-63, AISS-13; evidence in section 2.2). The web application has no notification screen; the API is the only reader.

=== Use Case and Flows of Events <sec-a-notifications-flows>

- *Actors.* Buyer (primary, the recipient).
- *Trigger.* A team succeeds or expires, or a payment succeeds, in another use case.
- *Services.* commerce-svc notes the fact and passes it on; notification-svc creates the notifications and serves the list.
- *Preconditions.* Notifications are switched on in the environment. For B4, the buyer is signed in (UC2, @sec-a-uc02).
- *Postconditions.* Each recipient has at most one notification per fact. A malformed fact leaves no partial notifications. A fact is passed on if and only if its business change was kept.

#atable(
  [Receive notifications: flows of events. B = basic flow, A = alternate flow, E = exception flow; "At" is the step where the flow branches off. A6–A8 exist only because the design splits the use case across two services (S3, S7, S9) and are drawn only in the design sequences.],
  <tbl-a-ntf-flows>,
  columns: (0.35fr, 0.5fr, 5fr),
  header: ([Step], [At], [What happens]),
  [*B1*], [], [A team succeeds or expires, or a payment succeeds. Together with that change, the system notes the fact and who must be told: the team's active members, or the payer.],
  [*B2*], [], [Soon after, the system checks the kind and the content of the fact.],
  [*B3*], [], [The system creates one notification per recipient, with a fixed plain-text title and text that hold no names or amounts. A recipient never gets two notifications for one fact.],
  [*B4*], [], [The buyer asks for their notifications. The system returns only this buyer's notifications, newest first, one page at a time.],
  [*A1*], [B2], [The kind of fact is not one the system knows: it is logged and ignored, so new kinds of fact never hold back the others.],
  [*A2*], [B2], [The fact is malformed: no notification is created. After repeated attempts it is set aside for an operator, who can send it again after a fix.],
  [*A3*], [B3], [The fact names no recipient: it counts as handled, no notification is created and a warning is logged.],
  [*A4*], [B4], [The buyer is not signed in: the request is refused.],
  [*A5*], [B4], [The page number or page size is out of range: the request is refused.],
  [*A6*], [B2], [Design only (S9). Passing the fact on fails: it is tried again after a growing delay. After repeated failures it is marked failed and an operator is alerted, who can send it again. Other facts are not affected.],
  [*A7*], [B2], [Design only (S7). Only one relay passes facts on at a time. If another holds the turn, this one does nothing; facts it has not sent when it loses the turn wait for the next turn without counting as a failure.],
  [*A8*], [B2], [Design only (S9). The same fact arrives again: it is found already handled, and nothing is written.],
  [*E1*], [B1], [The fact cannot be noted: the business change itself is undone, so no change is kept without its fact.],
  [*E2*], [B3], [A failure while creating the notifications: none of them is kept, and the fact is tried again later; the attempt counts towards A2.],
)

=== Analysis Class Diagram <sec-a-notifications-ac>

@fig-a-ntf-ac is the analysis model: one system, with no broker and no services. The Buyer works through `NotificationListUI`. `ReceiveNotificationsControl`, named after the use case, is told each fact by the producing use case's control (`JoinTeamControl` in UC4, `ResolveTeamControl` in UC5, `PayUpfrontControl` in Pay upfront), turns it into notifications and answers the buyer's list. The fact is the parameter of `factHappened`, not an entity: it is the domain event that the producing use case raises, a design element of S9 (@tbl-a-ntf-map).

`Team`, `Membership`, `Payment`, `Account` and `Notification` are the names of the system-level model (@sec-a-reading-system), with only the attributes this use case reads. A notification is about one team or one payment and has one recipient. The business rule "at most one notification per fact and recipient" constrains the `recipient` association; S8 and S9 realise it. `forRecipient` is a class-scope query («static»). There is no relay control and no feed boundary: both exist only because S3 places the producer and the consumer in two services, so they appear in the design.

#afig("a-ntf-ac", [Receive notifications analysis class diagram (UML class notation, BCE stereotypes). `ReceiveNotificationsControl` alone turns a fact into notifications; each `Notification` has one recipient and is about one team or one payment, at most one per fact and recipient.])

=== Analysis Sequence Diagrams <sec-a-notifications-as>

@fig-a-ntf-as-receive draws the basic flow with A1 to A4 in one sequence: the producing control tells `ReceiveNotificationsControl` the fact, which finds the recipients and creates a notification for each one not yet told; later the buyer lists them. A5 (request validation) and E1, E2 (transactions) need design concepts, and A6 to A8 exist only in the design (S3, S7, S9); they appear in the design sequences.

#afig("a-ntf-as-receive", [Receive notifications analysis sequence diagram, B1–B4 with A1–A4. The recipients are found when the fact is noted; a recipient already told of the fact gets no second notification.])

=== Transition: Strategies and Mapping <sec-a-notifications-transition>

All nine strategies of @sec-a-strategies change this use case; S3 and S9 reshape it.

- *S1 Language and framework (AD-01, AD-18; NFR-05, 06, 26):* impacted `Notification` → resultant record `Notification` and contracts `TeamSucceededV1`, `TeamExpiredV1`, `PaymentSucceededV1` in `IntegrationEventEnvelope<T>` (`blocbuy-client`); a plain AWS SDK v2 consumer (ADR 0007).
- *S2 Presentation (AD-09; NFR-07, 12, 16, 25):* impacted `NotificationListUI` → resultant `NotificationController` (`GET /api/v1/notifications`), `NotificationResponse`; no web screen; dynamics @fig-a-ntf-ds-read.
- *S3 Distribution (AD-02, AD-06, AD-09; NFR-05, 07, 64, 66):* impacted `ReceiveNotificationsControl` → resultant writer and relay in commerce-svc, consumer and read API in notification-svc, joined only by the event feed (SNS topic, SQS queue); dynamics @fig-a-ntf-ds-relay, @fig-a-ntf-ds-consume.
- *S4 Persistence (AD-03, AD-11; NFR-05, 13):* impacted `Notification` → resultant `biz_notification` behind `NotificationRepository` → `NotificationRepositoryImpl` → `BizNotificationMapper`; the associations to `Team` and `Payment` become the copied `referenceId`, and the recipient the copied `userId`.
- *S5 External interface (backend ADRs 0001, 0004, 0005; NFR-21, NFR-22, NFR-23, NFR-31, NFR-50, NFR-66):* impacted `factHappened` across services → resultant port `OutboxEventTransport` (`SnsOutboxEventTransport`, `SqsOutboxEventTransport`) and inbound `SqsNotificationEventConsumer` with `IntegrationEventDecoder`; dynamics @fig-a-ntf-ds-relay, @fig-a-ntf-ds-consume.
- *S6 Security (AD-05, AD-09; NFR-01, 46, 47, 58, 68):* impacted `listNotifications(buyer, page)` → resultant `StpUtil.checkLogin()`, owner from the session, `Cache-Control: no-store`; payloads carry ids only; dynamics @fig-a-ntf-ds-read.
- *S7 Scheduling (AD-16; NFR-15, 32, 61):* impacted none in analysis → resultant `OutboxRelayJob` (2 s) under `LeasedJobRunner`, lease `outbox-relay`: at most one relay holds the lease, and a send already in flight may overlap a successor after lease loss, which the Idempotent Receiver absorbs (ADR 0007; A7); dynamics @fig-a-ntf-ds-relay.
- *S8 Concurrency (AD-02, AD-03; NFR-13, 21, 27, 30; also NFR-59):* impacted the rule "at most one per fact and recipient" → resultant `ConsumedMessageRepository.markConsumed` on `PRIMARY KEY (event_id, consumer)` (`sys_consumed_message`, one marker per consumer), committed with the notifications in one local transaction (A.2 S8, @sec-a-s8); dynamics @fig-a-ntf-ds-record.
- *S9 Domain and integration events (AD-04; NFR-30, 39, 59, 60, 61, 63):* impacted `factHappened(fact)` → resultant domain events, `IntegrationOutboxWriter` and the `sys_outbox` row with its delivery lifecycle (@fig-a-ntf-st-outbox), the relay, the topic, the queue and its dead-letter queue; dynamics @fig-a-ntf-ds-produce to @fig-a-ntf-ds-record.

@tbl-a-ntf-map shows where each analysis object lands. Every class on the four design class diagrams appears in it.

#atable(
  [Receive notifications analysis → design mapping. Every analysis object of @fig-a-ntf-ac, and every design class of @fig-a-ntf-dc-produce to @fig-a-ntf-dc-record. The "(none)" rows are the design mechanisms that S3, S8 and S9 introduce.],
  <tbl-a-ntf-map>,
  columns: (1fr, auto, 3.7fr, auto, 0.75fr),
  header: ([Analysis object], [Stereotype], [Design elements (layer, service)], [Strategies], [Figures]),
  [`NotificationListUI`], [boundary], [Adapter `NotificationController` with the record `NotificationResponse` (notification-svc); no frontend.], [S2, S6], [@fig-a-ntf-dc-record],
  [`ReceiveNotificationsControl` (record)], [control], [Entry point `IntegrationOutboxWriter.onTeamSucceeded`, `onTeamExpired`, `onPaymentSucceeded` (infrastructure, commerce-svc), in the producer's transaction.], [S3, S9], [@fig-a-ntf-dc-produce],
  [`ReceiveNotificationsControl` (pass on)], [control], [Entry point `OutboxRelayJob.run` (bootstrap; its callback `LeasedJobRunner.LeaseAwareWork` not drawn) → `OutboxRelayService.relayBatch` (application, commerce-svc).], [S3, S7, S9], [@fig-a-ntf-dc-relay],
  [`ReceiveNotificationsControl` (consume)], [control], [Entry point `SqsNotificationEventConsumer.process` → port `NotificationEventHandler` (`NotificationEventHandler.ConsumeOutcome`), implemented by `NotificationEventAppService`; `NotificationEventAssembler`; `NotificationAppService.recordBatch` (notification-svc).], [S3, S8, S9], [@fig-a-ntf-dc-consume, @fig-a-ntf-dc-record],
  [`ReceiveNotificationsControl` (read)], [control], [Entry point `NotificationController.list` → `NotificationAppService.pageMine` (notification-svc).], [S2, S6], [@fig-a-ntf-dc-record],
  [`Team`, `Membership`], [entity], [Read only: `IntegrationRecipientRepository.findActiveMemberIdsByDealId` (`biz_group_member`; implementation not drawn).], [S4], [@fig-a-ntf-dc-produce],
  [`Payment`], [entity], [Read only: the payer is `CheckoutQuote.userId` on the domain event `PaymentSucceeded` (not drawn).], [S9], [@fig-a-ntf-dc-produce],
  [`Account`], [entity], [Only a user id (`recipientUserIds`, `Notification.userId`); notification-svc never calls identity-svc.], [S3], [@fig-a-ntf-dc-produce, @fig-a-ntf-dc-record],
  [`Notification`], [entity], [Aggregate root `Notification` (immutable record), `NotificationRepository` → `NotificationRepositoryImpl` → `BizNotificationMapper`, table `biz_notification`.], [S1, S4], [@fig-a-ntf-dc-record],
  [(none): the fact], [—], [S9: domain events `TeamSucceeded`, `TeamExpired`, `PaymentSucceeded` (with `CheckoutQuote`), published by the producing use cases (not drawn). Outbox row: `OutboxInsert` via `OutboxMapper` into `sys_outbox`, read as `OutboxEvent`, updated with `OutboxPublishResult` via `OutboxRelayRepository`; sent as `IntegrationEventEnvelope` with the `*V1` contracts.], [S1, S4, S9], [@fig-a-ntf-dc-produce, @fig-a-ntf-dc-relay],
  [(none): the event feed], [—], [S3, S5: port `OutboxEventTransport`, abstract `BatchingOutboxEventTransport`, adapters `SnsOutboxEventTransport`, `SqsOutboxEventTransport`, `OutboxMessageFactory`; topic `domain-events`, queues `notification`, `notification-dlq` (Terraform). Inbound: `SqsNotificationEventConsumer`, `IntegrationEventDecoder` (`IntegrationEventDecoder.Decoded`, `MalformedIntegrationEventException`), the sealed `NotificationEvent` and its records.], [S3, S5, S9], [@fig-a-ntf-dc-relay, @fig-a-ntf-dc-consume],
  [(none): the consumption marker], [—], [S8, S9: `ConsumedMessageRepository` (`sys_consumed_message`, key `(event_id, consumer)`; implementation not drawn).], [S8, S9], [@fig-a-ntf-dc-record],
)

=== Design Class Diagrams <sec-a-notifications-dc>

The design has 38 classes and interfaces in four slices along the event's path: the writer (@fig-a-ntf-dc-produce) and the relay (@fig-a-ntf-dc-relay) in commerce-svc, the consumer (@fig-a-ntf-dc-consume) and the transaction that applies an event, with the read API (@fig-a-ntf-dc-record), in notification-svc. The services share only the contract module `blocbuy-client`. `#` marks a protected member, «static» a static member, «final» an operation that subclasses cannot override and «abstract» a primitive operation that each subclass supplies; the caption of @fig-a-ntf-dc-relay names the type each subclass binds to `E`. `SnsClient` and `SqsClient` are AWS SDK types, shown only as attributes. The persistence chain `NotificationRepository` → `NotificationRepositoryImpl` → `BizNotificationMapper` in @fig-a-ntf-dc-record is representative; every other port here is implemented the same way.

#afig("a-ntf-dc-produce", [Receive notifications design class diagram, slice 1: the outbox writer (UML, commerce-svc). `IntegrationOutboxWriter` maps each domain event to a versioned integration event that names its recipients, so the consumer never has to ask commerce whom to tell.])

#afig("a-ntf-dc-relay", [Receive notifications design class diagram, slice 2: the relay and its transports (UML, commerce-svc). `BatchingOutboxEventTransport` holds the final `publish` algorithm; the two adapters supply only the abstract `entry` and `send`, binding `E` to `PublishBatchRequestEntry` (SNS) and `SendMessageBatchRequestEntry` (SQS).])

#afig("a-ntf-dc-consume", [Receive notifications design class diagram, slice 3: the SQS consumer and the anti-corruption layer (UML, notification-svc). `IntegrationEventDecoder` turns commerce's envelope into notification's own `NotificationEvent`; the consumer reaches the use case only through the port `NotificationEventHandler`.])

#afig("a-ntf-dc-record", [Receive notifications design class diagram, slice 4: applying an event once, and the read API (UML, notification-svc). `NotificationEventAppService` writes the consumption marker and the notifications through two ports in one transaction; the controller reads through the same `NotificationAppService`.])

=== Design Sequence Diagrams <sec-a-notifications-ds>

Each design sequence retells part of @tbl-a-ntf-flows; the step tags in the notes link the two. Listener callbacks and calls to the AWS APIs (`PublishBatch`, `ReceiveMessage`, `DeleteMessage`) are synchronous: the caller waits for the answer and uses it. The asynchronous messages are the topic's delivery to the queue and the queue's redrive to the dead-letter queue, which nobody waits for.

*Recording (B1, E1).* @fig-a-ntf-ds-produce shows the TeamExpired path. The writer runs last among the before-commit listeners, after the order and refund subscribers of UC5 and UC6, and resolves the recipients in the same transaction, so the list matches the committed team. `StripeCheckoutService` publishes `PaymentSucceeded` before it checks whether the frozen terms have expired (`fulfilOrCompensate`, @sec-a-pay-upfront). A payment then refunded as `BUSINESS_EXPIRED` therefore still commits a `PaymentSucceededV1` row, and its payer is told the payment succeeded; the refund is UC6's (@sec-a-uc06). The event states a true fact (Stripe took the money), not that the purchase was fulfilled.

#afig("a-ntf-ds-produce", [Receive notifications design sequence diagram, B1 with E1: the outbox record (commerce-svc). The writer runs before commit inside the producer's transaction, so the event and the fact commit or roll back together.])

*Relay (B2, A6, A7).* @fig-a-ntf-ds-relay is one relay run. No transaction spans a provider call: `findDue` and `recordResults` are single statements. The transport checks the lease before each slice of ten and again before sending it; a slice it does not send comes back as skipped and keeps its attempt count (A7). SNS delivers to the queue as soon as it accepts the batch. `recordResults` sets the next attempt of a failed row to now + min(300, 2#super[attempts]) seconds and marks the tenth failure failed (A6, @fig-a-ntf-st-outbox).

#afig("a-ntf-ds-relay", [Receive notifications design sequence diagram, B2 with A6 and A7: one relay run (commerce-svc, SNS, SQS). The lease admits one relay at a time; the template method sends slices of ten, and SNS fans each accepted message out to the queue. The SNS hop is designed and unit-tested but not yet run on AWS (AISS-13); locally and in CI `SqsOutboxEventTransport` sends to ElasticMQ, which gives the consumer the same input.])

*Consumption (B2–B3, A1, A2, A8, E2).* @fig-a-ntf-ds-consume is the consumer loop. The poller receives only as many messages as it has free workers. A message is deleted only after the handler's transaction has committed, or when its type or version is unsupported. @fig-a-ntf-ds-record is the handler's transaction: the notifications are rendered before the first write, and a duplicate stops at the marker. The SQS message id is never the key, because a relay retry or an SNS redelivery creates a new one.

#afig("a-ntf-ds-consume", [Receive notifications design sequence diagram, B2–B3 with A1, A2, A8 and E2: the SQS consumer (notification-svc). Only a committed result or an unsupported type deletes the message; anything else returns to the queue and, on the fifth receive, goes to the dead-letter queue.])

#afig("a-ntf-ds-record", [Receive notifications design sequence diagram, B3 with A3, A8 and E2, inside `NotificationEventAppService.handle` (notification-svc). The consumption marker and the notifications share one transaction; a second copy of the event finds the marker and writes nothing.])

*Reading (B4, A4, A5).* @fig-a-ntf-ds-read shows the list. The owner comes from the session, and the page and the count are read in one repeatable-read snapshot, so a notification inserted between them cannot make the total disagree with the page.

#afig("a-ntf-ds-read", [Receive notifications design sequence diagram, B4 with A4 and A5: list my notifications (notification-svc). The request names no owner; the session decides whose notifications are read.])

=== Operation Specifications <sec-a-notifications-ops>

@tbl-a-ntf-ops specifies the seven operations that carry the rules of this use case. Signatures are those in the code. `BatchingOutboxEventTransport.publish` is specified by @tbl-a-ntf-patterns and @fig-a-ntf-ds-relay.

#atable(
  [Receive notifications operation specifications. Errors in `code` form are `BizException` error codes unless named as another exception.],
  <tbl-a-ntf-ops>,
  columns: (1.25fr, 1fr, 1fr, 1.2fr, 1fr),
  header: ([Operation; transaction; service], [Purpose; parameters and return], [Preconditions], [Postconditions and effects], [Errors]),
  [`IntegrationOutboxWriter.onTeamExpired(event: TeamExpired): void`. `BEFORE_COMMIT`, lowest precedence, producer's transaction; commerce-svc. `onTeamSucceeded`, `onPaymentSucceeded` alike.], [Records the integration event of a fact (B1).], [`blocbuy.events.enabled`; the fact's transaction is committing.], [One `sys_outbox` row: type `TeamExpired`, version 1, team id, payload `TeamExpiredV1` with the active member ids, trace id.], [`IllegalStateException` on serialisation; a database error on insert. Either rolls back the fact (E1).],
  [`OutboxRelayService.relayBatch(leaseValid: BooleanSupplier): int`. No transaction; under the job lease; commerce-svc.], [Relays one batch (B2). Returns the number of rows updated.], [Lease held; transport configured (`sns` or `sqs`), else startup fails.], [Each attempted row is published, retried later (attempts + 1) or failed at the tenth attempt. Skipped rows unchanged (A7).], [None: a transport exception fails every row of the batch (A6).],
  [`SqsNotificationEventConsumer.process(message: Message): void`. No transaction of its own; one worker thread; notification-svc.], [Handles one received message (B2, B3, A1, A2, E2).], [Message received with a 60 s visibility timeout.], [Deleted after `APPLIED`, `DUPLICATE` or an unsupported type; otherwise left for redelivery.], [None: every exception is logged.],
  [`IntegrationEventDecoder.decode(body: String): IntegrationEventDecoder.Decoded`. Pure; notification-svc.], [Anti-corruption layer: envelope → `NotificationEvent` (B2).], [None.], [`Decoded` with an event for a supported type at version 1, else `supported()` false (A1).], [`MalformedIntegrationEventException` if the body does not bind to its `*V1` contract (A2).],
  [`NotificationEventAppService.handle(consumer: String, event: NotificationEvent): NotificationEventHandler.ConsumeOutcome`. `REQUIRED`, `READ COMMITTED`, 10 s; notification-svc.], [Applies an event once for one consumer (B3).], [`consumer` 1–64 characters; event decoded.], [`APPLIED`: marker and one notification per recipient (none for A3). `DUPLICATE`: nothing written (A8).], [`NOTIFICATION_INVALID` (`NOTIFICATION_400_001`); any exception rolls back both (E2).],
  [`ConsumedMessageRepository.markConsumed(eventId: UUID, consumer: String): boolean`. Caller's transaction; notification-svc.], [Idempotent Receiver: records that `consumer` handled the event (B3). Returns `true` if this call inserted the marker.], [Inside `handle`'s transaction.], [Insert-if-absent on `PRIMARY KEY (event_id, consumer)`; a concurrent copy waits on the key, then gets `false` (A8).], [A database error rolls back `handle` (E2).],
  [`NotificationAppService.pageMine(query: PageQuery, userId: UUID): PageResult<Notification>`. Read-only, `REPEATABLE READ`, 3 s; notification-svc.], [Lists the buyer's notifications (B4).], [`userId` from the session; page ≥ 1, size 1–100 (checked by the controller).], [Newest first; page and total from one snapshot.], [`NOTIFICATION_INVALID` if `userId` is null. At the controller: `UNAUTHENTICATED` (`COMMON_401_001`, A4), `VALIDATION_FAILED` (`COMMON_400_002`, A5).],
)

=== State Machines <sec-a-notifications-states>

The outbox row (`sys_outbox`, introduced by S9) has a lifecycle, and this part owns it (@fig-a-ntf-st-outbox).

#afig("a-ntf-st-outbox", [Outbox event statechart (UML state machine, commerce-svc). «create» is the insert before commit (attempts = 0), kept only if the business transaction commits; an event leaves Pending only by a confirmed send or by its tenth failure, and Failed returns to Pending only by an operator's replay.])

Its states follow from two timestamps, as `OutboxMapper.recordResults` writes them: Pending (`published_at` and `failed_at` both empty), Published and Failed. The row is inserted before commit and exists only if the business transaction commits (E1). A row skipped after a lost lease takes no transition. The operator replay is the `UPDATE` of the outbox recovery runbook (`backend/docs/operations/outbox-relay-recovery.md`). `Notification` has no lifecycle: it is immutable and has no read flag. The SQS message's states belong to the broker.

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
  [Idempotent Receiver (enterprise integration pattern)], [At-least-once delivery: a relay retry, an SNS redelivery or a failed delete delivers the same event again.], [Receiver: `NotificationEventAppService`. Key: the envelope's `eventId` (never the SQS message id), recorded by `ConsumedMessageRepository.markConsumed` on the primary key of `sys_consumed_message`; second guard: `NotificationRepository.insertIfAbsent` on `(event_id, user_id)`. See @fig-a-ntf-ds-record.], [A duplicate writes nothing and is deleted (A8, NFR-59). Liability: the marker table grows with every event.],
  [Anti-Corruption Layer (DDD, architectural)], [Commerce's contract (`dealId`, the `operation` string) must not leak into the notification model, and a newer contract version must not break the consumer.], [Translator: `IntegrationEventDecoder` (tolerant read, then strict binding to the `*V1` contract). Notification's model: the sealed `NotificationEvent` with `teamId` and `startedTeam`. See @fig-a-ntf-dc-consume, @fig-a-ntf-ds-consume.], [Notification depends only on the contract module. Unknown pairs are acknowledged, so producers can evolve additively (A1). Liability: each new event needs a binder and a record in notification.],
)

=== Flow-to-Design Trace <sec-a-notifications-trace>

@tbl-a-ntf-trace maps each step of @tbl-a-ntf-flows to design operations; a design-only step has no analysis message.

#atable(
  [Receive notifications flow-to-design trace: each flow step, its analysis message and the design operations that realise it.],
  <tbl-a-ntf-trace>,
  columns: (0.35fr, 1fr, 2fr, 0.8fr),
  header: ([Step], [Analysis message], [Design operations], [Figures]),
  [B1], [`factHappened`, `activeMembers`, `payer`], [`publishEvent`; `IntegrationOutboxWriter.onTeamExpired` (or `onTeamSucceeded`, `onPaymentSucceeded`) → `IntegrationRecipientRepository.findActiveMemberIdsByDealId` (or `CheckoutQuote.userId`); «create» `TeamExpiredV1`, `OutboxInsert`; `OutboxMapper.insert`], [@fig-a-ntf-ds-produce],
  [B2], [guard: kind known, content valid], [`OutboxRelayJob.run` → `LeasedJobRunner.run` → `OutboxRelayService.relayBatch` → `findDue`, `BatchingOutboxEventTransport.publish` (`entry`, `OutboxMessageFactory.body`, `attributes`, `send`: `PublishBatch`), `recordResults`; SNS delivery; `SqsNotificationEventConsumer.pollOnce` (`ReceiveMessage`), `process` → `IntegrationEventDecoder.decode`], [@fig-a-ntf-ds-relay, @fig-a-ntf-ds-consume],
  [B3], [«create» `Notification`], [`NotificationEventHandler.handle` → `NotificationEventAssembler.toNotifications` («create» `Notification`), `ConsumedMessageRepository.markConsumed` true, `NotificationAppService.recordBatch` → `NotificationRepository.insertIfAbsent`; then `DeleteMessage`], [@fig-a-ntf-ds-record, @fig-a-ntf-ds-consume],
  [B4], [`listMine`, `listNotifications`, `forRecipient`, `showNotifications`], [`NotificationController.list` → `NotificationAppService.pageMine` → `NotificationRepositoryImpl.pageByUserId` → `BizNotificationMapper.selectPageByUserId`, `countByUserId`], [@fig-a-ntf-ds-read],
  [A1], [guard: kind not known], [`decode` returns `Decoded` with `supported()` false → `DeleteMessage`], [@fig-a-ntf-ds-consume],
  [A2], [guard: malformed], [`decode` throws `MalformedIntegrationEventException`; no delete; SQS redrive to `notification-dlq` on the fifth receive], [@fig-a-ntf-ds-consume],
  [A3], [loop with no recipient], [`toNotifications` returns an empty list; `markConsumed` true → `APPLIED` with a WARN, no `recordBatch`], [@fig-a-ntf-ds-record],
  [A4], [`showSignInRequired`], [`StpUtil.checkLogin` throws `NotLoginException` → `UNAUTHENTICATED` (401)], [@fig-a-ntf-ds-read],
  [A5], [—], [`@Min` and `@Max` on `page` and `size` → `VALIDATION_FAILED` (400)], [@fig-a-ntf-ds-read],
  [A6], [— (S9)], [Failed `PublishBatch` entry or exception → `OutboxPublishResult.failed`; `recordResults` sets the retry delay or `failed_at`], [@fig-a-ntf-ds-relay, @fig-a-ntf-st-outbox],
  [A7], [— (S7)], [`LeasedJobRunner.run` skips; `publish` returns `skipped` results once `leaseValid` is false; `relayBatch` leaves them out of `recordResults`], [@fig-a-ntf-ds-relay],
  [A8], [— (S9; analysis guard "not yet told")], [`markConsumed` returns false → `DUPLICATE` → `DeleteMessage`], [@fig-a-ntf-ds-record, @fig-a-ntf-ds-consume],
  [E1], [—], [`IntegrationOutboxWriter` throws before commit; the producer's transaction rolls back], [@fig-a-ntf-ds-produce],
  [E2], [—], [An exception in `handle` rolls back marker and notifications; `process` does not delete; redelivery after 60 s], [@fig-a-ntf-ds-record, @fig-a-ntf-ds-consume],
)
