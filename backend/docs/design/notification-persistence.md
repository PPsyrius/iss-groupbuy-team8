# Notification persistence and read API (#215)

Date: 2026-10-13. Implements the agreed, reduced scope in [#215's October 11 comments](https://github.com/PPsyrius/iss-groupbuy-team8/issues/215#issuecomment-6110253432), under [ADR 0007](../adr/0007-cross-service-events-sns-sqs.md).

## Use case and boundaries

One immutable in-app notification per recipient for `TeamSucceeded`, `TeamExpired` or `PaymentSucceeded`. Commerce resolves recipients; #446 validates the integration envelope and renders bounded plain-text title/content. Team events reference a team ID; payment events reference a payment ID. This issue adds no listener, publisher, unread state, mark-as-read, external delivery or frontend bell.

`Notification` owns non-null identities/type/time and non-blank title/content limits (128/1024 Java characters). `NotificationAppService.recordBatch` accepts 1–1000 validated effects, generates no remote calls and performs one SQL batch inside a REQUIRED local transaction. The caller supplies UUIDv7 row IDs (`UuidV7.next()`), the envelope `eventId` and server creation time, never client-controlled identity or text.

`NotificationRepository.insertIfAbsent` retains the first effect for `(event_id, user_id)`; replay is a no-op even if the incoming row ID differs. The unique key includes soft-deleted rows, so replay never resurrects deleted effects. There is no mutable command or optimistic version in this scope. #446 must insert its `(eventId, consumer)` marker and these effects in the same transaction and acknowledge only after commit; the repository uniqueness is a defensive second boundary, not a replacement for consumer idempotency.

## HTTP contract

`GET /api/v1/notifications?page=1&size=20` requires a Sa-Token login and returns a `Result<PageResult<NotificationResponse>>`. Size is 1–100, page is at least 1, order is `create_time DESC, id DESC`; only the session recipient's active rows are queried. Each item exposes `id`, `type`, `referenceId`, `title`, `content`, `createTime`, never recipient IDs, event IDs or audit actors. Responses use `Cache-Control: no-store`. Render title/content as plain text, not HTML.

Page and count run in one read-only REPEATABLE READ transaction (3 s timeout). Offset paging matches existing APIs and can shift across requests when notifications arrive; it is not a cursor or a stable multi-request snapshot. There are no object lookup or write endpoints.

## Schema and rollout

**Prerequisite update (2026-10-13): local merge-readiness hold cleared.** PR #473 merged as `22536866` and is already in this branch's base. Its [dev delivery run](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/38336577751) passed SSM deployment, EC2-local and public health checks for the V26-bearing artifact; deployed Flyway history was not directly inspected. Final full Docker-backed verification passed (1747 tests, zero failures/errors, one skipped), including `NotificationMigrationIT`: V26→V27 preserves an existing outbox fact, validates checksums, permits notification writes and repeats migration without changes. Keep V27; CI/review still gate merge, and deployment to any other database requires V26 first. No global Flyway out-of-order setting or cross-PR renumbering is authorized.

V27 creates `biz_notification` with UUIDv7 default, standard audit/soft-delete columns, event-recipient uniqueness and query-driven indexes; [canonical SQL](../db/notification.sql). No foreign keys or business CHECK constraints; the producer/consumer validates references. System writes leave audit actors null. No retention job is added.

Final scope review (2026-10-13): retain only the recipient/time paging index and structural primary/unique indexes; there is no reference-ID lookup in #215. Regressions passed for the actual notification-svc HTTP assembly with a restricted database role and Redis sessions, a committed insert between page/count queries, and a database-failing batch with no partial effects and successful retry. Final full verification covers these additions and the index removal. V27 is not yet merged or deployed, so its migration and canonical DDL are updated together.

V26 is allocated to the outbox in #444 / PR #473. Merge and migrate V26 before deploying V27; otherwise Flyway's default out-of-order policy will reject the later arrival of V26. Development of #215 is independent of #467, but notification-svc's session authorization still requires it. ADR 0008's grants already assign `biz_notification` to notification; commerce/identity do not gain access.
