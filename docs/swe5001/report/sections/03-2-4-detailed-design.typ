// Detailed Design: part of 03-solution-overview.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *
#import "appendix-a/lib-a.typ": afig

=== Detailed Design

This section shows, at class and object level, the two flows that move money under concurrency. The first is UC4 Join an existing team by paying, with UC3 Start a team and UC10 Pay upfront. The second is UC5 Resolve a team with UC6 Refund members of an expired team. They are critical because only in these flows do two actions race for the same money (HS-1, HS-4 and HS-8, §3.1.4). In both, the team's row lock decides the race, and a refused payment becomes a recorded refund obligation, never a lost payment.

*From analysis to design.* We first modelled each use case as an analysis model with boundary, control and entity (BCE) objects only, and wrote its flow of events. Each analysis object then became design classes through one or more of nine transition strategies (@tbl-strategies-324). A strategy names a design concern, the decisions it follows and what it adds to the model. Its tag is the short name that each design class carries as a stereotype in @fig-d11-uc4-design-classes and @fig-d13-expiry-design-classes, so every class shows the strategies that produced it. @tbl-strategy-detail gives the five strategies that shape these two flows in the four-part form: impacted analysis elements, resultant static design, resultant dynamic design and justification.

#figure(
  table(
    columns: (1.15fr, 0.5fr, 2.8fr, 1.05fr),
    table.header([Strategy], [Tag], [What it adds to the design], [Follows]),
    [S1 Language and framework], [—], [Java 21 and Spring Boot in COLA layers; entities as immutable aggregates that return a new snapshot], [AD-01, AD-18],
    [S2 Presentation], [—], [React screens that call the API only through the generated client], [AD-09],
    [S3 Distribution], [Dist], [Ports and module interfaces between modules; the one synchronous call, commerce to identity], [AD-02, AD-06],
    [S4 Persistence], [Pers], [One repository port per aggregate, MyBatis adapters, one owning service per table], [AD-03, AD-11],
    [S5 External interface], [Ext], [Adapters for Stripe and the other providers; no provider call inside a database transaction], [ADR 0001, 0004, 0005],
    [S6 Security], [Sec], [Session checks, role checks and rate limits at every boundary], [AD-05, AD-09],
    [S7 Scheduling], [Sched], [Time-triggered jobs that run under a database lease, one runner across tasks], [AD-16],
    [S8 Concurrency], [Conc], [Row locks in one global order, status-guarded updates, idempotency records], [AD-02, AD-03],
    [S9 Domain and integration events], [Evt], [In-process events before commit; integration events through the outbox, SNS and SQS], [AD-04],
  ),
  kind: table,
  caption: [Transition strategies from the analysis model to the design, with the tag each design class carries. Source: the AD register (§3.1.1, §3.2.1, §3.3) and backend ADRs 0001–0008.],
) <tbl-strategies-324>

S8 fixes one lock order for every path that settles money. In the sequence diagrams, a `critical` fragment marks one database transaction; lock 1 is the team row, lock 2 the purchase intent row and lock 3 the payment row (`OrderFulfilmentAdapter`, `PurchaseIntentRepository`). Expiry takes lock 1, cancels the team's orders with a status-guarded update, then locks the paid members' payment rows (lock 3) as it records their refunds.

#figure(
  table(
    columns: (0.95fr, 0.85fr, 4fr),
    table.header([Strategy], [Part], [Content]),
    table.cell(rowspan: 4)[S3 Distribution (Dist)],
    [Impacted], [`JoinTeamControl`, `Account`, `Product`],
    [Static], [Payment owns `PaymentBusinessPort`, implemented by `OrderFulfilmentAdapter` in the order module; port `UserAppService` with adapter `RemoteUserAppService`; module interface `CatalogPurchaseSupport`],
    [Dynamic], [`fulfil` crosses the port inside the settlement transaction (@fig-d12b-two-buyers-join)],
    [Justification], [Payment never depends on order code, and the only synchronous call between services is commerce to identity (AD-02, AD-06)],
    table.cell(rowspan: 4)[S4 Persistence (Pers)],
    [Impacted], [`Team`, `PurchaseIntent`, `Payment`, `Order`, `Refund`],
    [Static], [One repository port per aggregate: `PaidGroupDealRepository`, `PurchaseIntentRepository`, `CheckoutPaymentRepository`, `OrderRepository`, `RefundRepository`, each with a MyBatis adapter],
    [Dynamic], [Every repository call runs inside a `critical` fragment, one transaction per fragment (@fig-d12b-two-buyers-join, @fig-d14a-team-expiry)],
    [Justification], [One owning module per table keeps the later split into services open (AD-03, AD-11)],
    table.cell(rowspan: 4)[S7 Scheduling (Sched)],
    [Impacted], [`SchedulerTrigger`, `ResolveTeamControl`, `RefundMembersControl`],
    [Static], [`TeamExpirySweepJob`, `RefundWorkerJob` and `OutboxRelayJob`, each run by `LeasedJobRunner` with its lease row in `sys_scheduler_lock`],
    [Dynamic], [One sweep, then one `expireOne` transaction per due team (@fig-d14a-team-expiry); the refund worker's claim, call and finish (@fig-d14b-refund-retry)],
    [Justification], [With several tasks running, the lease lets one runner act on each tick (AD-16)],
    table.cell(rowspan: 4)[S8 Concurrency (Conc)],
    [Impacted], [`Team`, `PurchaseIntent`, `Payment`, `JoinTeamControl`, `ResolveTeamControl`],
    [Static], [`lockDeal` on `PaidGroupDealRepository`, `lockTeam` on `GroupDealSettlementSupport`, `lockById` on `PurchaseIntentRepository`, `lock` and `claimEvent` on `CheckoutPaymentRepository`; status-guarded `updateDeal` on `PaidGroupDealMapper`],
    [Dynamic], [Locks 1, 2 and 3 in order inside the settlement fragment (@fig-d12b-two-buyers-join); the last-seat race serialised on lock 1 (@fig-race-last-seat); expiry locks the team, then reads the clock (@fig-d14a-team-expiry)],
    [Justification], [A join and an expiry, or two joins, cannot both take the last seat or both win (HS-1, HS-4); one global order prevents deadlock (AD-02, AD-03, ADR 0001)],
    table.cell(rowspan: 4)[S9 Domain and integration events (Evt)],
    [Impacted], [`ResolveTeamControl`, `RefundMembersControl` (record), `Order`, `Invitation`],
    [Static], [Events `MemberJoined`, `TeamSucceeded`, `TeamExpired`, `PaymentSucceeded`; subscribers `OrderLifecycleSubscriber` (`@Order(10)`), `TeamExpiryRefundSubscriber` (`@Order(20)`), `IntegrationOutboxWriter` (lowest precedence), `ShareCreditSubscriber` (after commit)],
    [Dynamic], [The transaction owner calls the `BEFORE_COMMIT` subscribers in order (@fig-d14a-team-expiry); the relay publishes the outbox row to SNS later (@fig-d14c-outbox-relay)],
    [Justification], [The publisher does not call each module, and an integration event exists only if its change committed (AD-04, ADRs 0002 and 0007)],
  ),
  kind: table,
  caption: [The five strategies that shape the UC4 and expiry designs, in the four-part form. Source: code; analysis objects from @tbl-uc4-map-summary and @tbl-expiry-map-summary.],
) <tbl-strategy-detail>

==== Joining a team by paying

@fig-uc4-analysis is the analysis model of UC4 with UC10. `JoinTeamControl` realises the join, and `PayUpfrontControl`, the control of the included payment, hands it the confirmed payment. @tbl-uc4-flow is the flow of events; each step names the design element that performs it.

#figure(
  table(
    columns: (0.32fr, 2.6fr, 1.75fr),
    table.header([Step], [What happens], [Design element (figure)]),
    [1], [The buyer opens the invite link and asks to join; the system creates a purchase intent that freezes the team price], [`StripeCheckoutController.intent`, `PurchaseIntentAppService` (@fig-d12b-two-buyers-join)],
    [2], [The buyer asks to pay; the system creates the payment and a Stripe Checkout session and returns its URL], [`StripeCheckoutController.checkout`, `StripeCheckoutService`],
    [3], [The buyer pays on Stripe. Stripe reports the payment by webhook; the system verifies the signature and reads the session back from Stripe], [`StripeWebhookController`, `StripeCheckoutService.handleWebhook`],
    [4], [In one settlement transaction, the system claims the event ID], [`CheckoutPaymentRepository.claimEvent`],
    [5], [It locks the team (lock 1), the purchase intent (lock 2) and the payment (lock 3)], [`OrderFulfilmentAdapter.lockForSettlement`, `CheckoutPaymentRepository.lock`],
    [6], [The team admits the buyer as a paid member], [`PaidGroupDealService.joinConfirmed`, `GroupDeal.joinPaid`],
    [7], [The system saves the order, marks the intent fulfilled, consumes one unit of stock and records the payment outcome], [`OrderFulfilmentAdapter.fulfil`, `recordOutcome`],
    [8], [When the payment fills the last seat, the team succeeds and every pending order is confirmed before commit. Stripe receives HTTP 200], [`TeamSucceeded`, `OrderLifecycleSubscriber` (@fig-d12b-two-buyers-join)],
    [4a], [The event ID is already claimed: nothing changes], [`claimEvent` returns false],
    [6a], [The team is full or closed: no order is created, and the payment is owed a refund], [`requireRefund`, outcome `REFUND_REQUIRED` (@fig-race-last-seat)],
    [6b], [The quote expired before settlement: no fulfilment, and the payment is owed a refund], [outcome `BUSINESS_EXPIRED`],
    [7a], [A technical error rolls back the whole transaction, the claim included; a Stripe redelivery settles the payment later], [`TransactionTemplate` in `StripeCheckoutService`],
  ),
  kind: table,
  caption: [Flow of events for UC4 with UC10: basic flow (1–8) and alternatives (4a–7a, numbered by the step they branch from). Source: code.],
) <tbl-uc4-flow>

@fig-d11-uc4-design-classes shows the objects behind the webhook (strategies S3 and S8). The inbound adapter `StripeWebhookController` passes the raw request and signature through `PaymentAppService` (implemented by `StripePaymentAppService`) to #src("backend/blocbuy-application/src/main/java/com/blocbuy/payment/service/StripeCheckoutService.java")[`StripeCheckoutService`]. Before it opens the settlement transaction, the service verifies the signature and reads the Checkout session back from Stripe. Inside the transaction it first claims the event ID (`CheckoutPaymentRepository.claimEvent`), so a redelivered event stops there. It then locks the team for JOIN, the saved purchase intent and the payment, in the global order. Payment requests fulfilment through `PaymentBusinessPort`, which the order module implements in #src("backend/blocbuy-application/src/main/java/com/blocbuy/order/service/OrderFulfilmentAdapter.java")[`OrderFulfilmentAdapter`]\; payment never depends on order code. The team aggregate (`GroupDeal`) checks its deadline, remaining seats and existing members, and returns the updated team snapshot. #src("backend/blocbuy-infrastructure/src/main/java/com/blocbuy/groupbuy/persistence/PaidGroupDealRepositoryImpl.java")[`PaidGroupDealRepositoryImpl`] implements the lock with PostgreSQL through MyBatis. @tbl-uc4-map-summary shows where each class comes from.

#figure(
  table(
    columns: (1.15fr, 0.6fr, 2.7fr, 0.55fr),
    table.header([Analysis object], [Stereotype], [Design classes (layer)], [Strategy]),
    [`JoinTeamUI`], [boundary], [`StripeCheckoutController`: `intent`, `checkout` (adapter)], [S2, S6],
    [`PayUpfrontControl`], [control (UC10)], [`StripeWebhookController` → `PaymentAppService` → `StripeCheckoutService.handleWebhook`, `settle` (adapter, application)], [S3, S8],
    [`JoinTeamControl`, before payment], [control], [`PurchaseIntentAppService.createIntent`, which implements `PaymentIntentPort`; `GroupDealPurchaseSupportImpl` (application)], [S3, S6],
    [`JoinTeamControl`, after payment], [control], [`OrderFulfilmentAdapter.lockForSettlement`, `fulfil`, which implements `PaymentBusinessPort`; `PaidGroupDealService.joinConfirmed` (application)], [S3, S8],
    [`ResolveTeamControl`], [control (UC5)], [`TeamSucceeded`, published by `PaidGroupDealService`; `OrderLifecycleSubscriber.onTeamSucceeded` (domain, application)], [S9],
    [`Account`], [entity], [Port `UserAppService`; adapter `RemoteUserAppService` calls identity-svc (application)], [S3],
    [`Product`], [entity], [Module interface `CatalogPurchaseSupport`: `requirePurchasable`, `consumeOneUnit` (application)], [S3, S4],
    [`Team`], [entity], [Aggregate root `GroupDeal`: `joinPaid` returns `PaidAdmission` (domain); `PaidGroupDealRepository` (domain port), `PaidGroupDealRepositoryImpl`, `PaidGroupDealMapper` (infrastructure)], [S4, S8],
    [`Membership`], [entity], [`PaidMember`, a `biz_group_member` row of the `GroupDeal` aggregate, written by `insertJoinedMember`; `PaidParticipation`, `ParticipationResult` (domain)], [S1, S4, S8],
    [`PurchaseIntent`], [entity], [Aggregate root `PurchaseIntent`, port `PurchaseIntentRepository` (domain)], [S4, S8],
    [`Payment`], [entity], [Aggregate root `CheckoutPayment`, port `CheckoutPaymentRepository` (domain); `CheckoutPaymentRepositoryImpl` (infrastructure)], [S4, S8],
    [`Order`], [entity], [Aggregate root `Order`, port `OrderRepository` (domain)], [S4, S9],
    [`Refund`], [entity], [Created by `CheckoutPaymentRepository.requireRefund`; its lifecycle is UC6’s], [S4],
    [`Invitation`], [entity], [Aggregate root `ShareInvite`, port `ShareInviteRepository`; after commit `ShareCreditSubscriber`], [S4, S9],
    [(none)], [—], [Added by a strategy: `PaymentBusinessPort`, `PaymentIntentPort`, `GroupDealSettlementSupport` (S3); `RateLimitGateway` (S6); `PaymentSucceeded`, `MemberJoined`, `TeamSucceeded`, `IntegrationOutboxWriter` (S9)], [S3, S6, S9],
  ),
  kind: table,
  caption: [UC4 analysis objects and the design classes that realise them, with the transition strategy that produced each. Source: code; strategies in @tbl-strategies-324.],
) <tbl-uc4-map-summary>

#afig("a-uc04-ac", lbl: <fig-uc4-analysis>, [UC4 analysis view (UML class diagram, «boundary», «control» and «entity»). Of the three controls, only `JoinTeamControl` uses the entities; each object is traced to its design classes in @tbl-uc4-map-summary.])

#afig("d11-uc4-design-classes", [UC4 design view (UML class diagram). Layers run from top to bottom: adapter, application, domain, infrastructure. No dependency leaves the domain, and payment reaches the order module only through `PaymentBusinessPort`. The purchase intent and order roots (@tbl-uc4-map-summary) sit behind that port and are not drawn.])

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

@fig-uc5-analysis and @fig-uc6-analysis are the analysis models of UC5 and UC6. `ResolveTeamControl` expires a due team and starts UC6; `RefundMembersControl` records each refund owed and later pays it through the `PaymentGateway` boundary. @tbl-expiry-flow is the flow of events.

#afig("a-uc05-ac", lbl: <fig-uc5-analysis>, [UC5 analysis view (UML class diagram, BCE stereotypes). A team is resolved by its last paid seat or, at its deadline, by the scheduler.])

#afig("a-uc06-ac", lbl: <fig-uc6-analysis>, [UC6 analysis view (UML class diagram, BCE stereotypes). `RefundMembersControl` is the only object that talks to the boundaries and to the refund entities; each object is traced in @tbl-expiry-map-summary.])

#figure(
  table(
    columns: (0.32fr, 2.6fr, 1.75fr),
    table.header([Step], [What happens], [Design element (figure)]),
    [1], [Every minute, under the scheduler lease, the system selects up to 100 teams past their deadline by the database clock], [`TeamExpirySweepJob`, `GroupDealExpirySweeper.sweep`, `dueForExpiry` (@fig-d14a-team-expiry)],
    [2], [For each team, one transaction locks the team (lock 1) and reads the clock again], [`GroupDealExpiryService.expireOne`, `lockDeal`],
    [3], [The team becomes expired, and the system publishes `TeamExpired`], [`GroupDeal.expire`, `ApplicationEventPublisher`],
    [4], [Before commit, the team's orders are cancelled], [`OrderLifecycleSubscriber` (`@Order(10)`)],
    [5], [One refund obligation is recorded for each paid member, under the payment locks (lock 3)], [`TeamExpiryRefundSubscriber` (`@Order(20)`), `requireRefundsForExpiredTeam`],
    [6], [The integration event is written to the outbox, and the transaction commits], [`IntegrationOutboxWriter`, `sys_outbox`],
    [7], [Every 30 s, the refund worker claims the oldest due obligation in a short transaction], [`RefundWorkerJob`, `RefundService.processNext` (@fig-d14b-refund-retry)],
    [8], [Outside any transaction it asks Stripe for the refund, with the obligation's idempotency key, and records the answer in another transaction], [`StripeRefundGateway.createRefund`, `RefundWork.providerKey`, `finish`],
    [9], [The outbox relay publishes the expiry to SNS, which delivers it to the notification queue], [`OutboxRelayJob`, `SnsOutboxEventTransport` (@fig-d14c-outbox-relay)],
    [1a], [Another task holds the lease: nothing runs on this tick], [`LeasedJobRunner`],
    [3a], [A join filled the team first: expiry returns without an event], [`DEAL_CLOSED` caught (@fig-d14a-team-expiry)],
    [4a], [Any subscriber fails: the team's expiry, cancellations and obligations roll back together; the team is retried after 5 minutes], [`GroupDealExpirySweeper`],
    [8a], [Stripe answers pending, or the call fails transiently: the obligation is due again after 30 s], [`finish` with PROCESSING],
    [8b], [The provider refund ID is known: the worker retrieves that refund instead of creating one], [`retrieveRefund`],
    [8c], [23 hours have passed since the first attempt: the worker only looks the refund up, and an uncertain outcome goes to review], [`findRefund`, status REVIEW],
  ),
  kind: table,
  caption: [Flow of events for team expiry with refund (UC5 and UC6): basic flow (1–9) and alternatives (1a–8c, numbered by the step they branch from). Source: code.],
) <tbl-expiry-flow>

@fig-d13-expiry-design-classes shows why expiry and refund execution are separate (strategies S7, S8 and S9). Under the scheduler lease, the sweeper calls `expireOne` for each candidate team. The expiry service locks the team and reads the database clock again. If a concurrent join has already filled the team, expiry returns without publishing an event. If expiry wins, it writes the expired state and publishes `TeamExpired` in the same transaction. Before commit, the transaction owner calls the subscribers in `@Order`: the order subscriber (10), the refund subscriber (20) and, when integration events are enabled, the outbox writer (lowest precedence), as @fig-d14a-team-expiry shows. A failure in one subscriber rolls back that team's expiry, cancellations and refund obligations together. A join paid after the deadline, or after the sweep has taken the team, never reaches the team: settlement sees the expired quote and records `BUSINESS_EXPIRED`, so that payment is owed a refund. The expiry transaction is in #src("backend/blocbuy-application/src/main/java/com/blocbuy/groupbuy/service/GroupDealExpiryService.java")[`GroupDealExpiryService`], and the subscriber order is recorded in #src("backend/docs/adr/0002-team-lifecycle-events.md")[ADR 0002]. @tbl-expiry-map-summary traces the analysis objects.

#figure(
  table(
    columns: (1.3fr, 0.6fr, 2.7fr, 0.6fr),
    table.header([Analysis object], [Stereotype], [Design elements], [Strategies]),
    [`SchedulerTrigger`], [boundary], [`TeamExpirySweepJob` (every minute) and `RefundWorkerJob` (every 30 s) in bootstrap scheduling; each runs under `LeasedJobRunner`, whose lease row is in `sys_scheduler_lock`.], [S7],
    [`ResolveTeamControl` (selection)], [control], [`GroupDealExpirySweeper.sweep`: up to 100 due teams, no transaction, a failed team deferred for 5 minutes.], [S1, S7],
    [`ResolveTeamControl` (one team)], [control], [`GroupDealExpiryService.expireOne`: one 10 s transaction that locks the team first and reads the clock after the lock; publishes `TeamExpired` through `ApplicationEventPublisher`.], [S1, S8, S9],
    [`Team`], [entity], [Aggregate root `GroupDeal` with `DealStatus`; `expire` becomes `GroupDeal.expire`. Port `PaidGroupDealRepository`, table `biz_group_deal`.], [S1, S4, S8],
    [`Order`], [entity], [`OrderLifecycleSubscriber.onTeamExpired` (`@Order(10)`) calls `OrderRepository.cancelByDealId`, a status-guarded update of `biz_order`.], [S4, S9],
    [`RefundMembersControl` (record)], [control], [`TeamExpiryRefundSubscriber.onTeamExpired` (`@Order(20)`) calls `TeamExpiryRefundService.recordExpiredTeam`. It records one obligation per paid member through `requireRefundsForExpiredTeam` on `CheckoutPaymentRepository`, in the expiry transaction.], [S3, S8, S9],
    [`RefundMembersControl` (pay)], [control], [`RefundService.processNext`: claim in one short transaction, call the provider outside any transaction, record the answer with `finish` in another. Each attempt makes the row due again after 30 s; after 23 h the worker only looks a refund up.], [S5, S7, S8],
    [`PaymentGateway` (analysis boundary)], [boundary], [Port `RefundGateway`, adapter `StripeRefundGateway`; the idempotency key is `RefundWork.providerKey`.], [S5, S6],
    [`Refund`], [entity], [One `biz_refund` row behind `RefundRepository`; a claim returns the snapshot `RefundWork`.], [S1, S4, S8],
    [`RefundAttempt`], [entity], [One `log_refund_attempt` row per attempt, inserted by `RefundMapper.audit`.], [S4],
    [`Payment`], [entity], [Aggregate root `CheckoutPayment`. `CheckoutPaymentRepository.requireRefundsForExpiredTeam` locks the team's paid payments (lock 3) and inserts one obligation each.], [S4, S8],
    [`PurchaseIntent`], [entity], [Aggregate root `PurchaseIntent`; each obligation keeps its intent (`biz_refund.intent_id`).], [S4],
    [`Membership`], [entity], [`PaidMember`, a `biz_group_member` row of the `GroupDeal` aggregate.], [S4],
    [`JoinTeamControl`], [control], [The join path of @tbl-uc4-map-summary; a payment that fills the last seat resolves the team with `TeamSucceeded`.], [S9],
    [`ReceiptUI`], [boundary], [`PaymentBrowserService.receipt` reads `RefundRepository.status` for the buyer's receipt.], [S2],
    [`ReviewUI`], [boundary], [Not built: no controller or job calls `RefundService.reconcile`; an uncertain refund waits in review status.], [S6],
    [(none)], [—], [`IntegrationOutboxWriter.onTeamExpired` (lowest precedence) writes `TeamExpiredV1` to `sys_outbox` in the expiry transaction; `OutboxRelayService` later publishes it to SNS.], [S9],
  ),
  kind: table,
  caption: [Analysis-to-design mapping for team expiry with refund (UC5 and UC6). Source: code; strategies in @tbl-strategies-324.],
) <tbl-expiry-map-summary>

#afig("d13-expiry-design-classes", [Expiry design view (UML class diagram). The expiry service only publishes `TeamExpired`; a separate job pays each refund obligation later.])

#afig("d14a-team-expiry", [Team expiry interaction view (UML sequence diagram). Each team expires in its own `critical` transaction: the team lock, the clock read under it, `GroupDeal.expire`, then the transaction owner calls the `BEFORE_COMMIT` subscribers in `@Order`, so the orders, the refund obligations and the outbox row commit or roll back with the expiry.])

#afig("d14b-refund-retry", [Refund worker interaction view (UML sequence diagram). After the expiry commits, the leased refund worker claims an obligation, calls Stripe and records the answer; no Stripe call runs inside a database transaction.])

*Paying the buyer back.* @fig-d14b-refund-retry traces what runs after the expiry commits. The refund worker claims an obligation in one short transaction, calls Stripe outside any transaction and records the answer in another. It derives the provider key from the refund ID (#src("backend/blocbuy-domain/src/main/java/com/blocbuy/payment/model/RefundWork.java")[`RefundWork`]). If it knows the provider refund ID, it retrieves that refund instead of creating one. From 23 hours after the first attempt it only looks the refund up and sends an uncertain outcome for review. Each attempt makes the obligation due again after a fixed 30 s, so a pending or failed call is retried until Stripe gives a final answer. Beside the worker, the outbox relay publishes the expiry through SNS (@fig-d14c-outbox-relay), which delivers it asynchronously to the notification queue; notification can lag or retry independently of repayment. Tested locally, not on AWS: #src("backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/TeamExpirySchedulingIT.java")[`TeamExpirySchedulingIT`] covers the rollback, the retry and the reused provider key.

#afig("d14c-outbox-relay", [Outbox relay interaction view (UML sequence diagram). The leased relay runs apart from the refund worker and publishes the committed expiry to SNS; no SNS call runs inside a database transaction.])

==== Patterns in the two flows

@tbl-patterns-324 lists the patterns in the two flows in a structured form: the problem, the roles and the classes that play them, the consequence and the variable aspect, the part that may change without changing the rest. Only Observer is a GoF pattern in these two flows; the other three are architectural or integration patterns. Factory Method (GoF) appears in UC3, where the team is created: `PaidGroupDealCreator.initiate` is final and calls the abstract `createGroupDeal`, which `StandardPaidGroupDealCreator` overrides (so `initiate` is also a Template Method). There is one product today; the variable aspect is the team type.

We rejected two candidates. `DealStatus` is not the State pattern: it has one transition rule and no behaviour that differs by state. A saga is not needed while team, order and payment share one settlement transaction (AD-02); AISS-02 records when that changes.

#figure(
  table(
    columns: (0.85fr, 1fr, 2fr, 1.25fr, 0.9fr),
    table.header([Pattern (kind)], [Problem], [Roles and classes], [Consequence and liability], [Variable aspect]),
    [Observer (GoF, behavioural), realised with Spring application events (publish–subscribe)],
    [A team's success or expiry must confirm or cancel orders, owe refunds, write an integration record, credit the sharer and evict a cache, without the publisher calling each module],
    [Subject: Spring's multicaster behind `ApplicationEventPublisher`. Event sources: `PaidGroupDealService`, `GroupDealExpiryService`, `StripeCheckoutService`. Events: `MemberJoined`, `TeamSucceeded`, `TeamExpired`, `PaymentSucceeded`. Concrete observers: `OrderLifecycleSubscriber`, `TeamExpiryRefundSubscriber`, `IntegrationOutboxWriter`, `ShareCreditSubscriber`, `OpenDealOfferCacheSubscriber`],
    [A new consequence is a new subscriber. Liability: `BEFORE_COMMIT` subscribers share the publisher's transaction in a fixed order (`@Order(10)` order, `@Order(20)` payment), so one failure rolls back the whole expiry; `ShareCreditSubscriber` runs after commit in its own transaction],
    [The set of reactions to a team outcome],
    [Transactional Outbox (architectural)],
    [notification-svc must hear of a committed success or expiry, but a broker call inside the transaction could be lost or sent for a rolled-back change],
    [Writer: `IntegrationOutboxWriter` into `sys_outbox`. Relay: `OutboxRelayJob` → `OutboxRelayService` → `OutboxEventTransport`, implemented by `SnsOutboxEventTransport`],
    [The event exists if and only if the change committed. Liability: delivery is at least once, and the relay can publish a duplicate if it loses its database update after the broker accepted the message],
    [The broker transport behind `OutboxEventTransport`],
    [Idempotent Receiver (enterprise integration pattern)],
    [Stripe and SQS each deliver at least once],
    [`StripeCheckoutService` claims the Stripe event ID with `CheckoutPaymentRepository.claimEvent` (`log_payment_webhook_event`); `NotificationEventAppService.handle` marks the message with `ConsumedMessageRepository.markConsumed` (`sys_consumed_message`). Towards Stripe, the refund worker sends `RefundWork.providerKey` as the idempotency key],
    [A repeat changes nothing. Liability: a rolled-back settlement is retried only when Stripe redelivers; the refund key is safe only inside Stripe's 24-hour window, so an obligation still open after 23 hours goes to review],
    [The delivery channel (Stripe webhooks, SQS messages)],
    [Ports and Adapters with Separated Interface (architectural)],
    [Payment must settle and refund money without knowing teams, orders or Stripe's SDK],
    [Payment owns the ports and other modules implement them: `PaymentBusinessPort` by `OrderFulfilmentAdapter` (order module); `CheckoutGateway` by `StripeCheckoutGateway`; `RefundGateway` by `StripeRefundGateway` (infrastructure)],
    [Payment depends only on interfaces it owns; a second provider is a new adapter. Liability: the port's lock-order contract lives in Javadoc, not in the type],
    [The payment provider, and the business that payment settles for],
  ),
  kind: table,
  caption: [Patterns in the UC4 and expiry designs, with the classes that play each role. Source: code and backend ADRs 0001, 0002 and 0007.],
) <tbl-patterns-324>
