# 0002. A team's resolution is published, and its consequences run before the commit

Date: 2026-09-22

## Status

Accepted

## Context

Until this change the team lifecycle had no end. `biz_group_deal.status` could reach `succeeded` through a join that filled the team, but nothing ever wrote `expired`: production code only read that value. `OrderRepository.confirmByDealId` and `cancelByDealId` had been implemented and tested since UC10 with **zero callers**, and `StripeCheckoutService.recordExpiredTeam` — which turns an expired team into refund obligations — had none either. A buyer could pay, get an order, and watch it sit in `pending` forever whatever the team did.

Closing that loop means groupbuy's decision has to reach two other domains. The hard gate forbids reaching into another module's aggregates, mappers or repositories, and UC5 is assessed on the state and observer patterns specifically, so an inline call from `PaidGroupDealService` to `OrderRepository` was never an option — it would be both a boundary violation and the wrong shape.

Two smaller questions came with it: where the legal transitions live, and when the subscribers run.

## Decision

**Transitions live in `DealStatus.canTransitionTo`, and only there.** Only `OPEN` may move, never to itself, and terminal states never re-enter — structurally identical to `OrderStatus`. `GroupDeal` gains `expire(Instant now)`, guarded by the matrix rather than by an `if` on the status, and `joinPaid`'s existing open-deal check is routed through the same matrix so there is one source of truth. Services never branch on status.

The aggregate's two guards fail differently on purpose. A deal that is already terminal raises `BizException(DEAL_CLOSED)` — another writer resolved it first, which the caller swallows as a replay. A deal that is not due yet raises `IllegalStateException` — the sweeper's own due-list query is wrong, and hiding that would hide a bug that silently expires live teams.

**groupbuy publishes; order and payment subscribe.** `TeamSucceeded(dealId, productId, size, at)` and `TeamExpired(dealId, productId, at)` are plain JDK records in `com.blocbuy.groupbuy.event`, carrying no annotations, published through Spring's `ApplicationEventPublisher` from the application layer. `OrderLifecycleSubscriber` (order) confirms or cancels that team's orders; `TeamExpiryRefundSubscriber` (payment), wired unconditionally by `PaymentIntegrationConfiguration`, delegates to the profile-independent `TeamExpiryRefundService` to record refund obligations. A subscriber consumes only the immutable record — never the `GroupDeal` aggregate, a mapper, a DO or a repository implementation — so this is a published contract, not a boundary breach.

**Subscribers run at `TransactionPhase.BEFORE_COMMIT`, ordered order(10) → payment(20).**

## Consequences

### Why not `AFTER_COMMIT`

`recordExpiredTeam`'s Javadoc says it verbatim: "Run inside the caller's team-expiry transaction; never AFTER_COMMIT." The team's closure and the money owed for it are one fact. Recorded after the commit, there is a window in which the team is expired and nobody is owed anything, and a failure to record leaves that state permanently — nothing would undo the expiry. `BEFORE_COMMIT` is synchronous, on the same connection, and a subscriber that throws rolls the whole resolution back.

### Why not a plain `@EventListener`

This is the easiest thing here to get wrong. `TeamSucceeded` is published from `joinConfirmed`, which `OrderFulfilmentAdapter.fulfil` calls through `admit(...)` — and **that buyer's own order is saved by `orders.save(...)` only after `admit` returns**. A plain listener fires immediately, so `confirmByDealId` would sweep the team before the last order row exists, leaving the order of the buyer who completed the team pending forever. `BEFORE_COMMIT` defers until every write in the transaction is done, so the ordering is correct by construction rather than by luck.

A consequence worth stating: `@TransactionalEventListener` silently discards events published outside a transaction. Both publishing sites (`joinConfirmed`, `GroupDealExpiryService.expireOne`) are `@Transactional`, and the integration tests assert on the resulting rows — orders really are `confirmed`, a refund row really exists — rather than on the listener having been called, because only the rows can tell the difference.

### Lock order and concurrency

The subscribers' `@Order` follows the global lock order team → intent → order → payment, so no combination of the two can take locks in opposite directions.

- **Sweeper against a settling webhook.** Both lock `biz_group_deal` first, so the order matches and they cannot deadlock. If the sweeper wins, the team is `expired` and the webhook settling afterwards reads `expired`, yields `BUSINESS_EXPIRED` and records a refund obligation — nobody is charged for a team they never joined. If the webhook wins, the team may be `succeeded`, and the sweeper's status-guarded UPDATE (`WHERE id = ? AND status = 'open' AND deleted = false`) affects zero rows; `PaidGroupDealRepository.expire` reports that as `DEAL_CLOSED` and `expireOne` returns false.
- **Refund obligations cannot be duplicated.** `recordExpiredTeam` locks refundable payments in deterministic order and performs one set-based upsert. The worker only claims, and `UNIQUE (payment_id)` on `biz_refund` (V9) is the final line of defence, with the `version` fencing token on the refund row behind it. Soft-deleted or mismatched conflicts fail safely.

### One aggregate, one transaction

`GroupDealExpiryService.expireOne` resolves exactly one team per call and holds no loop. The batch sweep over many teams belongs to the scheduler that is not in this change; keeping the loop out of this class keeps the lock on a single row and keeps the class unit-testable without a database.

## Alternatives rejected

| Alternative | Why not |
| --- | --- |
| `@TransactionalEventListener(AFTER_COMMIT)` | `recordExpiredTeam` forbids it, and it leaves a window where a team is expired and no refund is owed |
| A plain `@EventListener` | The last order of a completed team is not inserted yet, so `confirmByDealId` would miss it |
| groupbuy calling `OrderRepository` / payment orchestration directly | Cross-domain hard gate, and UC5 requires the consequences to be carried by subscribers |
| One transaction expiring a whole batch of teams | One team's failure rolls back the batch, and the lock hold time starts contending with webhook settlement |
| A message broker between the domains | Not in the stack (locked decision, 2026-08-30); in-process publication inside one local transaction is what the consistency rule asks for |

## Addendum (2026-09-23): the scheduled sweep (#28)

The scheduler that the section "One aggregate, one transaction" left out now exists. Plan: `docs/agile/sprint-02/team-expiry-sweeper-plan-20260923.md`.

**Shape.** `TeamExpirySweepJob` (bootstrap) fires on a fixed delay, default one minute, and hands the work to `LeasedJobRunner`, which takes and renews the database lease or skips the run. `GroupDealExpirySweeper` (application) reads up to 100 eligible team ids in one query on the database clock, then calls `expireOne` once per team. The sweeper is deliberately not `@Transactional`: every team commits or rolls back on its own. A failed team receives five minutes of durable backoff in `sys_scheduled_item_retry` (V17), so a poisoned oldest row cannot monopolize every bounded batch.

**Master switch.** `blocbuy.scheduling.enabled` is off by default in every profile. A deployment sets `BLOCBUY_SCHEDULING_ENABLED=true` only after counting its overdue open teams. Test and `openapi-export` contexts therefore remain free of accidental job execution and database races.

**Lease: a single upsert with a statement timeout, not V13's `SELECT ... FOR UPDATE NOWAIT`.** V13 cannot be edited (Flyway checksum), so this deviation is recorded here:

```sql
INSERT INTO sys_scheduler_lock (lock_key, owner, locked_until)
VALUES (?, ?, clock_timestamp() + make_interval(secs => ?))
ON CONFLICT (lock_key) WHERE deleted = false
DO UPDATE SET owner = EXCLUDED.owner,
              locked_until = clock_timestamp() + make_interval(secs => ?), ...
WHERE sys_scheduler_lock.locked_until <= clock_timestamp() OR sys_scheduler_lock.owner = EXCLUDED.owner
```

Both designs are leases. The row lock lasts only as long as the acquire statement, and `locked_until` is what keeps other instances out for the length of a run. NOWAIT's fail-fast therefore only covers a millisecond race, and in that race both designs end the same way: the loser skips.

The upsert meets every V13 obligation:

- no queueing behind a sweep;
- a stale lease is taken over under the row lock;
- there is one row per key.

It does so in one autocommit statement. The "expired or ours" decision is made atomically in SQL, and the update calculates its deadline only after obtaining the conflicting row lock. There is no `55P03` mapping.

NOWAIT's one real advantage is returning at once when a session holds the row lock for a long time. `timeout="3"` on both lease statements recovers it: PostgreSQL cancels the statement (`57014`), the run counts as `failed`, and the scheduler thread is never stuck. `SchedulerLeaseRepositoryIT` proves the single winner under an eight-way race and the timeout against a held `FOR UPDATE`.

**Renewal and overlap.** `LeasedJobRunner` renews a still-live owned lease every third of its duration. If renewal fails, the current item may finish but the sweeper starts no further item and the run is counted failed. A takeover can still overlap that one in-flight item, so the status-guarded UPDATE and `UNIQUE (payment_id)` on `biz_refund` remain the correctness backstops.

**Observability.** Each run carries its own `traceId` (`job-<uuidv7>`) in MDC. The job writes one INFO summary per non-empty run (`due`, `expired`, `alreadyResolved`, `failed`, `tookMs`); idle runs log at DEBUG. It also records these meters:

- `blocbuy.scheduler.runs{job,outcome}`;
- `blocbuy.scheduler.run.duration{job}`;
- `blocbuy.team.expiry.teams{result}`.

**Next layer.** The refund worker that sends the recorded obligations to Stripe is #29; it reuses `LeasedJobRunner` unchanged. Until that change is merged and explicitly enabled in deployment, obligations stay `pending`.

## Addendum (2026-09-24): local refund-worker candidate (#29)

`RefundWorkerJob` uses the renewable lease under the distinct `refund-worker` key and checks lease validity before calling `RefundService.processNext`. Each tick claims at most one due obligation; it neither loops over repository queries nor holds a database transaction across Stripe I/O. A provider failure remains eligible for the existing idempotent retry path, and uncertain or mismatched outcomes retain their existing operator-review state.

The bean requires both the `stripe-sprint2` profile and `blocbuy.scheduling.enabled=true`. This preserves the deployment-opt-in rule introduced for #28, but it also means the current `ec2`-only Compose configuration does not activate the worker. Enabling it requires a separate, deliberate rollout after backlog inspection and an operator recovery owner is named. Local PostgreSQL tests with a mocked Stripe gateway cover expiry, obligation persistence, a failed provider attempt, retry, receipt and audit without a duplicate refund; no shared-environment or live-provider result is claimed for this candidate.

## Addendum (2026-09-25): make the EC2 scheduling opt-in effective (#29)

Move `RefundService` wiring out of the `stripe-sprint2` checkout configuration and remove that profile condition from `RefundWorkerJob`. Both jobs now require only the deployment's existing `blocbuy.scheduling.enabled` switch; the refund worker stays inactive when it is false, and enabling it under `ec2` no longer enables Checkout. Operators must inspect overdue teams and due refund obligations before setting the switch because it can send sandbox refunds through the existing provider gateway. A context test under `ec2` verifies the worker is present without `stripe-sprint2`; a real-lease-runner test verifies runtime failures are recorded rather than propagated.
