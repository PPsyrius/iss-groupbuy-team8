# Team lifecycle and refunds — completion plan (Liu Jin)

Date: 2026-09-22. Branch: `feature/order-deal-20260919`. Conventions: `backend-constraints-skill`
plus `pgsql-ddl`. Prerequisite: the four PRs of `order-payment-integration-plan-20260922.md` are
complete (`d24f8c6` / `0dc6fe1` / `ace9c24` / `d1c9d7e`).

## 0. Requirements confirmed (measured 2026-09-22)

The settlement trunk works end to end: browse → `POST /payments/intents` →
`POST /payments/checkout-sessions` → Stripe → `POST /webhooks/stripe` → team created and order
created, all in production code. **But the team lifecycle has no end**: when a team fills up its
orders are not confirmed, and when a team expires its orders are not cancelled and no money is
returned.

### 0.1 Gaps mapped to issues

| Gap | Issue | Owner | Measured evidence |
|---|---|---|---|
| Team success/expiry resolution and domain events | #28 UC5 | @l0sgAi (me) | `DealStatus` has no transition matrix; `GroupDeal` has no `expire`; production code only ever reads `DealStatus.EXPIRED`, never writes it |
| Order `pending → confirmed / cancelled` | #124 UC10, remaining item | @l0sgAi (me) | `OrderRepository.confirmByDealId` / `cancelByDealId` are implemented and tested, with **zero callers in the application layer** |
| Triggering and executing refunds on expiry | #29 UC6 | @Yorick-Yu (handed over) | `StripeCheckoutService.recordExpiredTeam` and `RefundService.processNext` have no production callers at all; there is not one `@Scheduled` anywhere in `src/main` |
| Two defects that block wiring the refund worker | #132 / #133 | @Yorick-Yu (handed over) | See §3.2 |
| No write surface for product group terms | NFR-29 / backend ADR 0001 "Open" | Unassigned | `grep team_target_size` finds reads only across all of main; `AdminListingController.ListingContent` has neither field |
| `GET /orders/{id}` | #31 UC8, backend half | @limjiechao | The contract has only `/orders/me` |
| A webhook for an unknown payment answers 500 | #139 | @Yorick-Yu (handed over) | Nothing catches `UUID.fromString` in `apply` or `require(id)` in `settle` |
| The sandbox gate is duplicated seven times | #130 | @Yorick-Yu (handed over) | Seven sites across three files; `StripePaymentGateway` has none at all |

The last two rows are not lifecycle gaps. They are payment-slice hardening folded into this
iteration on 2026-09-22; the reasoning is in §0.4.

Item 2 of the pre-live checklist in `backend/docs/payment/stripe-release-cleanup-20260920.md` — "Wire and
monitor the refund worker and expiry notification path" — is the same work as rows 3 and 4 above.
Item 1 was closed by the previous plan.

### 0.2 Where each UC5 acceptance criterion lands

Issue #28 names two design problems, and the course deliverable is assessed on them:

- **State**: each lifecycle state owns its legal transitions and the aggregate rejects illegal ones
  → the `DealStatus.canTransitionTo` matrix plus `GroupDeal.expire`, structurally identical to
  `OrderStatus.canTransitionTo`.
- **Observer (publisher side)**: resolving the state publishes `TeamSucceeded` / `TeamExpired`, and
  the consequences are carried by subscribers rather than inline calls → §3.1.

"Scheduled/timeout resolution path covered" → the sweeper in §3.3.

### 0.3 One issue that no longer holds

**#138 was eliminated by `0dc6fe1`; it should be closed.** The issue assumes `businessDeadline` can
be earlier than `quoteValidUntil`, so that a `prepare` validating only the latter would issue a
session outliving the team's deadline. In fact both `PurchaseIntentAppService` paths guarantee
`quoteValidUntil ≤ businessDeadline`:

- JOIN: `quoteValidUntil = earlier(now + 2400s, team.expireTime())`,
  `businessDeadline = team.expireTime()`
- START: `quoteValidUntil = now + 2400s`, `businessDeadline = now + lifetimeSeconds`, and
  `lifetimeSeconds ≥ 2400` (guaranteed jointly by the V14 comments and
  `ProductTeamTerms.startable()`)

The existing `expires.isAfter(quote.quoteValidUntil())` in `prepare` is therefore identical to, or
stricter than, the check the issue asks for. Optionally change it to `quote.expiredAt(expires)` as
regression protection — one line, carried by PR-F.

### 0.4 Two payment-slice issues folded into this iteration (decided 2026-09-22)

`#139` and `#130` do not belong to lifecycle completion. The reasons for including them differ, and are
recorded separately:

- **#139 (a webhook for an unknown payment answers 500)** — it will fire continuously during this
  iteration's integration work. Once PR-G puts the sweeper and the refund worker on a schedule, a
  developer machine and the shared server pointing at the same Stripe sandbox endpoint is the
  normal case; every event that does not belong to this database answers 500, and Stripe retries a
  non-2xx event for up to 3 days. Leaving it in place means PR-G's logs drown in someone else's
  retries and we cannot tell whether our own jobs ran. **It is a precondition for this iteration's
  observability, not a convenience.**
- **#130 (the sandbox gate duplicated seven times)** — a pure refactor with no behavioural benefit;
  its only value is giving the "test mode only" invariant a single source of truth. It is folded in
  because the payment slice's code surface settles after this iteration and changing it later only
  gets more expensive, and because its safety net already exists (the two gateways' existing live-key
  and unconfigured-key rejection tests), so the refactor is verifiable. **It is the last PR and can
  be dropped at any point without affecting the other six.**

Neither touches the HTTP contract or the database schema.

## 1. Boundaries and layer placement

The hard gate is unchanged: cross-domain access goes only through the other domain's
application-layer interface or facade. This plan adds exactly one cross-domain direction —
**groupbuy publishes facts; order and payment subscribe**. A domain event is an explicitly
published contract (`com.blocbuy.<domain>.event` already declares in its package-info: "Published
in-process; no message broker in v1"), and subscribers consume only immutable records, never the
`GroupDeal` aggregate, a mapper, a DO or a RepositoryImpl — **so this is not a cross-domain
violation**.

```
groupbuy / domain       DealStatus transition matrix + GroupDeal.expire + TeamSucceeded / TeamExpired (records)
groupbuy / domain       PaidGroupDealRepository gains dueForExpiry / expire
groupbuy / application  PaidGroupDealService publishes TeamSucceeded; GroupDealExpiryService resolves expiry and publishes TeamExpired
order    / application  OrderLifecycleSubscriber (subscribes to both events → confirmByDealId / cancelByDealId)
payment  / application  TeamExpiryRefundSubscriber (subscribes to TeamExpired → recordExpiredTeam)
common   / domain       SchedulerLeaseRepository (port)
common   / infra        SchedulerLeaseRepositoryImpl + SysSchedulerLockMapper.xml
bootstrap / config      SchedulingConfiguration (@EnableScheduling + two @Scheduled drivers)
catalog  / *            group-terms write surface (PR-H)
order    / adapter      GET /orders/{id} (PR-I)
payment  / application  the unknown-payment branch in StripeCheckoutService.apply / settle (PR-J)
payment  / infra        StripeProperties.sandbox + the StripeClientProvider gate (PR-K)
```

PR-J and PR-K land entirely inside the payment slice and add no cross-domain direction.

Events use Spring's `ApplicationEventPublisher`, and **both the publishing and the subscribing
sites are in the application layer**; the event records themselves stay in domain and carry no
annotations, so `domain_is_framework_free` still passes.

## 2. PR breakdown

| # | PR | Contents | Depends on | Contract change |
|---|---|---|---|---|
| 1 | PR-E | Team lifecycle resolution + domain events + order subscriber (the bulk of UC5) | — | No |
| 2 | PR-F | Pre-wiring fixes for the refund worker (#132 / #133) + #138 regression guard | — | No |
| 3 | PR-J | Record and acknowledge webhooks for unknown payments (#139) | — | No |
| 4 | PR-G | Scheduling foundation (lease) + team-expiry sweeper + refund worker | PR-E, PR-F | No |
| 5 | PR-H | Admin write surface for product group terms (NFR-29) | — | **Yes** (additive) |
| 6 | PR-I | `GET /orders/{id}` | — | **Yes** (additive) |
| 7 | PR-K | Consolidate the sandbox gate into `StripeClientProvider` (#130) | PR-J | No |

**PR-J must precede PR-K, and PR-K goes last.** Both touch `StripeCheckoutService`: PR-J changes
the behaviour of `apply` / `settle`, PR-K only relocates the sandbox criterion. The refactor sits on
top of settled behaviour, not the other way round; otherwise PR-J's behavioural change would be
reviewed alongside a gate that has just moved house, and neither would be legible in one diff.

**PR-F must land before PR-G.** Both defects are latent while the worker has no scheduler. The
moment PR-G puts it on a schedule, #133 makes every successful refund re-read Stripe once an hour
for 31 days (roughly 744 provider reads and 1,488 audit rows), and #132 makes a permanent
`PAYMENT_STATE_INVALID` retry every 30 seconds for 23 hours (roughly 2,760 cycles). Wiring it up
without these fixes is an incident.

## 3. PR detail

### 3.1 PR-E: team lifecycle resolution and events

**Domain (groupbuy)**

- `DealStatus` gains `canTransitionTo(DealStatus target)`: only `OPEN` may move, and it may go to
  `SUCCEEDED` / `EXPIRED` / `FAILED` / `CANCELED`; terminal states never move again and nothing
  transitions to itself. The matrix lives in the enum and services must not branch on status with
  if/else — the same shape as `OrderStatus`.
- `GroupDeal.expire(Instant now)`: returns an `EXPIRED` snapshot, with two guards —
  `status.canTransitionTo(EXPIRED)` or else `BizException(GroupbuyErrorCode.DEAL_CLOSED)`; and
  `now.isBefore(expireTime)` or else `IllegalStateException` (a bug in the scheduler itself, not
  user input).
- The existing `succeeded ? SUCCEEDED : OPEN` inside `joinPaid` is rerouted through
  `canTransitionTo`, so the matrix is the single source of truth.
- Two new records (`com.blocbuy.groupbuy.event`, plain JDK):
  - `TeamSucceeded(UUID dealId, UUID productId, int size, Instant at)`
  - `TeamExpired(UUID dealId, UUID productId, Instant at)`
- `PaidGroupDealRepository` gains two methods:
  - `List<UUID> dueForExpiry(Instant now, int limit)` — uses the existing
    `idx_biz_group_deal_status_expire` (created in V5):
    `WHERE deleted=false AND status='open' AND expire_time <= #{now} ORDER BY expire_time LIMIT`
  - `void expire(GroupDeal before, GroupDeal after, Instant now)` — a status-guarded UPDATE
    (`WHERE id=? AND status='open' AND deleted=false`); 0 rows means a concurrent resolution
    already happened, so it throws `BizException(GroupbuyErrorCode.DEAL_CLOSED)` for the caller to
    swallow as an idempotent replay

**Application layer**

- `PaidGroupDealService`: inject `ApplicationEventPublisher` and, when `joinConfirmed` writes
  successfully and `after.status() == SUCCEEDED`, `publishEvent(new TeamSucceeded(...))`.
- New `GroupDealExpiryService` (groupbuy application): `boolean expireOne(UUID dealId)` — one short
  transaction per team, `lockDeal` → `currentTime` → `deal.expire(now)` → `repository.expire(...)`
  → `publishEvent(new TeamExpired(...))`. The sweep loop is not here (see PR-G); this class resolves
  a single team, which keeps it unit-testable.
- New `OrderLifecycleSubscriber` (order application):
  ```java
  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  @Order(10)
  void onTeamSucceeded(TeamSucceeded event) { orders.confirmByDealId(event.dealId()); }
  ```
  `TeamExpired` is structurally identical and calls `cancelByDealId(dealId, event.at())`.
- `TeamExpiryRefundSubscriber` (payment application): `@Order(20)`, calling
  `StripeCheckoutService.recordExpiredTeam(dealId)`. The bean is registered in the existing
  `StripeCheckoutConfiguration` (`@Profile("stripe-sprint2")`) — with payments switched off there
  is no money and therefore no refund obligation, while order cancellation happens as usual.

**Four timing decisions that must be spelled out**

1. **Why `BEFORE_COMMIT` and not `AFTER_COMMIT`.** The Javadoc on `recordExpiredTeam` says
   verbatim: "Run inside the caller's team-expiry transaction; never AFTER_COMMIT." The refund
   obligation and the team's expiry must live or die together; adding it after the commit leaves a
   window in which the team is expired and no refund is owed. `BEFORE_COMMIT` is synchronous, in
   the same transaction on the same connection, and a subscriber that throws rolls everything back
   — exactly the semantics wanted.
2. **Why not a plain `@EventListener`.** `TeamSucceeded` is triggered indirectly by `admit(...)`
   inside `OrderFulfilmentAdapter.fulfil`, and **that buyer's own order is only saved by
   `orders.save(...)` after `admit`**. A plain listener would run `confirmByDealId` while the last
   order is still pending, leaving the order of the buyer who completed the team stuck in pending
   forever. `BEFORE_COMMIT` defers until every write in that transaction is done, so the ordering
   is correct by construction. This is the easiest thing in this PR to get wrong.
3. **The two subscribers' order is fixed by `@Order` at order(10) → payment(20)**, matching the
   global lock order team → intent → order → payment.
4. `@TransactionalEventListener` silently discards events published outside a transaction. Both
   publishing sites (`joinConfirmed`, `expireOne`) are inside `@Transactional`, and the ITs must
   assert positively that "after the team fills, the orders really are confirmed" rather than
   merely asserting that the listener was invoked.

**Tests**

- Unit: every combination of the `DealStatus` matrix; `GroupDeal.expire` in three cases (legal, not
  yet due, terminal); `GroupDealExpiryService` in three cases (normal, concurrent resolution
  affecting 0 rows, lock unavailable); two cases per subscriber.
- IT (appended to `StripeLocalIntegrationTest`):
  - two buyers complete a team → both orders are `confirmed` and
    `biz_group_deal.status='succeeded'`
  - one buyer starts a team that then expires → the order is `cancelled`, one `biz_refund` row is
    `pending`, and the team is `expired`
  - `expireOne` repeated on the same team → the second run has no effect (0 new refunds, 0 status
    changes)

### 3.2 PR-F: pre-wiring fixes for the refund worker

**#132, a permanent error should not retry every 30 seconds.** `RefundService.processNext`
currently swallows every provider exception in a single `catch (BizException)` and records
`processing` / `PROVIDER_RETRY` regardless. Split on `errorCode()`:

| errorCode | Stored state | result |
|---|---|---|
| `PAYMENT_STATE_INVALID` (PaymentIntent not succeeded / currency or amount mismatch / live mode) | `review` | `PROVIDER_STATE_MISMATCH` |
| `PAYMENT_PROVIDER_ERROR` (a StripeException) | `processing` | `PROVIDER_RETRY` |

`reconcile` splits the same way (`OPERATOR_PROVIDER_STATE_MISMATCH`).

The 23-hour create-versus-lookup cutoff is evaluated immediately before the provider call, not
from the timestamp captured by `claimNext`. Add `RefundRepository.currentTime()` backed by the
database clock and compare that fresh value with `firstAttemptAt`. A worker suspended after claiming
work must use `findRefund` rather than `createRefund` if it resumes beyond the cutoff.

**#133, a succeeded refund should not be claimed again.** Three places change together; missing any
one makes the fix ineffective:

1. drop `'succeeded'` from the `status IN (...)` filter in `RefundMapper.claimNext`
2. stop `RefundMapper.finish` from setting `next_attempt_at = clock_timestamp() + 1 hour` for
   `succeeded`
3. a new migration `V16__refund_due_index.sql` rebuilding the partial-index predicate of
   `idx_biz_refund_due` as `status IN ('pending','processing')`. **`DROP INDEX` + `CREATE INDEX`,
   without `CONCURRENTLY`** — Flyway runs inside a transaction by default and `CONCURRENTLY` would
   fail there; the table's Sprint 2 data volume is negligible.

**#138 regression guard (optional, one line)**: change `expires.isAfter(quote.quoteValidUntil())`
in `prepare` to `quote.expiredAt(expires)`. Behaviourally equivalent (§0.3), but it pins the
invariant in one place, on `CheckoutQuote`.

**Tests**: written against the two issues' ACs — three `RefundServiceTest` cases (a state mismatch
parks for review and makes no further gateway call; a transient error still retries under the same
idempotency key; and a worker claimed before the cutoff but dispatched after it uses `findRefund`
without calling `createRefund`) and one `StripeLocalIntegrationTest` case (after
`finish(..., "succeeded", ...)`, `claimNext` returns null).

### 3.3 PR-G: the scheduling foundation and two jobs

**The lease (`sys_scheduler_lock`, created in V13, unused to this day)**

- Port `com.blocbuy.common.repository.SchedulerLeaseRepository` (domain module, plain JDK):
  ```java
  boolean acquire(String lockKey, String owner, Duration lease);
  void release(String lockKey, String owner);
  ```
- Implementation `com.blocbuy.common.persistence.SchedulerLeaseRepositoryImpl` plus
  `SysSchedulerLockMapper.xml`, following V13's "Application obligations" comment strictly:
  `INSERT ... ON CONFLICT DO NOTHING` to create the row, then `SELECT ... FOR UPDATE NOWAIT`;
  failing to take the lock means another instance is sweeping, so **skip this run rather than
  queue**; a `locked_until` in the past means the lease is stale and may be taken over (updating
  `owner` and `locked_until` under the same row lock).
- Redis does not participate: a lock that decides whether money is refunded is business state, and
  PostgreSQL is the only system of record (already stated in the V13 comments).

**Team-expiry sweeper**

- `GroupDealExpirySweeper` (groupbuy application): `int sweep(int limit)` — **one batch query
  first**, `dueForExpiry(now, limit)` for the id list, then `GroupDealExpiryService.expireOne(id)`
  one at a time.
- **Explicit exception to the "no queries inside a loop" rule**: the loop really does issue one
  `lockDeal` plus one UPDATE per team. This is not N+1 read amplification but a write job that locks
  one aggregate at a time. Merging it into one large transaction would (a) let one team's failure
  roll back the whole batch, (b) stretch lock hold time until it contends with webhook settlement,
  and (c) break the "one aggregate, one transaction" boundary. So the per-team short transaction
  stays, and the cost is bounded by **fetching ids in one batch and isolating failures with a
  try/catch per team**; a failure logs WARN and continues, and the next run retries naturally
  (`status` is still `open`).
- The driver lives in bootstrap: `SchedulingConfiguration` (`@EnableScheduling`),
  `@Scheduled(fixedDelayString = "${blocbuy.scheduling.team-expiry.interval:PT1M}")`, acquiring the
  lease first, releasing it at the end, and logging one INFO line per run with the number of teams
  resolved.

**Refund worker**

- The same `SchedulingConfiguration`, in its `@Profile("stripe-sprint2")` half:
  `@Scheduled(fixedDelayString = "${blocbuy.scheduling.refund-worker.interval:PT30S}")`, and inside
  the lease `while (processed < batch && refunds.processNext()) processed++;` with `batch`
  defaulting to 20 — bounded, so one run cannot occupy the whole lease.
- `RefundService.processNext` manages its own transactions (a `TransactionTemplate` with a 5-second
  timeout), so the driver must **not** be wrapped in `@Transactional`.

**Concurrency and lock-order argument (must go into the ADR)**

- The sweeper and a webhook contending for the same team: both lock `biz_group_deal` first, so the
  lock order matches and they cannot deadlock. If the sweeper wins, the team becomes `expired` and
  the JOIN settling afterwards reads `expired` → `BUSINESS_EXPIRED` → a refund obligation, so no
  buyer is charged without joining. If the webhook wins, the team may become `succeeded` and the
  sweeper's `expire` affects 0 rows under its status guard and is skipped as an idempotent replay.
- The refund worker running concurrently with `recordExpiredTeam`: the former only claims
  `biz_refund` rows, the latter inserts them, and the `UNIQUE` on `biz_refund.payment_id` (V9) is
  the final line of defence against duplicate refund obligations.
- Losing the lease does not by itself fence a hung worker's provider call. Concurrent stale-owner
  calls use the same immutable `RefundWork.providerKey()` as Stripe's idempotency key, so retries
  within the provider window resolve to one provider refund. The refund-row `version` only fences a
  stale local completion, the payment-id unique constraint prevents duplicate obligations, and the
  provider-refund-id unique index is a persistence backstop after Stripe returns an id. The fresh
  23-hour check above prevents a suspended worker from issuing another create after the idempotency
  retention boundary.

**Tests**

- Unit: lease acquisition, takeover, and failure to acquire; failure isolation in `sweep` (three
  teams where the second throws, the other two still resolve, returns 2).
- IT: two "instances" acquiring the same key concurrently, with exactly one succeeding; an expired
  team going through one scheduler run leaves the order `cancelled` and a `pending` refund
  obligation; a second run has no further effect.

### 3.4 PR-H: admin write surface for product group terms (NFR-29)

`AdminListingController.ListingContent` gains two **optional** fields:

```java
@Min(2) @Max(100) Integer teamTargetSize,
@Min(2400) @Max(86400) Integer teamLifetimeSeconds
```

Both must be supplied together or both left empty (the cross-field check goes in the application
layer with a new `CatalogErrorCode`, rather than being expressed through Bean Validation). The
bounds match `ProductTeamTerms.startable()` exactly, so nothing can be saved by the admin surface
that the checkout path then refuses.

Writing does not touch the `Product` aggregate root — it follows the read-side approach already
established in ADR 0001 (the `ProductTeamTerms` value object plus its own mapper statement).
`ProductRepository` gains `saveTeamTerms(UUID productId, ProductTeamTerms terms, UUID actorId)`,
called by `AdminListingAppServiceImpl.create` / `revise` **inside the same transaction, after the
same `expectedVersion` CAS**. Two UPDATEs against the same row is deliberate: changing the
aggregate root's signature would hit 14 `reconstitute` call sites for zero benefit.

Contract: additive. Regenerate with `./mvnw -Popenapi-export` and commit. NFR-29 moves to
`implemented`.

### 3.5 PR-I: `GET /orders/{id}`

Owner-scoped: any buyer who is not the owner gets 404, not 403 — do not leak whether the order
exists. The response reuses the `OrderSummaryResponse` field set, adding no `paymentId` and no
recipient PII. `OrderAppService` gains
`Optional<OrderSummaryView> findMine(UUID orderId, UUID buyer)`. Contract: additive.

**The "my teams" half of UC8 is not in this plan** — `GET /api/v1/group-deals/me` belongs to
groupbuy, issue #31 is assigned to @limjiechao, and who decides the interface shape has to be
settled first. `GroupDealController` is currently an empty shell and can take it at any time.

### 3.6 PR-J: record and acknowledge webhooks for unknown payments (#139)

**Current state**: the first line of `StripeCheckoutService.apply` is
`UUID.fromString(session.paymentId())`, and `settle` immediately calls `require(id)`. The former
throws `IllegalArgumentException` on provider metadata that is not a UUID; the latter throws
`IllegalStateException("CheckoutPayment not yet available")` for an id this database does not have.
Nothing on the path catches either, `StripeRequestExceptionHandler` maps only
`MissingRequestHeaderException`, and the exception ends up at
`GlobalExceptionHandler.handleUnknown`, which answers **500**. Stripe retries a non-2xx event for
up to 3 days, every retry hits the same path, and `claimEvent` is rolled back with the transaction
so deduplication never takes effect either.

**Why it must be fixed in this iteration**: it fires reliably whenever a developer machine and the
shared server point at the same Stripe sandbox endpoint, and again after any database reset — which
is the normal condition while integrating PR-G. The class comment on `StripeWebhookController` says
"Always answers 2xx quickly for handled/ignored events so the provider does not retry storms"; a
`payment_id` in the metadata that does not belong to this database is exactly **an ignored event**,
and should be recorded and acknowledged rather than answered with 500.

**The change**

1. In `apply`, replace `UUID.fromString` with a safe parse; on failure open a short transaction
   doing `claimEvent` plus `finishEvent(eventId, null, "UNKNOWN_PAYMENT")`, log one WARN line with
   the session id, and return.
2. In `settle`, replace `require(id)` with `findById(id)`; when empty, record `UNKNOWN_PAYMENT` the
   same way and return, **touching neither** `business`, `lock` nor `save`. This path is already
   inside the transaction with a successful `claimEvent`, so it is deduplicated for free.
3. Keep `require(UUID)` as is for `preview` / `inspect` / `recover` — an operator issuing a command
   against a payment that does not exist is an operator error and must blow up.
4. **`finishEvent` must be able to write a null payment_id.** The column is nullable (V9's
   `ALTER TABLE log_payment_webhook_event ADD COLUMN payment_id uuid`, with no NOT NULL), but
   `PaymentMapper.finishEvent` currently writes a bare `#{paymentId}`; passing null leaves MyBatis
   unable to infer the JDBC type and errors against a PostgreSQL `uuid` column. Change it to
   `#{paymentId,jdbcType=OTHER}`, matching how every other uuid parameter is written in this
   repository.

**Tests** (per the issue's ACs)

- `StripeCheckoutServiceTest`: two cases, a `payment_id` in the metadata that is not a UUID, and one
  that is a UUID this database does not have. Each asserts that nothing throws, that one
  `UNKNOWN_PAYMENT` record is written, and that there is zero interaction with `business` / `lock` /
  `save`.
- `StripeWebhookBoundaryTest`: MockMvc answers 200 `COMMON_200_000` for such an event.

### 3.7 PR-K: consolidating the sandbox gate (#130)

**Current state**: the "Stripe test mode only" invariant is written out seven times across three
files — `StripeCheckoutGateway.requireSandbox` (called at lines 38/87/105),
`StripeRefundGateway.requireSandbox` (a byte-identical copy, called at lines 31/65/75), and two
inline `cs_test_[A-Za-z0-9_-]{1,240}` regexes inside `StripeCheckoutService` (`inspect`,
`recover`). The comment on `StripeClientProvider.client()` says "every caller must pass the sandbox
gate first", but it does not enforce that itself. When live mode is eventually enabled, one missed
site silently leaves that path on sandbox.

**The change**

1. `StripeProperties` gains `sandbox()` (configured, and `apiKey` starts with `sk_test_`);
   `StripeClientProvider.client()` throws `BizException(PAYMENT_PROVIDER_ERROR)` when `sandbox()` is
   false; both `requireSandbox()` methods are deleted.
2. The `cs_test_` regex becomes one named predicate shared by `inspect` and `recover`.
3. The `liveMode` checks (on webhook events, retrieved sessions and retrieved PaymentIntents)
   **stay** — they validate data coming back from the provider, not local configuration — but they
   are routed through one named predicate so the rule reads the same everywhere.

**Two facts verified before implementing** (they decide whether this refactor is safe)

- All six `requireSandbox()` call sites are **immediately** followed by `clients.client()`, and
  `verifyCheckoutWebhook` does not call `requireSandbox` (it uses the webhook secret and needs no
  client). Moving the gate into `client()` is therefore exactly equivalent at those six sites and
  adds no new gate in front of webhook verification.
- Both gateways catch only `catch (StripeException)`, so a `BizException` is never swallowed —
  moving the gate from outside the `try` to inside it does not change the exception exit.

**One TODO closed on the way (a scope increase, stated explicitly)**: `StripePaymentGateway` (the
old `PaymentGateway` contract) does not go through `StripeClientProvider` and calls
`new StripeClient(properties.apiKey())` on every use, with the comment "TODO: hoist to a singleton
bean once credentials are provisioned per env" — it has **no sandbox gate at all**. Issue #130's
grep-based AC cannot catch it, because there is no `sk_test_` literal there to begin with. This PR
routes it through `StripeClientProvider` and brings it under the gate. Risk: with an unconfigured or
live key it now throws `PAYMENT_PROVIDER_ERROR` earlier, and `StripeDevCompatibilityTest` asserts
the wiring of that older path, so it must pass too. If that test exposes a compatibility constraint
that should not be changed, fall back to steps 1–3 only and state in the PR description that this
path remains unguarded.

**Tests**: the existing assertions in `StripeCheckoutGatewayTest` / `StripeRefundGatewayTest` that
an `sk_live_` key and an unconfigured key are rejected before any Stripe call must pass unchanged —
they are precisely this refactor's safety net. `StripeCheckoutServiceTest` must still reject
live-mode events and non-`cs_test_` hints. Re-check the AC by grep:

```bash
grep -rn "sk_test_\|cs_test_\[" backend/*/src/main
```

Each literal should match at most one production site.

## 4. Rejected approaches

| Approach | Why rejected |
|---|---|
| Subscribers using `@TransactionalEventListener(AFTER_COMMIT)` | `recordExpiredTeam` forbids it explicitly, and it would leave a window in which a team is expired with no refund obligation |
| Subscribers using a plain `@EventListener` | The last order of a completed team is not inserted yet, so `confirmByDealId` would miss it (§3.1, decision 2) |
| groupbuy calling `OrderRepository` / `StripeCheckoutService` directly | The cross-domain hard gate; and UC5's acceptance criteria require "consequences handled by subscribers, not inline" |
| Redis as the single-flight scheduling lock | A lock that decides whether money is refunded is business state, and PostgreSQL is the only system of record |
| One large transaction expiring the whole sweep batch | One team's failure rolls back the batch, and lock hold time contends with webhook settlement |
| Putting `expireDueSessions` on a schedule too | #134 is unfixed (review rows crowding out the batch, plus head-of-line blocking), so wiring it would make things worse; it stays a manual `stripe-ops` command |
| Changing `Product.reconstitute`'s signature for the group terms | 14 call sites for zero benefit (already settled in ADR 0001) |

## 5. Acceptance gates (every PR must pass these)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd backend && ./mvnw -B verify
```

- Spotless / Checkstyle (0 violations) / SpotBugs / all 8 ArchUnit rules green
- In the JaCoCo **aggregate** report
  (`blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml`, which only exists after `verify`),
  the files this PR touched have **0 missed lines and 0 missed branches**
- PR-H / PR-I: after regenerating with `./mvnw -B verify -Popenapi-export -DskipTests ...`,
  `git diff --exit-code -- contracts/` is clean
- One `docs/agile/sprint-02/log/YYYY-MM-DD.md` entry per PR; PR-E and PR-G each add an NFR row; PR-H moves
  NFR-29 to `implemented`; the timing and lock-order arguments from PR-E and PR-G go into backend
  ADR 0002
- PR-K additionally runs issue #130's grep-based AC and pastes the output into the PR description

Issues that should be closeable at the end of this iteration: #27, #28, #29, #124, #130, #132,
`#133`, `#138`, `#139` — nine, all closed after the branch merges into `dev`.

### 5.1 Frontend impact (verified 2026-09-22)

**This is a backend-only iteration: the seven PRs change not one `frontend/` file, and no frontend
artefact needs committing.**

But "changes no frontend file" is not "does not touch the frontend pipeline". Three things to be
clear about:

1. **PR-H and PR-I will trigger `ci-frontend`.** That workflow's `paths` include `contracts/**`
   (`.github/workflows/ci-frontend.yml`), so any contract change runs the full
   `gen:api → typecheck → lint → fmt:check → test → knip`. Run it locally before pushing either PR:
   ```bash
   cd frontend && pnpm check
   ```
2. **The generated client is not in git and needs no parallel commit.**
   `frontend/src/api/generated/` is in `.gitignore:87`, and the `prepare` hook regenerates it from
   `contracts/openapi.json` every time. A backend contract change flows into the frontend client
   automatically, so there is no "forgot to regenerate" drift risk.
3. **Both contract changes are additive and cannot break the frontend build.** PR-I adds one path;
   PR-H adds two **optional** fields to the `ListingContent` request body — a new optional request
   field is backward compatible and passes `openapi-diff`'s breaking-change check. Newly generated
   SDK functions that nothing imports are not flagged as dead code either: `knip.json`'s `project`
   already excludes `!src/api/generated/**`.

**Three behavioural changes, none of which needs a frontend change**

| Change | Frontend as it stands | Conclusion |
|---|---|---|
| Orders start becoming `confirmed` / `cancelled` | `account.tsx` currently has **no order-history section** (not rebuilt after #128 removed the old `GET /orders`; see #137), and no component renders order status | No visible difference, nothing breaks |
| Refund status actually starts progressing | `payments.$paymentId.tsx` already has the `SUCCEEDED` / `FAILED` / `REVIEW` branches for `refundStatus` and a `refundPending` branch | No change needed; that code becomes genuinely reachable for the first time |
| Group terms become configurable from the admin surface | The frontend has no admin route at all; `admin` appears only in generated code | No consumer |

**One mismatch to state up front**: the receipt page polls for 60 seconds (2s × 30) while the
refund worker runs every 30 seconds by default, so a refund usually has not finished while the
buyer still has the receipt page open. The page already says "This is not confirmation that the
refund has been sent", so this is not a defect; but "where do I watch the refund progress" still
has no entry point, which belongs to #31 / #128 and is not in this plan.

**So "the order/payment backend is complete" means precisely this**: after this iteration the
backend closes the loop — start a team, pay, create the order, confirm on success, cancel and
refund on expiry, with an executor driving the refunds. **But three capabilities have no frontend
presentation**: my orders / my teams (#31, #128), the group-terms admin page, and refund progress.
A complete backend is not a usable product, and that has to be said plainly at the sprint review so
"the backend is done" is not read as "UC5/UC6 can be demonstrated end to end".

## 6. Out of scope for this plan

| Issue | Why not |
|---|---|
| #136, the receipt reads the same row four times | An efficiency problem, not a correctness one; the receipt response shape is unchanged, so it can be done any time |
| #134, `expireDueSessions` crowd-out and head-of-line blocking | Only becomes a real harm once session expiry is put on a schedule, which this plan does not do (see §4) |
| #128 / #135 / #131 / #137 | Frontend |
| Removing `DevPaymentRepositoryAdapter` (the last sentence of pre-live checklist item 1) | It implements the **old** `PaymentRepository` contract, not the two ports connected here; ownership to be confirmed with Yorick |
