<!-- report
rubric: 3.3.12
owner: Panpakorn Siripanich (Pan)
plan: #677
sources: SWE5001 scope; ADR 0007; backend/docs/design/notification-persistence.md; IntegrationOutboxWriter, OutboxRelayService, SqsNotificationEventConsumer, NotificationEventAppService, NotificationController; application-ec2.yml; backend/docs/operations/notification-dlq-redrive.md; issues #215, #446
status: draft
-->

### 3.3.12 Receive Notifications

A buyer receives one in-app notification when a team they belong to succeeds or expires, or when their payment succeeds, and can list their notifications newest first. This use case is coded but switched off on the SWE5006 dev system: it belongs to the SWE5001 course, whose distributed deployment runs the message broker and the notification consumer it needs (backend ADR 0007). It is described here so that the use case model is complete; Section 3.4 has no models for it.

**Actors:** Buyer (primary; the recipient).

**Preconditions:** Event publishing, the relay and the notification consumer are enabled in the environment, and a message topic and queue exist. On the SWE5006 dev system all three switches are off and no broker runs, so none of the steps below happens there.

**Trigger:** A team succeeds or expires (UC5), or a payment succeeds (Section 3.3.11).

**Normal flow:**

1. In the same transaction as the business change, the system resolves who must be told (the team's members, or the payer) and writes a versioned event that names them to an outbox table. The event exists if and only if the business change commits.
2. A scheduled relay reads unpublished outbox events in batches, publishes them to the message topic, and marks them published.
3. The topic delivers each event to the notification queue; the notification consumer receives it and checks its type, version and content.
4. In one transaction, the consumer records that it has consumed this event and creates one notification per recipient, with a fixed plain-text title and text that hold no names or amounts.
5. After the commit, the consumer deletes the message from the queue.
6. The buyer asks for their notifications. The system returns only the signed-in buyer's notifications, newest first, one page at a time.

**Exceptional flows:**

- 2a. Publishing fails: the event stays unpublished and is retried with back-off; after ten failed attempts it is marked failed and logged as an error for an operator.
- 3a. The same event is delivered again (the queue delivers at least once): the consumer finds its consumption record, writes nothing and deletes the message.
- 3b. The event type or version is not supported: the consumer logs it, deletes the message and writes nothing, so that newer producers do not block it.
- 3c. The message is malformed: the consumer leaves it on the queue. After five failed receives the queue moves it to a dead-letter queue, from which an operator can redrive it after a fix.
- 3d. The event names no recipient: the consumer records it as consumed and writes no notification.
- 4a. The database fails during step 4: the transaction rolls back both the consumption record and the notifications; the message is not deleted and is received again after its visibility timeout.
- 6a. The request has no session: HTTP 401.

**Postconditions:** Each recipient has at most one notification per event, however often the event is delivered. A failed or malformed event leaves no partial notifications.

Evidence: tested locally against a local SQS emulator (ElasticMQ) in integration tests, not on dev: an event sent three times creates one notification per recipient, and a malformed event is received five times and then found in the dead-letter queue. The SWE5001 ECS environment (not the SWE5006 dev host) is configured to enable the relay and the consumer; that is context for SWE5001, not a SWE5006 result. The proposal excluded e-mail, SMS and push notifications "beyond in-app status"; this use case stays in-app, and the web application has no notification screen.

**Analysis objects:** notification list (read operation) and message queue «boundary»; event relay control and notification consumer control «control»; outbox event, consumption record and notification «entity».
