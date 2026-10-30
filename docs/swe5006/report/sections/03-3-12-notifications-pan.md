<!-- report
rubric: 3.3.12
owner: Panpakorn Siripanich (Pan)
plan: #677
sources: SWE5001 scope; ADR 0007; backend/docs/design/notification-persistence.md; IntegrationOutboxWriter, OutboxRelayService, SqsNotificationEventConsumer, NotificationEventAppService, NotificationController; application-ec2.yml; backend/docs/operations/notification-dlq-redrive.md; issues #215, #446
owns: none
status: draft
-->

### 3.3.12 Receive Notifications

A buyer receives one in-app notification when a team they belong to succeeds or expires, or when their payment succeeds, and can list their notifications newest first. The use case is coded but switched off (off on dev, section 1.4); we describe it so that the use case model is complete, and section 3.4 has no models for it. It stays in-app, outside the seven exclusions of section 1.4.2, and the web application has no notification screen.

**Actors:** Buyer (primary; the recipient).

**Preconditions:** Notifications are switched on in the environment.

**Trigger:** A team succeeds or expires (UC5), or a payment succeeds (section 3.3.11).

**Normal flow:**

1. Together with the business change, the system records an event that names who must be told: the team's members, or the payer. The event exists only if the business change does, because it is recorded through a transactional outbox (section 3.2.9).
2. Later, the system passes the event on for delivery.
3. The system receives the event and checks its type, version and content.
4. The system records that it has handled this event and creates one notification per recipient, with a fixed plain-text title and text that hold no names or amounts.
5. The new notifications are now available to their recipients.
6. The buyer asks for their notifications. The system returns only the signed-in buyer's notifications, newest first, one page at a time.

**Exceptional flows:**

- 2a. Passing the event on fails: the system tries again with growing delays; after repeated failures it marks the event failed and reports it to an operator.
- 3a. The same event arrives again: the system finds that it has already handled it and writes nothing.
- 3b. The event type or version is not supported: the system logs it, skips it and writes nothing, so that newer events do not block delivery.
- 3c. The event is malformed: after repeated failed attempts it is set aside for an operator, who can send it again after a fix.
- 3d. The event names no recipient: the system records it as handled and writes no notification.
- 4a. A failure happens during step 4: nothing of step 4 is kept, and the event arrives again later.
- 6a. The buyer is not signed in: the request is refused.

**Postconditions:** Each recipient has at most one notification per event, however often the event arrives. A failed or malformed event leaves no partial notifications.

**Analysis objects:** notification list (read operation) and message queue «boundary»; event relay control and notification consumer control «control»; outbox event, consumption record and notification «entity».
