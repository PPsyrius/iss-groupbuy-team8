<!-- report
rubric: 3.2.8
owner: Wang Ziang (Eric)
plan: #677
sources: ../report-drafts/transition-strategies.md
owns: R11, R14, R15
status: draft
-->

### 3.2.8 Concurrency

Several buyers can pay for the last seat of a team at the same moment, and the expiry sweep can meet a late payment on the same team. The design serialises these cases in PostgreSQL, not in application memory, with four mechanisms:

- **Pessimistic row locks in a fixed order.** Every path that changes a team's seats, orders or payments first locks the team's row with `SELECT … FOR UPDATE`, so two such paths on one team run one after the other. Table 3.12 gives the order each path takes after that.
- **Guarded updates.** A paid join writes the team row only through `PaidGroupDealMapper.updateDeal` (called by `PaidGroupDealRepositoryImpl.insertJoinedMember`), which writes only `WHERE current_size = expectedSize AND status = expectedStatus`. A writer that read a stale team therefore updates zero rows, `requireOne` throws, and the transaction rolls back. The same statement increments `biz_group_deal.version`, but no statement compares it, so the version is not the guard on this path. The expiry update `expireDeal` is guarded by `status = 'open'`; when it changes zero rows, `PaidGroupDealRepository.expire` refuses with `DEAL_CLOSED`.
- **Unique indexes as the last line of defence.** Unique indexes on members, receipts, orders, fulfilled purchase intents and refund obligations reject any duplicate that gets past the row locks (index list in section 3.6).
- **Work queues and leases.** Workers claim work with `FOR UPDATE SKIP LOCKED` (refund obligations, purchase-intent expiry, and the retention job that deletes old published outbox rows). The outbox relay reads its due rows without a row lock (`OutboxMapper.findDue`) and relies on the job lease alone. Each scheduled job holds a lease in `sys_scheduler_lock` through `SchedulerLeaseRepository.tryAcquire`, so only one application instance runs it at a time (section 3.2.7). A refund obligation also carries its own two-minute row lease, `lease_until` (retry rules in section 3.2.5).

Administrator edits of a listing add an optimistic version check (section 3.5.5).

#### Lock order

Table 3.12 states the lock order that the code takes on each path. On existing rows, every path takes a subset of one combined order, **team → purchase intent → order → payment → participation receipt → product**, and no path holds a later lock while it waits for an earlier one. The paths therefore cannot deadlock against each other. The webhook path does one thing before its first row lock: `StripeCheckoutService.settle` claims the Stripe event with a unique insert into `log_payment_webhook_event` (`claimEvent`, `ON CONFLICT (event_id) DO NOTHING`), and only then locks the team and purchase intent (`lockForSettlement`) and the payment (`repository.lock`). A second delivery of the same event waits on that unique entry, holding no row lock, and stops at the claim once the first commits.

Table 3.12: Lock order per path, as implemented, and where each part is recorded.

{{CONFIRM: D17 Where does the product row come in the lock order of each path: last after the receipt, or first, as on the admin and team-price quotation paths? — Eric}}

| Path | Locks, in the order taken | Recorded in |
|---|---|---|
| Checkout preparation (UC3, UC4), in `OrderFulfilmentAdapter` and `StripeCheckoutService` | team (JOIN only) → purchase intent → payment | ADR 0001 |
| Paid settlement by webhook (UC3, UC4), in `StripeCheckoutService`, `OrderFulfilmentAdapter` and `PaidGroupDealService` | webhook event claim (unique insert) → team → purchase intent → payment → participation receipt → product stock | ADR 0001 (team → intent → payment); `PaidGroupDealRepository` Javadoc (receipt, then team) |
| Team expiry (UC5, UC6), in `GroupDealExpiryService`, then its two subscribers | team → orders of the team → payments of the team | ADR 0002 (team → intent → order → payment) |
| Purchase-intent expiry, in `PurchaseIntentExpiryService` | team → purchase intent (`SKIP LOCKED`) | `PurchaseIntentRepository` Javadoc |
| Refund worker (UC6), in `RefundMapper.xml` | one refund obligation (`SKIP LOCKED`), and no other lock | none |
| All paths combined | team → purchase intent → order → payment → participation receipt → product | not yet in one record; an open backend task |

Three details make the paid settlement path fit this order. First, `PaidGroupDealService.joinConfirmed` locks the team again after the receipt, but the transaction already holds that lock from the start of settlement, so it does not wait. Second, the order is a new row, inserted after the receipt; until the commit no other transaction can see it, so no other path can wait on it. Third, the product row is touched last, by the stock decrement `CatalogPurchaseSupport.consumeOneUnit`; the earlier purchasability check is a plain read. On team expiry, `OrderLifecycleSubscriber` runs at `@Order(10)` and `TeamExpiryRefundSubscriber` at `@Order(20)`, so orders are locked before payments.

The written records do not yet agree. Backend ADR 0001 states "group deal → purchase intent → payment" for the paid path. Backend ADR 0002 states "team → intent → order → payment" for the expiry subscribers. The `PaidGroupDealRepository` Javadoc says "receipt then deal" (deal is the code's name for a team). Each statement is true for its own path. The Javadoc describes the order inside `PaidGroupDealService` alone. On a JOIN, the caller has already locked the team before the receipt, so the second team lock is one the transaction already holds. On a START, the team row does not exist yet. ADR 0001 adds that the receipt cannot cause a deadlock because it is keyed by payment ID, and the payment is already locked. Neither ADR states the combined order, and neither mentions the product row that the paid path updates last. Writing the combined order into the ADRs is an open documentation task; no locking code needs to change. Until then, the order in Table 3.12 is the one a new designer must follow.

#### Analysis objects affected

- «entity» objects with limited capacity or shared state: the team (`GroupDeal`), purchase intent, order, payment and refund obligation; the listing (`Product`) for administrator edits and stock.
- «control» objects that change more than one entity in one step: settlement, expiry and refund.
- The «control» objects started by the Scheduler, which must not run twice at once (job lease).

#### Use cases affected

UC3 and UC4 (the last seat and the last stock unit), UC5 (expiry against a late payment), UC6 (concurrent refund workers) and UC9 (concurrent listing edits).

#### Static structure changes

Repositories gain lock methods, so the locking is visible in the design instead of hidden in SQL. An entity that is edited concurrently gains a `version`. Jobs gain a lease port. Figure 3.27 shows the analysis «entity» `Team` and the design elements that make it safe under concurrency.

```mermaid
classDiagram
    class Team {
        <<entity>>
        -currentSize: Integer
        -targetSize: Integer
        +admit(buyer, payment) Admission
    }
    class GroupDeal {
        <<entity>>
        -currentSize: int
        -status: DealStatus
        +joinPaid(...) PaidAdmission
    }
    class PaidGroupDealRepository {
        <<interface>>
        +reserveAndLock(paymentId, ...) ParticipationReceipt
        +lockDeal(UUID dealId) Optional~GroupDeal~
        +expire(GroupDeal before, GroupDeal after, Instant now)
    }
    class PurchaseIntentRepository {
        <<interface>>
        +lockById(UUID id) Optional~PurchaseIntent~
        +tryLockForExpiry(UUID id) Optional~PurchaseIntent~
    }
    class CheckoutPaymentRepository {
        <<interface>>
        +lock(UUID id) CheckoutPayment
    }
    class SchedulerLeaseRepository {
        <<interface>>
        +tryAcquire(lockKey, owner, Duration lease) boolean
    }
    class Product {
        <<entity>>
        -version: int
    }
    %% at Team 0,0
    %% at GroupDeal 0,1
    %% at PaidGroupDealRepository 1,1
    %% at PurchaseIntentRepository 1,2
    %% at CheckoutPaymentRepository 0,2
    %% at SchedulerLeaseRepository 0,3
    %% at Product 1,3
    Team ..> GroupDeal : trace
    PaidGroupDealRepository ..> GroupDeal : locks
```

Figure 3.27: Class diagram, before (the analysis «entity» Team, top) and after the concurrency strategy. Lock methods appear on the repository ports, a concurrently edited listing carries a version, and scheduled jobs get a lease port.

Rationale: a lock method in the port makes the locking part of the design contract, so a reviewer can check the lock order from the interfaces, and a lease port lets several application instances share one schedule.

#### Dynamic behaviour changes

The analysis model says nothing about two buyers at once (Figure 3.28). In design, both settlements ask the database for the team's row lock; the second waits until the first commits, then reads the team, which the first admission has already set to SUCCEEDED, and is refused with `DEAL_CLOSED` because the team is no longer open (Figure 3.29).

```mermaid
sequenceDiagram
    participant A as a:JoinTeamControl (control)
    participant B as b:JoinTeamControl (control)
    participant T as :Team (entity)
    par buyer A pays
        A->>T: admit(buyerA, paymentA)
    and buyer B pays
        B->>T: admit(buyerB, paymentB)
    end
    Note over A,T: who gets the last seat is not defined
```

Figure 3.28: Analysis sequence of two joins for the last seat, before the concurrency strategy. Both controls call the same entity in parallel, and the model does not say which one wins.

```mermaid
sequenceDiagram
    participant A as a:StripeCheckoutService (control)
    participant B as b:StripeCheckoutService (control)
    participant DB as :PostgreSQL (database)
    A->>DB: lock team (FOR UPDATE)
    B->>DB: lock team (FOR UPDATE)
    Note over B,DB: b waits for a's commit
    A->>DB: lock intent, payment, receipt
    A->>DB: updateDeal guarded; insert member, order
    A->>DB: commit: team SUCCEEDED
    DB-->>B: team row lock granted
    B->>DB: lock intent, payment, receipt
    B->>DB: read team: not OPEN (DEAL_CLOSED)
    B->>DB: insert biz_refund (refund obligation)
    B->>DB: commit: payment SUCCEEDED, refund owed
```

Figure 3.29: Design sequence of the same race, after the concurrency strategy. The team row lock queues the second settlement behind the first; the loser's money is not lost but committed with a refund obligation.

Rationale: a group-buy must never sell more seats than its target or settle one payment twice. Database locks hold across application instances, which in-memory locks would not. Because the expiry sweep also locks the team first, a sweep and a late payment on the same team queue behind each other in the same way.

Evidence: the integration test `StripeLocalIntegrationTest.twentyBuyersRaceForLastSeatAndNineteenRefundObligationsPersist` starts 20 concurrent paid joins for the last seat of a team of two, through the production purchase-intent and fulfilment ports. Each payment settles through `StripeCheckoutService.recover`, the operator recovery entry, which runs the same settlement as a webhook under a fresh event ID. The test asserts that the team ends with two members, two orders and current size two, that 19 refund obligations are stored, and that stock fell by exactly two, from 100 to 98. Tested locally and in CI (PostgreSQL in Testcontainers), not on dev.

#### Transaction boundary

Each step of a use case that changes data is one database transaction, owned by the application service. `PaidGroupDealService` and `GroupDealExpiryService` use `@Transactional(timeout = 10)`. `StripeCheckoutService` uses a `TransactionTemplate` (READ COMMITTED, 10-second timeout), because its step must start only after the external provider has answered: it verifies the webhook signature and re-reads the Checkout session first, outside any transaction, and only then settles.

- A collaborator that must run inside its caller's transaction declares `Propagation.MANDATORY` (`OrderFulfilmentAdapter`), so calling it without one fails.
- No call to an external provider runs inside a transaction. `StripeCheckoutService.handleWebhook` refuses to start if a transaction is already open.
- Each event listener runs in the phase its work needs (listener phases, section 3.2.9).

Figure 3.30 shows the transaction boundary in the classes, with one listener of each phase: `OrderLifecycleSubscriber` (`BEFORE_COMMIT`) and `OpenDealOfferCacheSubscriber` (`AFTER_COMMIT`); the other listeners follow the same pattern and are not drawn. Figure 3.31 shows the transaction boundary on the settlement of the last seat. Before this strategy the step was the analysis interaction of Figure 3.28, with no transaction at all.

```mermaid
classDiagram
    class StripeCheckoutService {
        <<control>>
        -tx: TransactionTemplate
        +handleWebhook(rawBody, signature)
    }
    class PaymentBusinessPort {
        <<interface>>
        +fulfil(PaymentSucceeded event) Outcome
    }
    class OrderFulfilmentAdapter {
        <<control>>
        +fulfil(event) Outcome [MANDATORY]
    }
    class OrderLifecycleSubscriber {
        <<control>>
        +onTeamSucceeded(event) [BEFORE_COMMIT]
    }
    class OpenDealOfferCacheSubscriber {
        <<control>>
        +onTeamSucceeded(event) [AFTER_COMMIT]
    }
    %% at StripeCheckoutService 0,0
    %% at PaymentBusinessPort 0,1
    %% at OrderFulfilmentAdapter 0,2
    %% at OrderLifecycleSubscriber 1,0
    %% at OpenDealOfferCacheSubscriber 1,1
    StripeCheckoutService --> PaymentBusinessPort
    OrderFulfilmentAdapter ..|> PaymentBusinessPort
```

Figure 3.30: Class diagram of the transaction boundary, after the concurrency strategy. The payment service owns the transaction, the business port refuses to run outside it, and each subscriber's phase says whether its work is part of the fact or follows it.

Rationale: the payment slice owns the transaction and calls the business through a port that refuses to run outside it, and a subscriber's phase states in one place whether its work is part of the fact or follows it.

```mermaid
sequenceDiagram
    participant S as :StripeCheckoutService (control)
    participant F as :PaymentBusinessPort (interface)
    participant L as :OrderLifecycleSubscriber (control)
    participant E as :OpenDealOfferCacheSubscriber (control)
    Note over S: provider calls done, no transaction open
    critical one transaction: READ COMMITTED, 10 s
        S->>F: fulfil(PaymentSucceeded)
        F-->>S: Outcome, team full: TeamSucceeded
        S->>L: before commit: onTeamSucceeded
        L->>L: confirm the team's orders
    end
    S->>E: after commit: onTeamSucceeded
```

Figure 3.31: Design sequence of the last-seat settlement, transaction boundary and listener phases only. Provider calls stay outside the transaction (transaction drawn as a critical region, section 3.4.0), the confirmation of the team's orders commits with the payment, and the cache eviction runs only after the commit; the full settlement is Figure 3.44.

Rationale: the seat, the order and the stock unit change together with the payment's SUCCEEDED state, or not at all. A payment whose admission is rejected commits with a refund obligation instead. If the transaction fails, everything rolls back and Stripe delivers the webhook again. A slow provider never holds a database lock.

Evidence. No listener is `@Async`, and `StripeWebhookController` answers only after settlement returns, so the `AFTER_COMMIT` cache eviction also runs before the 2xx. Rollback and redelivery are covered by automated tests on PostgreSQL 18 (Testcontainers) that run in `build-quality`, not on dev: `StripeCheckoutFlowTest.signedHttpFailureRollsBackAndProviderRetryCanRecover` makes fulfilment fail inside the transaction, receives HTTP 500 with no webhook receipt, order or payment change stored, and then settles the same event on redelivery; `PaidGroupDealServiceIT.surroundingPaymentTransactionRollbackLeavesNoGroupOrMember` shows that a rolled-back payment transaction leaves no team or member.
