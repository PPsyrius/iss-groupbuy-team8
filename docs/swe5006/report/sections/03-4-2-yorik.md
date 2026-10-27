<!-- report
rubric: 3.4.2, 3.4-A..E, G4
owner: Yu Qianshuo (Yorik)
plan: #678
sources: ../report-drafts/yorick-yu-contribution.md (YY:5–212); ../report-drafts/statecharts.md (SC:83–110)
owns: R09, R28, FG04
status: draft
-->

### 3.4.2 UC6 Refund on Team Expiry by Yu Qianshuo (Yorik)

When a team's deadline passes before its last seat is taken, BlocBuy expires the team, cancels its orders and records one refund obligation for every paid member, all in one database transaction. A scheduled refund worker then pays each obligation back to the original card through Stripe. It retries a temporary provider failure with the same request identity, and it parks any case it cannot settle safely for an operator to review. A team is one group-buy of one product, shown as a "bloc" to buyers (section 3.4.1); the use case description is section 3.3.7.

This subsection gives the full set of models for UC6, the one use case its owner holds. The same refund worker also pays back a payment that UC3 or UC4 refused after the money was taken (section 3.4.1, Figure 3.46); only the way the obligation is created differs.

UC6 has three major flows:

1. **Expire a team and record its refunds.** The scheduler finds an overdue open team; the system expires it, cancels its orders and records the refund obligations.
2. **Pay one refund.** The scheduler wakes the refund worker, which claims one due obligation and asks the provider to refund it. A temporary provider failure (retry later) and a refund that stops part-way (repeat with the same request identity) are `alt` fragments and a note of this flow, because they use the same lifelines and differ only in the provider's answer.
3. **Reconcile a parked refund.** An operations reviewer checks a refund that the worker parked for review against the provider's records. This exceptional flow is a major flow of its own, because a different actor starts it and it never creates a refund.

The recovery mechanisms these flows rely on (Idempotency Key, Retry and operator review) are described once, as part of the external interface strategy in section 3.2.5. The way expiry reaches orders and payments through a domain event is the Observer pattern of section 3.5.2.

#### A Analysis class diagram

The model uses only «boundary», «control» and «entity» classes, with attributes on entities only. The scheduler, the payment provider, the operations reviewer and the buyer are actors; each talks to the system through one boundary. Refund is the refund obligation: a promise to return one succeeded payment. RefundAttempt is the audit record of each try.

```mermaid
classDiagram
    class SchedulerTrigger {
        <<boundary>>
        +teamsDue()
        +refundsDue()
    }
    class ResolveTeamControl {
        <<control>>
        +expireOverdueTeams()
    }
    class RefundControl {
        <<control>>
        +payNextRefund()
        +reconcile(payment, providerRef)
        +refundStatus(payment)
    }
    class PaymentGateway {
        <<boundary>>
        +refund(payment, amount, requestId)
        +findRefund(requestId)
        +retrieveRefund(providerRef)
    }
    class ReviewUI {
        <<boundary>>
        +reconcile(payment, providerRef)
    }
    class ReceiptUI {
        <<boundary>>
        +showRefundStatus(payment)
    }
    class Team {
        <<entity>>
        -status: TeamStatus
        -expireTime: DateTime
        +expire(now)
    }
    class Order {
        <<entity>>
        -status: OrderStatus
        +cancel()
    }
    class Membership {
        <<entity>>
        -joinTime: DateTime
    }
    class Payment {
        <<entity>>
        -amount: Money
        -status: PaymentStatus
        -providerRef: String
    }
    class Refund {
        <<entity>>
        -amount: Money
        -reason: String
        -status: RefundStatus
        -providerRefundRef: String
        -nextAttemptTime: DateTime
    }
    class RefundAttempt {
        <<entity>>
        -result: String
        -time: DateTime
    }
%% at SchedulerTrigger 1,0
%% at ReviewUI 2,0
%% at ReceiptUI 3,0
%% at ResolveTeamControl 0,1
%% at RefundControl 2,1
%% at PaymentGateway 3,1
%% at Team 0,2
%% at Order 1,2
%% at Refund 2,2
%% at RefundAttempt 3,2
%% at Membership 0,3
%% at Payment 1,3

    SchedulerTrigger "1" -- "1" ResolveTeamControl
    SchedulerTrigger "1" -- "1" RefundControl
    ReviewUI "1" -- "1" RefundControl
    ReceiptUI "1" -- "1" RefundControl
    RefundControl "1" -- "1" PaymentGateway
    ResolveTeamControl --> Team
    ResolveTeamControl --> Order : cancels
    ResolveTeamControl ..> Refund : «create»
    RefundControl --> Refund
    RefundControl --> Payment : verifies
    RefundControl ..> RefundAttempt : «create»
    Team "1" *-- "1..*" Membership
    Order "0..*" -- "1" Team
    Membership "0..1" -- "1" Payment
    Refund "0..1" -- "1" Payment
    Refund "1" *-- "0..*" RefundAttempt
```

Figure 3.51: UML analysis class diagram of UC6 Refund on team expiry. Two controls split the use case at the point where money moves: ResolveTeamControl only records what is owed, and RefundControl alone talks to the payment provider; each Payment owes at most one Refund.

#### B Analysis sequence diagrams

The payment provider talks only to the PaymentGateway boundary, so the sequence diagrams leave its lifeline out and show its part as a note.

Figure 3.52 is flow 1. Expiring the team, cancelling its orders and recording the refunds form one unit of work: either all of them happen or none does, so an expired team is never left without a record of the money it owes.

```mermaid
sequenceDiagram
    actor Clock as Scheduler
    participant Trig as SchedulerTrigger (boundary)
    participant Ctl as ResolveTeamControl (control)
    participant Team as Team (entity)
    participant Ord as Order (entity)
    participant Ref as Refund (entity)

    Clock-)Trig: tick
    Trig->>Ctl: expireOverdueTeams()
    loop each open team past its deadline
        Note over Ctl,Ref: one unit of work per team
        Ctl->>Team: expire(now)
        alt still open and deadline passed
            Team-->>Ctl: expired
            loop each pending order of the team
                Ctl->>Ord: cancel()
            end
            loop each succeeded payment of a member
                Ctl-->>Ref: «create» (payment, TEAM_EXPIRED)
            end
        else already resolved by a last-seat join or another run
            Team-->>Ctl: unchanged, nothing owed
        end
    end
```

Figure 3.52: UML analysis sequence diagram of UC6 flow 1, expire a team and record its refunds. The `alt` fragment makes a repeated or late expiry harmless, and every paid member gets exactly one Refund in the same unit of work as the expiry.

Figure 3.53 is flow 2. The worker takes one due Refund at a time. It checks the payment's facts before it asks for money back and checks the provider's answer before it believes it.

```mermaid
sequenceDiagram
    actor Clock as Scheduler
    participant Trig as SchedulerTrigger (boundary)
    participant Ctl as RefundControl (control)
    participant Ref as Refund (entity)
    participant Pay as Payment (entity)
    participant PG as PaymentGateway (boundary)
    participant Att as RefundAttempt (entity)

    Clock-)Trig: tick
    Trig->>Ctl: payNextRefund()
    Ctl->>Ref: claimNextDue()
    Ref-->>Ctl: refund, claimed for a short time
    Ctl->>Pay: check succeeded, amount, currency
    break payment facts do not match
        Ctl->>Ref: parkForReview(reason)
    end
    Ctl->>PG: refund(payment, amount, requestId)
    Note over PG,Att: the provider refunds the original payment
    alt answer matches the payment and the Refund
        PG-->>Ctl: providerRef, status
        Ctl->>Ref: record(status)
    else temporary provider failure
        PG-->>Ctl: retryable failure
        Ctl->>Ref: retryLater()
    else permanent refusal or mismatched answer
        PG-->>Ctl: refusal or mismatch
        Ctl->>Ref: parkForReview(reason)
    end
    Ctl-->>Att: «create» (result)
    Note over Ctl,Ref: if the worker stops before record, the claim lapses and a later tick repeats the request with the same requestId
```

Figure 3.53: UML analysis sequence diagram of UC6 flow 2, pay one refund. The `alt` fragment holds the exceptional flows: a temporary failure keeps the Refund due, and an unsafe answer parks it; the same request identity makes a repeat harmless.

Figure 3.54 is flow 3. It only looks up a refund that already exists at the provider; it never asks for a new one, so it cannot pay a buyer twice.

```mermaid
sequenceDiagram
    actor Reviewer as Operations reviewer
    participant RUI as ReviewUI (boundary)
    participant Ctl as RefundControl (control)
    participant Ref as Refund (entity)
    participant Pay as Payment (entity)
    participant PG as PaymentGateway (boundary)
    participant Att as RefundAttempt (entity)

    Reviewer->>RUI: reconcile(payment, providerRef)
    RUI->>Ctl: reconcile(payment, providerRef)
    Ctl->>Ref: claimForReview(payment)
    Ctl->>Pay: check succeeded, amount, currency
    Ctl->>PG: retrieveRefund(providerRef)
    alt provider refund matches the Refund
        PG-->>Ctl: providerRef, status
        Ctl->>Ref: record(status)
    else no match or provider error
        Ctl->>Ref: keep in review
    end
    Ctl-->>Att: «create» (result)
    Ctl-->>RUI: outcome
```

Figure 3.54: UML analysis sequence diagram of UC6 flow 3, reconcile a parked refund. The reviewer can only confirm an existing provider refund; any doubt leaves the Refund in review.

The buyer's last step, reading the refund status on the payment receipt, is a single query (`ReceiptUI` asks `RefundControl.refundStatus`) and is not drawn as a flow.

#### C Design class diagram

Expiry is a group-buy decision, while the obligation and the provider interaction belong to the payment module. The design class diagram is therefore drawn in two parts: the expiry side (Figure 3.55) and the refund worker (Figure 3.56). Each class carries the tag of the transition strategy of section 3.2 that produced it: Pres 3.2.2 presentation, Dist 3.2.3 distribution, Pers 3.2.4 persistence, Ext 3.2.5 external interface, Sec 3.2.6 security, Sched 3.2.7 scheduling, Conc 3.2.8 concurrency, Evt 3.2.9 domain events. `…` stands for parameters left out to keep a box narrow.

```mermaid
classDiagram
    class TeamExpirySweepJob {
        <<Sched>>
        +run()
    }
    class LeasedJobRunner {
        <<Sched, Conc>>
        +run(job, lease, work)
    }
    class GroupDealExpirySweeper {
        <<Sched>>
        +sweep(limit, leaseValid) Result
    }
    class GroupDealExpiryService {
        <<Conc, Evt>>
        +expireOne(dealId) boolean
    }
    class PaidGroupDealRepository {
        <<interface, Pers, Conc>>
        +dueForExpiry(now, limit)
        +lockDeal(dealId)
        +currentTime() Instant
        +expire(before, after, now)
        +deferExpiryRetry(dealId, delay)
    }
    class GroupDeal {
        <<Pers>>
        +expire(now) GroupDeal
    }
    class TeamExpired {
        <<Evt>>
        +dealId
        +productId
        +at
    }
    class OrderLifecycleSubscriber {
        <<Evt>>
        +onTeamExpired(event)
    }
    class TeamExpiryRefundSubscriber {
        <<Evt>>
        +onTeamExpired(event)
    }
    class TeamExpiryRefundService {
        <<Dist>>
        +recordExpiredTeam(dealId)
    }
    class PaymentBusinessPort {
        <<interface, Dist>>
        +lockExpiredTeam(dealId)
    }
    class CheckoutPaymentRepository {
        <<interface, Pers>>
        +requireRefundsForExpiredTeam(…)
    }
    class CheckoutPaymentRepositoryImpl {
        <<Pers, Conc>>
    }
    class PaymentMapper {
        <<interface, Pers, Conc>>
        +lockRefundableMemberIds(dealId)
        +refundExpiredTeam(dealId, …)
    }
%% at TeamExpirySweepJob 0,0
%% at LeasedJobRunner 1,0
%% at GroupDealExpirySweeper 0,1
%% at PaidGroupDealRepository 1,1
%% at GroupDealExpiryService 0,2
%% at GroupDeal 1,2
%% at TeamExpired 0,3
%% at OrderLifecycleSubscriber 1,3
%% at TeamExpiryRefundSubscriber 0,4
%% at TeamExpiryRefundService 1,4
%% at PaymentBusinessPort 2,4
%% at CheckoutPaymentRepository 1,5
%% at CheckoutPaymentRepositoryImpl 2,5
%% at PaymentMapper 2,6

    TeamExpirySweepJob --> LeasedJobRunner
    TeamExpirySweepJob --> GroupDealExpirySweeper
    GroupDealExpirySweeper --> PaidGroupDealRepository
    GroupDealExpirySweeper --> GroupDealExpiryService
    GroupDealExpiryService --> PaidGroupDealRepository
    GroupDealExpiryService ..> GroupDeal : expire
    GroupDealExpiryService ..> TeamExpired : publishes
    OrderLifecycleSubscriber ..> TeamExpired : Order 10
    TeamExpiryRefundSubscriber ..> TeamExpired : Order 20
    TeamExpiryRefundSubscriber --> TeamExpiryRefundService
    TeamExpiryRefundService --> PaymentBusinessPort
    TeamExpiryRefundService --> CheckoutPaymentRepository
    CheckoutPaymentRepositoryImpl ..|> CheckoutPaymentRepository
    CheckoutPaymentRepositoryImpl --> PaymentMapper
```

Figure 3.55: UML design class diagram of UC6, part 1, expiry. The group-buy module publishes the immutable `TeamExpired` event and never touches orders or payments; the order and payment subscribers react before the transaction commits, in the fixed order 10 then 20.

`GroupDealExpiryService.expireOne` resolves exactly one team per call, under its own 10-second transaction; the batch loop lives in `GroupDealExpirySweeper`, so the row lock covers one team at a time. A team whose expiry fails is retried five minutes later (`deferExpiryRetry`) and does not stop the others. `TeamExpiryRefundService` reaches the group-buy module only through `PaymentBusinessPort.lockExpiredTeam`, which takes the same team row lock that joins use and checks that the team really is EXPIRED. `CheckoutPaymentRepositoryImpl` then locks the team's refundable payments in a fixed order (`PaymentMapper.lockRefundableMemberIds`) and inserts one `biz_refund` row per payment with `PaymentMapper.refundExpiredTeam`, an upsert on `ON CONFLICT (payment_id)`. If the number of rows written differs from the number of payments locked, it throws, and the whole expiry rolls back.

```mermaid
classDiagram
    class RefundWorkerJob {
        <<Sched>>
        +run()
    }
    class LeasedJobRunner {
        <<Sched, Conc>>
        +run(job, lease, work)
    }
    class RefundService {
        <<Ext, Conc>>
        +processNext() boolean
        +reconcile(paymentId, providerId) boolean
    }
    class RefundWork {
        <<Ext>>
        +providerKey() String
        +valid() boolean
    }
    class RefundRepository {
        <<interface, Pers, Conc>>
        +claimNext() RefundWork
        +claimReview(paymentId) RefundWork
        +finish(work, providerId, status, result)
        +status(paymentId) String
    }
    class RefundGateway {
        <<interface, Ext>>
        +createRefund(work) Snapshot
        +retrieveRefund(providerId) Snapshot
        +findRefund(work) Snapshot
    }
    class RefundRepositoryImpl {
        <<Pers>>
    }
    class StripeRefundGateway {
        <<Ext>>
    }
    class RefundMapper {
        <<interface, Pers, Conc>>
        +claimNext() RefundWork
        +finish(work, providerId, …) int
        +audit(work, result) int
    }
    class PaymentBrowserService {
        <<Sec>>
        +receipt(user, id) Receipt
    }
%% at LeasedJobRunner 0,0
%% at RefundWorkerJob 1,0
%% at PaymentBrowserService 2,0
%% at RefundWork 0,1
%% at RefundService 1,1
%% at RefundRepository 2,1
%% at RefundGateway 1,2
%% at RefundRepositoryImpl 2,2
%% at StripeRefundGateway 1,3
%% at RefundMapper 2,3

    RefundWorkerJob --> LeasedJobRunner
    RefundWorkerJob --> RefundService
    RefundService ..> RefundWork
    RefundService --> RefundRepository
    RefundService --> RefundGateway : outside DB transactions
    PaymentBrowserService --> RefundRepository : status
    StripeRefundGateway ..|> RefundGateway
    RefundRepositoryImpl ..|> RefundRepository
    RefundRepositoryImpl --> RefundMapper
```

Figure 3.56: UML design class diagram of UC6, part 2, the refund worker. `RefundService` holds no transaction across the provider call: it claims and finishes in two short transactions, and the stable `RefundWork.providerKey()` is the idempotency key sent to Stripe.

`RefundWork` is the immutable snapshot that a claim returns: the obligation joined with its payment's status, amount, currency and Stripe PaymentIntent. Its `valid()` checks that the payment succeeded and that amount and currency agree. Its `providerKey()` is `blocbuy-refund-` plus the obligation's identifier, so every attempt for one obligation sends Stripe the same idempotency key. `StripeRefundGateway.createRefund` re-reads the original PaymentIntent and refunds it, and tags the refund with the obligation's identifier as metadata; `findRefund` lists the PaymentIntent's refunds and accepts only a single one with that tag. The buyer's receipt reads the refund status through `PaymentBrowserService.receipt`, which serves only the payment's owner.

The analysis classes map onto these design classes as follows. SchedulerTrigger becomes the two Spring jobs `TeamExpirySweepJob` and `RefundWorkerJob`, each run under a database job lease by `LeasedJobRunner`. ResolveTeamControl is split across three modules: `GroupDealExpirySweeper` and `GroupDealExpiryService` in group-buy, `OrderLifecycleSubscriber` in order, and `TeamExpiryRefundSubscriber` with `TeamExpiryRefundService` in payment. RefundControl becomes `RefundService`. PaymentGateway becomes the port `RefundGateway` with the adapter `StripeRefundGateway`. Refund becomes a `biz_refund` row behind `RefundRepository`, and RefundAttempt a `log_refund_attempt` row written by `RefundMapper.audit`. ReceiptUI becomes the receipt page described in Table 3.15. ReviewUI has no design element yet: no endpoint or command calls `RefundService.reconcile` (part D).

#### D Design sequence diagrams

Each design sequence refines the analysis sequence of the same flow: Figure 3.57 refines Figure 3.52, Figure 3.58 refines Figure 3.53, and Figure 3.59 refines Figure 3.54. A `critical` fragment marks a database transaction, because UML has no transaction fragment.

```mermaid
sequenceDiagram
    participant Sweeper as :GroupDealExpirySweeper
    participant Expiry as :GroupDealExpiryService
    participant Orders as :OrderLifecycleSubscriber
    participant Refunds as :TeamExpiryRefundService
    participant Repo as :PaidGroupDealRepository

    Note over Sweeper,Expiry: TeamExpirySweepJob, every minute, under the job lease
    Sweeper->>Repo: dueForExpiry(now, limit)
    loop each due team while the lease is valid
        Sweeper->>Expiry: expireOne(dealId)
        critical expiry transaction, 10 s
            Expiry->>Repo: lockDeal(dealId) FOR UPDATE
            Expiry->>Repo: currentTime()
            Expiry->>Expiry: GroupDeal.expire(now)
            Expiry->>Repo: expire(before, after, now)
            Note over Repo: UPDATE … WHERE status = 'open'
            break already resolved, DEAL_CLOSED
                Expiry-->>Sweeper: false
            end
            Expiry->>Orders: TeamExpired, BEFORE_COMMIT listener
            Orders->>Orders: cancelByDealId, pending only
            Expiry->>Refunds: TeamExpired, BEFORE_COMMIT listener
            Refunds->>Refunds: lockExpiredTeam(dealId)
            Refunds->>Refunds: requireRefundsForExpiredTeam
        end
        Expiry-->>Sweeper: true
    end
```

Figure 3.57: UML design sequence diagram of UC6 flow 1, expire a team and record its refunds. The status-guarded update decides a race with a last-seat join, and both subscribers write inside the expiry transaction, so expiry, order cancellation and refund obligations commit together or not at all.

The lock order matches the paid join of section 3.4.1: both lock the team row first, so a sweep and a settling webhook cannot deadlock (backend ADR 0002). If the sweep wins, a payment that settles afterwards finds the team expired and is owed a refund in its own transaction. If the join wins, the guarded update changes zero rows, `PaidGroupDealRepository.expire` reports `DEAL_CLOSED`, and `expireOne` returns false.

```mermaid
sequenceDiagram
    participant Job as :RefundWorkerJob
    participant Svc as :RefundService
    participant Repo as :RefundRepository
    participant GW as :RefundGateway

    Note over Job,Svc: every 30 s, under the refund-worker lease
    Job->>Svc: processNext()
    critical claim transaction, 5 s
        Svc->>Repo: claimNext()
        Note over Repo,GW: due pending or processing row, FOR UPDATE SKIP LOCKED, 2-minute row lease, version + 1, audit CLAIMED
    end
    Repo-->>Svc: RefundWork
    break work.valid() is false
        Svc->>Repo: finish(work, review, PAYMENT_MISMATCH)
    end
    alt provider refund id already stored
        Svc->>GW: retrieveRefund(providerId)
    else 23 hours since the first attempt
        Svc->>GW: findRefund(work)
    else otherwise
        Svc->>GW: createRefund(work), key providerKey()
    end
    alt snapshot matches the obligation
        GW-->>Svc: Snapshot
        Svc->>Repo: finish(work, id, succeeded, processing or failed)
    else retryable provider error
        GW-->>Svc: PAYMENT_PROVIDER_ERROR
        Svc->>Repo: finish(work, processing, PROVIDER_RETRY)
    else refusal, mismatch, or nothing found after 23 hours
        GW-->>Svc: refusal or mismatching snapshot
        Svc->>Repo: finish(work, review, reason)
    end
    Note over Svc,Repo: each finish is its own 5 s transaction, fenced by id and version, next attempt 30 s later
```

Figure 3.58: UML design sequence diagram of UC6 flow 2, pay one refund. The provider call runs between two short transactions, never inside one; a crash after Stripe accepts the refund leaves the row claimable once its lease lapses, and the next attempt sends the same idempotency key.

The `alt` fragments are the exceptional flows of section 3.3.7. A retryable provider error (transport, rate limit, availability) moves the obligation to `processing`, due again 30 seconds later; the delay is fixed, not exponential. A permanent refusal (`PAYMENT_STATE_INVALID` or `PAYMENT_PROVIDER_REJECTED`) or a snapshot that does not match the obligation moves it to `review`, which the worker never claims again. Stripe may forget an idempotency key after about a day, so after 23 hours without a stored refund identifier the worker stops creating and only searches; if it finds nothing, the obligation goes to `review`. A worker that stops after Stripe accepted the refund but before `finish` leaves the row with an expired lease; the next claim increments the version, so a late `finish` from the old worker changes nothing, and the repeat request with the same key returns the same Stripe refund.

```mermaid
sequenceDiagram
    actor Op as Operator
    participant Svc as :RefundService
    participant Repo as :RefundRepository
    participant GW as :RefundGateway

    Note over Op,Svc: no endpoint or command; a separately reviewed invocation
    Op->>Svc: reconcile(paymentId, providerId)
    critical claim transaction, 5 s
        Svc->>Repo: claimReview(paymentId)
        Note over Repo,GW: review or failed row only, audit OPERATOR_RECONCILE
    end
    break work.valid() is false
        Svc->>Repo: finish(work, review, PAYMENT_MISMATCH)
    end
    Svc->>GW: retrieveRefund(providerId)
    alt snapshot matches the obligation
        GW-->>Svc: Snapshot
        Svc->>Repo: finish(work, id, succeeded, processing or failed)
    else provider error or mismatch
        Svc->>Repo: finish(work, review, reason)
    end
    Svc-->>Op: true
```

Figure 3.59: UML design sequence diagram of UC6 flow 3, reconcile a parked refund. `reconcile` only retrieves a refund the operator names and maps it with the same checks as the worker; it has no path that creates a refund.

`RefundService.reconcile` exists and is unit-tested, but no endpoint, command or job calls it: it has no HTTP endpoint, the operator command runner `stripe-ops` (`StripeOperationsConfiguration`) does not expose it, and the rollout runbook (`backend/docs/payment/stripe-rollout-runbook.md`) requires a separately reviewed invocation. Flow 3 is therefore designed and unit-tested but has never been run on dev.

#### Statechart of the refund obligation

A refund obligation is one row of `biz_refund`. It is created when a team expires (this subsection) or when a paid settlement is refused (section 3.4.1). A buyer's receipt shows its status.

```mermaid
stateDiagram-v2
    state outcome <<choice>>
    [*] --> pending : / one row per payment
    pending --> outcome : tick [due] / ask provider
    processing --> outcome : tick [due] / ask provider
    review --> outcome : reconcile / retrieve
    failed --> outcome : reconcile / retrieve
    outcome --> succeeded : [SUCCEEDED]
    outcome --> processing : [PENDING or retryable error]
    outcome --> failed : [FAILED or CANCELED]
    outcome --> review : [mismatch, refusal, 23 h]
    succeeded --> [*]
%% at __start0 0,0
%% at pending 0,1
%% at processing 2,0
%% at review 4,1
%% at failed 4,3
%% at outcome 2,2
%% at succeeded 0,3
%% at __end0 0,4
```

Figure 3.60: UML statechart of the refund obligation (`biz_refund.status`). Every attempt passes through one choice on the provider's answer; only `pending` and `processing` are claimed by the worker, while `review` and `failed` wait for an operator's reconcile.

A tick takes an obligation only when it is *due*: its next attempt time has passed and no live lease holds it. The choice reads the provider's answer: an unknown status also goes to `review`, and *23 h* is the search after 23 hours that finds no refund. Every transition out of the choice stamps the next attempt time 30 seconds later (one `finish` statement for all outcomes), but only a `processing` obligation is claimed again by the worker: `succeeded` and `failed` are never retried automatically, and `review` waits for an operator. A provider error during a reconcile leaves the obligation in `review`, so a case in an operator's hands never returns to the automatic worker. The states are plain strings, the constants of `RefundService`, with no transition table in code: the statechart is enforced by the claim and finish SQL in `RefundMapper.xml`. Of the objects in this use case, only the team checks its own transitions (`DealStatus.canTransitionTo`, section 3.4.5).

**Evidence.** On 23 October on dev, with scheduling enabled, a two-seat team started with a real Stripe Sandbox payment and was left to expire. It closed 18.9 seconds after its unedited deadline, its order was cancelled, and its one S$22 refund succeeded 40.6 seconds after the deadline; Stripe listed exactly one refund for the payment, and the buyer's receipt showed "Refund sent" (`backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md`). These times describe that run, not a guarantee: the design bound for expiry is one sweep interval plus the sweep's own time, and the run does not show that bound is met on dev in general. The same day, an operator recovered three paid payments that had been stranded by a webhook fault; each was recorded as refund required, because its purchase had expired, and the worker refunded each exactly once.

Tested locally (PostgreSQL 18, Stripe replaced by a test substitute), not on dev, on 3 October (`backend/docs/payment/stripe-refund-recovery-acceptance-20261003.md`). The rehearsal ran the eight `StripeRolloutAcceptanceIT` tests and the full Testcontainers integration suite. A refund whose result was lost before `finish` was reclaimed after its lease, retried with the same key and produced one refund; a signed refund notification delivered twice changed no payment, refund or audit row; and a permanent refusal was parked in `review` with no second provider call. {{CONFIRM: D18 Which evidence kind states crash recovery: tested locally, proven locally (Testcontainers and the 3 October rehearsal), or tested in CI (StripeRolloutAcceptanceIT)? — Yorik}} A deliberate crash, a replay and a permanent failure with operator review have not been exercised on dev. The unit tests `RefundServiceTest`, `TeamExpiryRefundSubscriberTest`, `TeamExpiryRefundServiceTest` and `GroupDealExpiryServiceTest` cover the worker's branches and the expiry service. The integration tests `StripeRolloutAcceptanceIT`, `TeamExpirySchedulingIT` and `V4ExpiryPipelineIT` run the real expiry, claim and persistence code against PostgreSQL; the last seeds two thousand teams and checks that each expires with exactly one effect.

#### E Compliance with the transition strategies

UC6 applies eight of the nine transition strategies of section 3.2. Language and framework (3.2.1) adds nothing specific to UC6 beyond the Spring scheduling and event annotations listed under 3.2.7 and 3.2.9.

Table 3.15: Transition strategies applied to UC6.

| Strategy | Analysis object | Design element |
| --- | --- | --- |
| 3.2.2 Presentation | «boundary» ReceiptUI | Receipt route `payments.$paymentId.tsx` with `receipt-view.ts` and `refund-status.ts`; the page polls while a refund is pending |
| 3.2.3 Distribution | «control» ResolveTeamControl; «boundary» ReceiptUI | Expiry split across the group-buy, order and payment modules, joined only by the `TeamExpired` event and the port `PaymentBusinessPort.lockExpiredTeam`; receipt served by `GET /api/v1/payments/{id}` on `StripeCheckoutController` |
| 3.2.4 Persistence | «entity» Refund, RefundAttempt, Team, Order | `biz_refund` behind `RefundRepository`, `RefundRepositoryImpl` and `RefundMapper`, created by `CheckoutPaymentRepository.requireRefundsForExpiredTeam` and `PaymentMapper.refundExpiredTeam`; `log_refund_attempt` written by `RefundMapper.audit`; team expiry by `GroupDeal.expire` and `PaidGroupDealRepository.expire` |
| 3.2.5 External interface | «boundary» PaymentGateway | Port `RefundGateway` with adapter `StripeRefundGateway`; idempotency key `RefundWork.providerKey()`; fixed 30-second retry of provider errors; `review` state for unsafe answers; search-only after 23 hours |
| 3.2.6 Security | «boundary» ReviewUI, ReceiptUI | No endpoint or command exposes `RefundService.reconcile`; `PaymentBrowserService.receipt` returns a receipt only to the payment's owner |
| 3.2.7 Scheduling | «boundary» SchedulerTrigger | `TeamExpirySweepJob` (fixed delay one minute) and `RefundWorkerJob` (fixed delay 30 seconds) under `LeasedJobRunner` job leases, active only when `blocbuy.scheduling.enabled` is true |
| 3.2.8 Concurrency | «control» ResolveTeamControl, RefundControl; «entity» Team, Refund | Team row lock and status-guarded `expireDeal`; refundable payments locked in a fixed order; `UNIQUE (payment_id)` with `ON CONFLICT`; claim with `FOR UPDATE SKIP LOCKED`, a 2-minute row lease and a version fence on `finish`; provider I/O outside transactions |
| 3.2.9 Domain events | «control» ResolveTeamControl (cancel orders, record refunds) | `GroupDealExpiryService` publishes `TeamExpired`; `OrderLifecycleSubscriber` (`@Order(10)`) and `TeamExpiryRefundSubscriber` (`@Order(20)`) react at `BEFORE_COMMIT` |
