# 0002. A team's resolution is published, and its consequences run before the commit

Date: 2026-09-22

## Status

Accepted

## Context

Until this change the team lifecycle had no end. `biz_group_deal.status` could reach `succeeded`
through a join that filled the team, but nothing ever wrote `expired`: production code only read
that value. `OrderRepository.confirmByDealId` and `cancelByDealId` had been implemented and tested
since UC10 with **zero callers**, and `StripeCheckoutService.recordExpiredTeam` — which turns an
expired team into refund obligations — had none either. A buyer could pay, get an order, and watch
it sit in `pending` forever whatever the team did.

Closing that loop means groupbuy's decision has to reach two other domains. The hard gate forbids
reaching into another module's aggregates, mappers or repositories, and UC5 is assessed on the
state and observer patterns specifically, so an inline call from `PaidGroupDealService` to
`OrderRepository` was never an option — it would be both a boundary violation and the wrong shape.

Two smaller questions came with it: where the legal transitions live, and when the subscribers run.

## Decision

**Transitions live in `DealStatus.canTransitionTo`, and only there.** Only `OPEN` may move, never
to itself, and terminal states never re-enter — structurally identical to `OrderStatus`. `GroupDeal`
gains `expire(Instant now)`, guarded by the matrix rather than by an `if` on the status, and
`joinPaid`'s existing open-deal check is routed through the same matrix so there is one source of
truth. Services never branch on status.

The aggregate's two guards fail differently on purpose. A deal that is already terminal raises
`BizException(DEAL_CLOSED)` — another writer resolved it first, which the caller swallows as a
replay. A deal that is not due yet raises `IllegalStateException` — the sweeper's own due-list query
is wrong, and hiding that would hide a bug that silently expires live teams.

**groupbuy publishes; order and payment subscribe.** `TeamSucceeded(dealId, productId, size, at)`
and `TeamExpired(dealId, productId, at)` are plain JDK records in `com.blocbuy.groupbuy.event`,
carrying no annotations, published through Spring's `ApplicationEventPublisher` from the application
layer. `OrderLifecycleSubscriber` (order) confirms or cancels that team's orders;
`TeamExpiryRefundSubscriber` (payment) records the refund obligations. A subscriber consumes only
the immutable record — never the `GroupDeal` aggregate, a mapper, a DO or a repository
implementation — so this is a published contract, not a boundary breach.

**Subscribers run at `TransactionPhase.BEFORE_COMMIT`, ordered order(10) → payment(20).**

## Consequences

### Why not `AFTER_COMMIT`

`recordExpiredTeam`'s Javadoc says it verbatim: "Run inside the caller's team-expiry transaction;
never AFTER_COMMIT." The team's closure and the money owed for it are one fact. Recorded after the
commit, there is a window in which the team is expired and nobody is owed anything, and a failure to
record leaves that state permanently — nothing would undo the expiry. `BEFORE_COMMIT` is
synchronous, on the same connection, and a subscriber that throws rolls the whole resolution back.

### Why not a plain `@EventListener`

This is the easiest thing here to get wrong. `TeamSucceeded` is published from `joinConfirmed`,
which `OrderFulfilmentAdapter.fulfil` calls through `admit(...)` — and **that buyer's own order is
saved by `orders.save(...)` only after `admit` returns**. A plain listener fires immediately, so
`confirmByDealId` would sweep the team before the last order row exists, leaving the order of the
buyer who completed the team pending forever. `BEFORE_COMMIT` defers until every write in the
transaction is done, so the ordering is correct by construction rather than by luck.

A consequence worth stating: `@TransactionalEventListener` silently discards events published
outside a transaction. Both publishing sites (`joinConfirmed`, `GroupDealExpiryService.expireOne`)
are `@Transactional`, and the integration tests assert on the resulting rows — orders really are
`confirmed`, a refund row really exists — rather than on the listener having been called, because
only the rows can tell the difference.

### Lock order and concurrency

The subscribers' `@Order` follows the global lock order team → intent → order → payment, so no
combination of the two can take locks in opposite directions.

- **Sweeper against a settling webhook.** Both lock `biz_group_deal` first, so the order matches
  and they cannot deadlock. If the sweeper wins, the team is `expired` and the webhook settling
  afterwards reads `expired`, yields `BUSINESS_EXPIRED` and records a refund obligation — nobody is
  charged for a team they never joined. If the webhook wins, the team may be `succeeded`, and the
  sweeper's status-guarded UPDATE (`WHERE id = ? AND status = 'open' AND deleted = false`) affects
  zero rows; `PaidGroupDealRepository.expire` reports that as `DEAL_CLOSED` and `expireOne` returns
  false.
- **Refund obligations cannot be duplicated.** `recordExpiredTeam` only inserts, the worker only
  claims, and `UNIQUE (payment_id)` on `biz_refund` (V9) is the final line of defence, with the
  `version` fencing token on the refund row behind it.

### One aggregate, one transaction

`GroupDealExpiryService.expireOne` resolves exactly one team per call and holds no loop. The batch
sweep over many teams belongs to the scheduler that is not in this change; keeping the loop out of
this class keeps the lock on a single row and keeps the class unit-testable without a database.

## Alternatives rejected

| Alternative | Why not |
|---|---|
| `@TransactionalEventListener(AFTER_COMMIT)` | `recordExpiredTeam` forbids it, and it leaves a window where a team is expired and no refund is owed |
| A plain `@EventListener` | The last order of a completed team is not inserted yet, so `confirmByDealId` would miss it |
| groupbuy calling `OrderRepository` / `StripeCheckoutService` directly | Cross-domain hard gate, and UC5 requires the consequences to be carried by subscribers |
| One transaction expiring a whole batch of teams | One team's failure rolls back the batch, and the lock hold time starts contending with webhook settlement |
| A message broker between the domains | Not in the stack (locked decision, 2026-08-30); in-process publication inside one local transaction is what the consistency rule asks for |
