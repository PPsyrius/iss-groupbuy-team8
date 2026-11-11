// Detailed Design: part of 03-solution-overview.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *
#import "appendix-a/lib-a.typ": afig

=== Detailed Design

This section shows, at class and object level, the two flows that move money under concurrency. The first is UC4 Join an existing team by paying, with UC3 Start a team and UC10 Pay upfront. The second is UC5 Resolve a team with UC6 Refund members of an expired team. They are critical because only in these flows do two actions race for the same money (HS-1, HS-4 and HS-8, §3.1.4). In both, the team's row lock decides the race, and a refused payment becomes a recorded refund obligation, never a lost payment.

*From analysis to design.* We first modelled each use case as an analysis model with boundary, control and entity (BCE) objects only. Each analysis object then became design classes through one or more of nine transition strategies (@tbl-strategies-324). A strategy names a design concern, the decisions it follows and what it adds to the model. Two matter most here: S4 Persistence gives each aggregate its own repository and tables, and S8 Concurrency fixes one lock order (team, purchase intent, payment) for every path that settles money (§3.1.4). @tbl-uc4-map-summary and @tbl-expiry-map-summary trace every analysis object of the two flows to its design classes. In the sequence diagrams, a `critical` fragment marks one database transaction, and "lock 1" is the team row lock.

#figure(
  table(
    columns: (1.15fr, 3fr, 1.1fr),
    table.header([Strategy], [What it adds to the design], [Follows]),
    [S1 Language and framework], [Java 21 and Spring Boot in COLA layers; entities as immutable aggregates that return a new snapshot], [AD-01, AD-18],
    [S2 Presentation], [React screens that call the API only through the generated client], [AD-09],
    [S3 Distribution], [Ports and module interfaces between modules; the one synchronous call, commerce to identity], [AD-02, AD-06],
    [S4 Persistence], [One repository port per aggregate, MyBatis adapters, one owning service per table], [AD-03, AD-11],
    [S5 External interface], [Adapters for Stripe and the other providers; no provider call inside a database transaction], [ADR 0001, 0004, 0005],
    [S6 Security], [Session checks, role checks and rate limits at every boundary], [AD-05, AD-09],
    [S7 Scheduling], [Time-triggered jobs that run under a database lease, one runner across tasks], [AD-16],
    [S8 Concurrency], [Row locks in one global order, status-guarded updates, idempotency records], [AD-02, AD-03],
    [S9 Domain and integration events], [In-process events before commit; integration events through the outbox, SNS and SQS], [AD-04],
  ),
  kind: table,
  caption: [Transition strategies from the analysis model to the design. Source: the AD register (§3.1.1, §3.2.1, §3.3) and backend ADRs 0001–0008.],
) <tbl-strategies-324>

==== Joining a team by paying

@fig-d11-uc4-design-classes shows the objects behind the webhook (strategies S3 and S8). The inbound adapter `StripeWebhookController` passes the raw request and signature through `PaymentAppService` (implemented by `StripePaymentAppService`) to #src("backend/blocbuy-application/src/main/java/com/blocbuy/payment/service/StripeCheckoutService.java")[`StripeCheckoutService`]. Before it opens the settlement transaction, the service verifies the signature and reads the Checkout session back from Stripe. Inside the transaction it first claims the event ID (`CheckoutPaymentRepository.claimEvent`), so a redelivered event stops there. It then locks the team for JOIN, the saved purchase intent and the payment, in the global order. Payment requests fulfilment through `PaymentBusinessPort`, which the order module implements in #src("backend/blocbuy-application/src/main/java/com/blocbuy/order/service/OrderFulfilmentAdapter.java")[`OrderFulfilmentAdapter`]\; payment never depends on order code. The team aggregate (`GroupDeal`) checks its deadline, remaining seats and existing members, and returns the updated team snapshot. #src("backend/blocbuy-infrastructure/src/main/java/com/blocbuy/groupbuy/persistence/PaidGroupDealRepositoryImpl.java")[`PaidGroupDealRepositoryImpl`] implements the lock with PostgreSQL through MyBatis. @tbl-uc4-map-summary shows where each class comes from.

#figure(
  table(
    columns: (1fr, 0.7fr, 2.8fr, 0.6fr),
    table.header([Analysis object], [Stereotype], [Design classes (layer)], [Strategy]),
    [`JoinTeamUI`], [boundary], [`StripeCheckoutController`: `intent`, `checkout` (adapter)], [S2, S6],
    [`PayUpfrontControl`], [control (UC10)], [`StripeWebhookController` → `PaymentAppService` → `StripeCheckoutService.handleWebhook`, `settle` (adapter, application)], [S3, S8],
    [`JoinTeamControl`, before payment], [control], [`PurchaseIntentAppService.createIntent`, which implements `PaymentIntentPort`; `GroupDealPurchaseSupportImpl` (application)], [S3, S6],
    [`JoinTeamControl`, after payment], [control], [`OrderFulfilmentAdapter.lockForSettlement`, `fulfil`, which implements `PaymentBusinessPort`; `PaidGroupDealService.joinConfirmed` (application)], [S3, S8],
    [`ResolveTeamControl`], [control (UC5)], [`TeamSucceeded`, published by `PaidGroupDealService`; `OrderLifecycleSubscriber.onTeamSucceeded` (domain, application)], [S9],
    [`Account`], [entity], [Port `UserAppService`; adapter `RemoteUserAppService` calls identity-svc (application)], [S3],
    [`Product`], [entity], [Module interface `CatalogPurchaseSupport`: `requirePurchasable`, `consumeOneUnit` (application)], [S3, S4],
    [`Team`], [entity], [Aggregate root `GroupDeal`: `joinPaid` returns `PaidAdmission` (domain); `PaidGroupDealRepository` (domain port), `PaidGroupDealRepositoryImpl`, `PaidGroupDealMapper` (infrastructure)], [S4, S8],
    [`Membership`], [entity], [A `biz_group_member` row of the `GroupDeal` aggregate, written by `insertJoinedMember`; `PaidParticipation`, `ParticipationResult` (domain)], [S1, S4, S8],
    [`PurchaseIntent`], [entity], [Aggregate root `PurchaseIntent`, port `PurchaseIntentRepository` (domain)], [S4, S8],
    [`Payment`], [entity], [Aggregate root `CheckoutPayment`, port `CheckoutPaymentRepository` (domain); `CheckoutPaymentRepositoryImpl` (infrastructure)], [S4, S8],
    [`Order`], [entity], [Aggregate root `Order`, port `OrderRepository` (domain)], [S4, S9],
    [`Refund`], [entity], [Created by `CheckoutPaymentRepository.requireRefund`; its lifecycle is UC6's], [S4],
    [`Invitation`], [entity], [Aggregate root `ShareInvite`, port `ShareInviteRepository`; after commit `ShareCreditSubscriber`], [S4, S9],
    [(none)], [—], [Added by a strategy: `PaymentBusinessPort`, `PaymentIntentPort`, `GroupDealSettlementSupport` (S3); `RateLimitGateway` (S6); `PaymentSucceeded`, `MemberJoined`, `TeamSucceeded`, `IntegrationOutboxWriter` (S9)], [S3, S6, S9],
  ),
  kind: table,
  caption: [UC4 analysis objects and the design classes that realise them, with the transition strategy that produced each. Source: code; strategies in @tbl-strategies-324.],
) <tbl-uc4-map-summary>

#afig("d11-uc4-design-classes", [UC4 design view (UML class diagram). Rows are the COLA layers, adapter to infrastructure; no dependency leaves the domain, and payment reaches the order module only through `PaymentBusinessPort`.])

*Refusal and retry.* In @fig-d12b-two-buyers-join the business port returns a rejection for a full team. The payment service then commits the obligation to refund the losing buyer and creates no order. A technical error instead rolls back the event claim and the business writes, so a webhook redelivery can settle the payment later. After a successful commit, a repeated event ID does nothing. The fulfilment adapter saves the order, links it to the membership, marks the intent fulfilled and consumes stock in the same transaction. When the payment fills the last seat, the order listener runs before commit, after the last buyer's order exists, so both pending orders become confirmed. Tested locally, not on AWS: #src("backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/StripeLocalIntegrationTest.java")[`StripeLocalIntegrationTest`] races 20 buyers for one seat and leaves one member and 19 refund obligations (`twentyBuyersRaceForLastSeatAndNineteenRefundObligationsPersist`); eight concurrent replays of one webhook still produce one member and one order. The settlement order is recorded in #src("backend/docs/adr/0001-payment-business-integration.md")[ADR 0001].

==== Two buyers, one team

@fig-d12a-two-buyers-start and @fig-d12b-two-buyers-join follow two buyers through UC3 START and UC4 JOIN. The leader pays for a two-seat listing and shares the invite link. After the leader's payment the team has one paid member. The second buyer, the joiner, opens the invite link and pays; that payment fills the second seat. Each buyer sees their own order; the team's status is shared. We recorded this flow on 24 October on the SWE5006 monolith on EC2 (same code, `monolith` topology; §6.6). It has not been run on the three-service runtime (§2.2). @tbl-two-buyer-walkthrough lists the recorded steps.

#figure(
  table(
    columns: (0.8fr, 1.5fr, 1.5fr, 1.6fr),
    table.header([Step], [Leader (buyer A)], [Joiner (buyer B)], [Shared outcome]),
    [Start], [Selects the two-seat listing, pays in Stripe test mode and sees the confirmation], [Not yet joined], [Open team, one of two seats paid; the leader's order waits for the team],
    [Invite], [Shares the invite link of the same team], [Opens the invite link in a separate account and browser session], [Same team; no second START],
    [Join], [Pays nothing more], [Pays S\$22.00 in Stripe test mode and sees "Payment received"], [The UI shows "2 of 2 joined · Filled"; both orders are confirmed],
    [Own views], [Sees only their own orders and teams], [Sees only their own orders and teams], [The team's status is shared; neither buyer sees the other's payment or order],
  ),
  kind: table,
  caption: [Two-buyer walkthrough recorded on 24 October on the SWE5006 monolith on EC2 (recorded UI observations, not a database audit). Source: \#590, \#657.],
) <tbl-two-buyer-walkthrough>

#afig("d12a-two-buyers-start", [Two-buyer interaction view, part 1 (UML sequence diagram). One settlement transaction opens the team with the leader as its first paid member; the leader then shares the invite link, and the Stripe webhook is a synchronous HTTP call.])

#afig("d12b-two-buyers-join", [Two-buyer interaction view, part 2 (UML sequence diagram). Settlement locks team, intent, then payment; under the team lock `GroupDeal.joinPaid` fills the last seat, or, when another buyer took it first, the full team leaves a refund obligation and no order.])

@fig-race-last-seat shows two payments racing for the last seat. Both webhooks reach settlement at once, but the team lock admits one transaction at a time: the first commits the join and fills the team, and the second, granted the lock only after that commit, finds the team closed and records a refund obligation instead of an order.

#afig("a-uc04-ds-race", lbl: <fig-race-last-seat>, [Last-seat race interaction view (UML sequence diagram). The team lock serialises the two settlements, so the second payment finds the team full and commits a refund obligation, not an order.])

==== Team expiry and refunds

@fig-d13-expiry-design-classes shows why expiry and refund execution are separate (strategies S7, S8 and S9). Under the scheduler lease, the sweeper calls `expireOne` for each candidate team. The expiry service locks the team and reads the database clock again. If a concurrent join has already filled the team, expiry returns without publishing an event. If expiry wins, it writes the expired state and publishes `TeamExpired` in the same transaction. Before commit, the transaction owner calls the subscribers in `@Order`: the order subscriber (10), the refund subscriber (20) and, when integration events are enabled, the outbox writer (lowest precedence), as @fig-d14a-team-expiry shows. A failure in one subscriber rolls back that team's expiry, cancellations and refund obligations together. A join paid after the deadline, or after the sweep has taken the team, never reaches the team: settlement sees the expired quote and records `BUSINESS_EXPIRED`, so that payment is owed a refund. The expiry transaction is in #src("backend/blocbuy-application/src/main/java/com/blocbuy/groupbuy/service/GroupDealExpiryService.java")[`GroupDealExpiryService`], and the subscriber order is recorded in #src("backend/docs/adr/0002-team-lifecycle-events.md")[ADR 0002]. @tbl-expiry-map-summary traces the analysis objects.

#figure(
  table(
    columns: (1.1fr, 0.7fr, 3.1fr, 0.75fr),
    table.header([Analysis object], [Stereotype], [Design elements], [Strategies]),
    [`SchedulerTrigger`], [boundary], [`TeamExpirySweepJob` (every minute) and `RefundWorkerJob` (every 30 s) in bootstrap scheduling; each runs under `LeasedJobRunner`, whose lease row is in `sys_scheduler_lock`.], [S7],
    [`ResolveTeamControl` (selection)], [control], [`GroupDealExpirySweeper.sweep`: up to 100 due teams, no transaction, a failed team deferred for 5 minutes.], [S1, S7],
    [`ResolveTeamControl` (one team)], [control], [`GroupDealExpiryService.expireOne`: one 10 s transaction that locks the team first and reads the clock after the lock; publishes `TeamExpired` through `ApplicationEventPublisher`.], [S1, S8, S9],
    [`Team`], [entity], [Aggregate root `GroupDeal` with `DealStatus`; `expire` becomes `GroupDeal.expire`. Port `PaidGroupDealRepository`, table `biz_group_deal`.], [S1, S4, S8],
    [`Order`], [entity], [`OrderLifecycleSubscriber.onTeamExpired` (`@Order(10)`) calls `OrderRepository.cancelByDealId`, a status-guarded update of `biz_order`.], [S4, S9],
    [`RefundMembersControl` (record)], [control], [`TeamExpiryRefundSubscriber.onTeamExpired` (`@Order(20)`) calls `TeamExpiryRefundService.recordExpiredTeam`. It records one obligation per paid member through `requireRefundsForExpiredTeam` on `CheckoutPaymentRepository`, in the expiry transaction.], [S3, S8, S9],
    [`RefundMembersControl` (pay)], [control], [`RefundService.processNext`: claim in one short transaction, call the provider outside any transaction, record the answer with `finish` in another. Each attempt makes the row due again after 30 s; after 23 h the worker only looks a refund up.], [S5, S7, S8],
    [`PaymentGateway`], [boundary], [Port `RefundGateway`, adapter `StripeRefundGateway`; the idempotency key is `RefundWork.providerKey`.], [S5, S6],
    [`Refund`], [entity], [One `biz_refund` row behind `RefundRepository`; a claim returns the snapshot `RefundWork`.], [S1, S4, S8],
    [(none)], [—], [`IntegrationOutboxWriter.onTeamExpired` (lowest precedence) writes `TeamExpiredV1` to `sys_outbox` in the expiry transaction; `OutboxRelayService` later publishes it to SNS.], [S9],
  ),
  kind: table,
  caption: [Analysis-to-design mapping for team expiry with refund (UC5 and UC6). Source: code; strategies in @tbl-strategies-324.],
) <tbl-expiry-map-summary>

#afig("d13-expiry-design-classes", [Expiry design view (UML class diagram). The expiry service only publishes `TeamExpired`; a separate job pays each refund obligation later.])

#afig("d14a-team-expiry", [Team expiry interaction view (UML sequence diagram). Each team expires in its own `critical` transaction: the team lock, the clock read under it, `GroupDeal.expire`, then the transaction owner calls the `BEFORE_COMMIT` subscribers in `@Order`, so the orders, the refund obligations and the outbox row commit or roll back with the expiry.])

*Paying the buyer back.* @fig-d14b-refund-retry traces what runs after the expiry commits. The refund worker claims an obligation in one short transaction, calls Stripe outside any transaction and records the answer in another. It derives the provider key from the refund ID (#src("backend/blocbuy-domain/src/main/java/com/blocbuy/payment/model/RefundWork.java")[`RefundWork`]). If it knows the provider refund ID, it retrieves that refund instead of creating one. From 23 hours after the first attempt it only looks the refund up and sends an uncertain outcome for review. Each attempt makes the obligation due again after a fixed 30 s, so a pending or failed call is retried until Stripe gives a final answer. Beside the worker, the outbox relay publishes the expiry through SNS, which delivers it asynchronously to the notification queue; notification can lag or retry independently of repayment. Tested locally, not on AWS: #src("backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/TeamExpirySchedulingIT.java")[`TeamExpirySchedulingIT`] covers the rollback, the retry and the reused provider key.

#afig("d14b-refund-retry", [Refund and relay interaction view (UML sequence diagram). After the commit two leased jobs run on their own; no Stripe or SNS call runs inside a database transaction.], float: true)

==== Patterns in the two flows

@tbl-patterns-324 lists the patterns in the two flows in a structured form: the problem, the roles and the classes that play them, and the consequence. Only Observer is a GoF pattern; the other three are architectural or integration patterns.

#figure(
  table(
    columns: (1fr, 1.3fr, 1.8fr, 1.4fr),
    table.header([Pattern (kind)], [Problem], [Roles and classes], [Consequence and liability]),
    [Observer (GoF, behavioural), realised with Spring application events (publish–subscribe)],
    [A team's success or expiry must confirm or cancel orders, owe refunds, write an integration record, credit the sharer and evict a cache, without the publisher calling each module],
    [Subject: Spring's multicaster behind `ApplicationEventPublisher`. Event sources: `PaidGroupDealService`, `GroupDealExpiryService`, `StripeCheckoutService`. Events: `MemberJoined`, `TeamSucceeded`, `TeamExpired`, `PaymentSucceeded`. Concrete observers: `OrderLifecycleSubscriber`, `TeamExpiryRefundSubscriber`, `IntegrationOutboxWriter`, `ShareCreditSubscriber`, `OpenDealOfferCacheSubscriber`],
    [A new consequence is a new subscriber. Liability: `BEFORE_COMMIT` subscribers share the publisher's transaction in a fixed order (`@Order(10)` order, `@Order(20)` payment), so one failure rolls back the whole expiry; `ShareCreditSubscriber` runs after commit in its own transaction],
    [Transactional Outbox (architectural)],
    [notification-svc must hear of a committed success or expiry, but a broker call inside the transaction could be lost or sent for a rolled-back change],
    [Writer: `IntegrationOutboxWriter` into `sys_outbox`. Relay: `OutboxRelayJob` → `OutboxRelayService` → `OutboxEventTransport`, implemented by `SnsOutboxEventTransport`],
    [The event exists if and only if the change committed. Liability: delivery is at least once, and the relay can publish a duplicate if it loses its database update after the broker accepted the message],
    [Idempotent Receiver (enterprise integration pattern)],
    [Stripe and SQS each deliver at least once],
    [`StripeCheckoutService` claims the Stripe event ID with `CheckoutPaymentRepository.claimEvent` (`log_payment_webhook_event`); `NotificationEventAppService.handle` marks the message with `ConsumedMessageRepository.markConsumed` (`sys_consumed_message`). Towards Stripe, the refund worker sends `RefundWork.providerKey` as the idempotency key],
    [A repeat changes nothing. Liability: a rolled-back settlement is retried only when Stripe redelivers; the refund key is safe only inside Stripe's 24-hour window, so an obligation still open after 23 hours goes to review],
    [Ports and Adapters with Separated Interface (architectural)],
    [Payment must settle and refund money without knowing teams, orders or Stripe's SDK],
    [Payment owns the ports and other modules implement them: `PaymentBusinessPort` by `OrderFulfilmentAdapter` (order module); `CheckoutGateway` by `StripeCheckoutGateway`; `RefundGateway` by `StripeRefundGateway` (infrastructure)],
    [Payment depends only on interfaces it owns; a second provider is a new adapter. Liability: the port's lock-order contract lives in Javadoc, not in the type],
  ),
  kind: table,
  caption: [Patterns in the UC4 and expiry designs, with the classes that play each role. Source: code and backend ADRs 0001, 0002 and 0007.],
) <tbl-patterns-324>

We rejected two candidates. `DealStatus` is not the State pattern: it has one transition rule and no behaviour that differs by state. A saga is not needed while team, order and payment share one settlement transaction (AD-02); AISS-02 records when that changes. Team creation in UC3 uses a GoF Factory Method (`PaidGroupDealCreator`, overridden by `StandardPaidGroupDealCreator`), with one product today; its variation point is new team types.
