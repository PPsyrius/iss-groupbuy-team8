#import "lib-a.typ": *

== UC4 Join an Existing Team by Paying <sec-a-uc04>

UC4 is the use case in which money, a limited number of seats, concurrent buyers, an external payment provider and domain events all meet. A signed-in buyer pays the team's frozen price first; only after Stripe confirms the payment does commerce decide whether the buyer gets a seat. The buyer who fills the last seat makes the team succeed. A buyer who pays for a seat that has gone is owed a refund. Section 3.2.4 summarises this use case in D11 and D12. This part gives the full path from the flows of events to the code: analysis model, transition, design model, operations and patterns. The payment itself (the webhook's arrival, its verification and the payment side of the settlement) is the included use case Pay upfront and is designed once, in @sec-a-pay-upfront; this part designs what the money buys. Every class, method and error code named here exists in the code on this branch. UC4 is designed and built; the evidence for each flow is listed in section 2.2.

=== Use Case and Flows of Events <sec-a-uc04-flows>

- *Actors.* Buyer (primary, a joining member). Stripe (secondary, through the included use case Pay upfront, @sec-a-pay-upfront).
- *Trigger.* The buyer presses "Join this bloc" on an open team's row, or on the invite banner shown to a buyer who arrived through a member's invite link (UC7, @sec-a-uc07).
- *Services.* commerce-svc owns every step (its payment, order, group-buy and catalog modules). identity-svc answers one question: is this buyer's account active?
- *Preconditions.* The buyer is signed in (UC2) and active. Stripe Checkout is enabled. The team is open, inside its deadline and has a free seat, and the buyer neither leads it nor belongs to it.
- *Postconditions on success.* The team has exactly one more paid member and never more than its target size. The buyer has one membership and one pending order. The payment has exactly one outcome, fulfilled. If this buyer filled the last seat, the team has succeeded and all its orders are confirmed.
- *Postconditions on refusal after payment.* No membership or order exists. The payment's outcome is "refund required" and exactly one refund obligation is recorded.

@tbl-a-uc04-flows numbers every step in business terms. The diagrams below repeat these numbers on their messages; @tbl-a-uc04-ops and @tbl-a-uc04-trace add the error codes, transactions and services that realise each step.

#atable(
  [UC4 flows of events. B = basic flow, A = alternate flow, E = exception flow; "At" is the step where an alternate or exception flow branches off.],
  <tbl-a-uc04-flows>,
  columns: (auto, auto, 1fr),
  header: ([Step], [At], [What happens]),
  [*B1*], [], [The buyer asks to join an open team, optionally carrying an invite reference.],
  [*B2*], [], [The system checks that the buyer's account is active.],
  [*B3*], [], [The system checks that the buyer may still join: the team is open and inside its deadline, a seat is free, and the buyer is not its leader or a member. No seat is reserved.],
  [*B4*], [], [The system freezes the purchase terms as a purchase intent: the team's own price, currency, target size and deadline, and the sharer from A1 if any. The intent is payable for 40 minutes, never past the team's deadline.],
  [*B5*], [], [The buyer pays the frozen amount: «include» Pay upfront via Stripe Checkout (@sec-a-pay-upfront). The included use case ends when Stripe reports that the payment succeeded.],
  [*B6*], [], [The system checks again that the payment arrived in time, that the intent is unspent and not lapsed, that the buyer is active and that the listing can still be bought.],
  [*B7*], [], [The system admits the buyer against the team as it is now: the amount paid equals the team's price, the buyer is not a member, the team is open and inside its deadline, and a seat is free. It records the membership (with the sharer) and adds one to the team's size.],
  [*B8*], [], [The system creates the buyer's order as pending, marks the intent as spent and takes one unit of stock.],
  [*B9*], [], [The system records that the payment bought its seat.],
  [*B10*], [], [If B7 filled the last seat, the team succeeds and all its pending orders become confirmed, together with the join (UC5, @sec-a-uc05).],
  [*B11*], [], [The receipt page shows "Payment received", and the account page shows the team's seats, for example "2 of 2 joined".],
  [*A1*], [B1], [An invite reference is given. If it names this team and another member, that member is frozen on the intent as the sharer and credited once the join is final (UC7 extension point _admission_, @sec-a-uc07). An unknown, other-team or self-referring reference is ignored; the join goes on unattributed.],
  [*A2*], [B1], [The buyer asks to join this team more than 60 times a minute: the request is refused as too many requests and nothing is stored.],
  [*A3*], [B3], [The team does not exist, is closed or expired, is full, or the buyer already leads or belongs to it: the request is refused before any payment exists.],
  [*A4*], [B5], [The buyer abandons the payment or the Checkout session expires: no membership or order exists (@sec-a-pay-upfront).],
  [*A5*], [B6], [The payment arrived after the intent or the team's deadline, the intent has lapsed, the buyer is no longer active or the listing can no longer be bought: no membership or order is created, and the money is owed back (one refund obligation).],
  [*A6*], [B7], [The team refuses the admission: it filled, closed, succeeded or expired after B3, the buyer joined with an earlier payment, or the amount differs. The refusal is recorded with the payment, the money is owed back, and the refund is paid by UC6 (@sec-a-uc06). The receipt shows "Refund pending".],
  [*A7*], [B7], [Two buyers pay for the last seat at the same time: the system admits them one at a time. The first is admitted and completes the team; the second meets a team that is no longer open and follows A6.],
  [*A8*], [B5], [Stripe reports the same payment again: the system returns the first result and creates nothing twice.],
  [*E1*], [B6–B10], [A technical failure (the account service cannot be reached, the last unit of stock has gone, a storage error): nothing of B6–B10 is kept. Stripe is told to report again later, and the steps are retried.],
  [*E2*], [B2], [The account service cannot be reached when the buyer asks to join: the request fails and no intent is stored.],
)

=== Analysis Class Diagram <sec-a-uc04-ac>

@fig-a-uc04-ac is the analysis model: what UC4 needs, with no platform. The Buyer works through `JoinTeamUI`; Stripe reaches the system only through the gateway boundary `PaymentGateway`. `JoinTeamControl` realises UC4 and is the only object that talks to both boundaries and to the entities. Attributes sit on entities only, in analysis types; a reference to another entity is an association with a role name. The sharer is the member whose invitation brought the buyer: the intent freezes it, and the new membership copies it (a self-association of `Membership`, which the kit cannot draw). The team composes its memberships, so the team, not the control, adds a member when it admits a buyer. A payment owns at most one refund obligation.

Compared with the UC4 analysis model in the SWE5006 report, four entities are new because the flows above need them: `Account` (B2, B6), `Product` (B6, B8), `PurchaseIntent` (B4, the frozen terms) and `Invitation` (A1). The association between a product and its teams belongs to UC3 (@sec-a-uc03); UC4 does not navigate it.

#afig("a-uc04-ac", [UC4 analysis class diagram (UML class notation, BCE stereotypes). `JoinTeamControl` is the only object that talks to both boundaries and to the entities; `Team` owns the admission rule and composes the memberships.])

=== Analysis Sequence Diagrams <sec-a-uc04-as>

The basic flow is drawn in two parts so that the lifelines stay legible: before payment (@fig-a-uc04-as-basic-1) and after it (@fig-a-uc04-as-basic-2). Each message carries the step it realises. `checkAdmission` before payment reserves nothing; `admit` after payment is the authority, because the team may fill, close or expire while the buyer pays. @fig-a-uc04-as-alt-seat draws the two significant alternate flows in which money has moved but no seat is given: A5 (a B6 check fails) and A6 with its race A7 (the team refuses). A2, E1 and E2 need design concepts (a rate counter, a transaction, a remote call) and appear only in the design sequences; A4 and A8 belong to Pay upfront.

#afig("a-uc04-as-basic-1", [UC4 analysis sequence diagram, basic flow part 1 (B1–B5), with A1 and A3. The `break` fragment refuses a buyer before any money moves; the purchase intent is created only after the check passes.])

#afig("a-uc04-as-basic-2", [UC4 analysis sequence diagram, basic flow part 2 (B5–B11). The checks of B6 are repeated because the terms may lapse while the buyer pays; the team creates the membership, and the `opt` fragment confirms every order when this join fills the last seat.])

#afig("a-uc04-as-alt-seat", [UC4 analysis sequence diagram, alternate flows A5, A6 and A7 (refused after payment). Payments for one team are admitted one at a time; whichever check refuses, the payment creates a `Refund` instead of a membership.])

=== Transition: Strategies and Mapping <sec-a-uc04-transition>

The transition strategies of @sec-a-strategies turn the analysis model into the design model. The tags in the design diagrams name them: S1 Lang, S2 Pres, S3 Dist, S4 Pers, S5 Ext, S6 Sec, S7 Sched, S8 Conc, S9 Evt. Each line gives the decision (AD) and quality requirement (NFR) behind the strategy, the analysis objects it impacts and the design elements that result.

#[
#set par(justify: false)
- *S1 Language and framework (AD-01, AD-18; NFR-05, 06, 26):* impacted the immutable terms and results of `Team`, `PurchaseIntent` and `Payment` → resultant records `GroupDealPurchaseSupport.JoinTerms`, `PaidParticipation`, `PaidAdmission`, `ParticipationResult`, `PaymentBusinessPort.Outcome` and `PaymentSucceeded`; the two port implementations are plain classes built in `PaymentIntegrationConfiguration`.
- *S2 Presentation (AD-09; NFR-07, 12, 16, 25):* impacted `JoinTeamUI` → resultant route component `ProductDetailPage` and hook `useCheckout`, whose `begin` call uses the generated API client; not drawn (frontend, @sec-a-strategies).
- *S3 Distribution (AD-02, AD-06, AD-09; NFR-05, 07, 64, 66):* impacted `JoinTeamControl`, `Account`, `Team`, `Product` → resultant: the control splits by phase and by module behind `PaymentIntentPort` and `PaymentBusinessPort` (owned by payment, implemented by order) and the module interfaces `GroupDealPurchaseSupport`, `GroupDealSettlementSupport` and `CatalogPurchaseSupport`; `Account` becomes `UserAppService`, which commerce-svc implements as `RemoteUserAppService`, one synchronous HTTP call to identity-svc that fails closed. Dynamics @fig-a-uc04-ds-intent, @fig-a-uc04-ds-fulfil.
- *S4 Persistence (AD-03, AD-11; NFR-05, 13):* impacted `Team`, `Membership`, `PurchaseIntent`, `Order` → resultant aggregates `GroupDeal`, `PurchaseIntent` and `Order` behind repository ports implemented with MyBatis; a membership is a `biz_group_member` row written by `PaidGroupDealRepository.insertJoinedMember`. The chain `PaidGroupDealRepository` → `PaidGroupDealRepositoryImpl` → `PaidGroupDealMapper` (@fig-a-uc04-dc-admit) is representative; every other repository follows it. `Payment` becomes `CheckoutPayment` (@sec-a-pay-upfront). The refund obligation is created by `CheckoutPaymentRepository.requireRefund` inside the payment's transaction; its lifecycle is the refund of UC6 (@sec-a-uc06).
- *S5 External interface (AD-06; NFR-21, 22, 23, 31, 50, 66):* impacted `PaymentGateway` → resultant `StripeWebhookController` and the port `CheckoutGateway`, designed once in @sec-a-pay-upfront. UC4's design starts where Pay upfront calls `PaymentBusinessPort`.
- *S6 Security (AD-05, AD-09; NFR-01, 46, 47, 58, 68):* impacted `JoinTeamUI`, `JoinTeamControl` → resultant: the buyer's id comes from the shared session, never from the request; `StripeCheckoutController.checkCsrf` requires the `X-BlocBuy-Request` header; `RateLimitGateway` allows 60 JOIN requests a minute per buyer and team; the price is the team's own `dealPrice`, never a client value. Dynamics @fig-a-uc04-ds-intent.
- *S7 Scheduling (AD-16; NFR-15, 32, 61):* not applied; no UC4 step is triggered by time. Lapsed intents and expired teams are swept in @sec-a-pay-upfront and @sec-a-uc05.
- *S8 Concurrency (AD-02, AD-03; NFR-13, 21, 27, 30):* impacted `Team`, `PurchaseIntent`, `Payment` → resultant row locks in one fixed order: `PaidGroupDealRepository.lockDeal` (lock 1, team), `PurchaseIntentRepository.lockById` (lock 2, intent), `CheckoutPaymentRepository.lock` (lock 4, payment; lock 3, the order, has no row yet in settlement), then inside fulfilment `PaidGroupDealRepository.reserveAndLock` (lock 5, participation receipt) and the guarded stock update `CatalogPurchaseSupport.consumeOneUnit` (lock 6, product row); the guarded `PaidGroupDealMapper.updateDeal` and unique indexes back every "at most one" rule. Dynamics @fig-a-uc04-ds-terms, @fig-a-uc04-ds-fulfil, @fig-a-uc04-ds-admit, @fig-a-uc04-ds-race.
- *S9 Domain and integration events (AD-04; NFR-30, 39, 59, 60, 61, 63):* impacted the B10 confirmation and the A1 credit → resultant `MemberJoined`, `TeamSucceeded` and `PaymentSucceeded` published through `ApplicationEventPublisher`; `OrderLifecycleSubscriber` and `IntegrationOutboxWriter` run before commit, `ShareCreditSubscriber` and `OpenDealOfferCacheSubscriber` after it. Dynamics @fig-a-uc04-ds-events; the outbox relay is in @sec-a-notifications.
]

*One transaction, four aggregates.* The settlement transaction writes `GroupDeal`, `PurchaseIntent`, `CheckoutPayment` and `Order`, plus the participation receipt and the refund row. This breaks the usual rule of one aggregate per transaction on purpose, for the reasons given once in S8 of @sec-a-strategies: the seat and the money must commit or fail together; all four aggregates live in commerce-svc's one database, so a local transaction replaces a saga (AD-02, AD-03); the fixed lock order (team → intent → order → payment; in settlement team → intent → payment, then the receipt and the product stock inside fulfilment) prevents deadlock with concurrent JOIN, START and expiry; and the wider lock window is bounded by the 10 s timeout.

@tbl-a-uc04-map shows where each analysis object lands. Every class on the four design class diagrams appears in it once; the control row is split by phase and names each phase's entry point.

#atable(
  [UC4 analysis → design mapping. Every analysis object of @fig-a-uc04-ac, and every design class of @fig-a-uc04-dc-intent to @fig-a-uc04-dc-events.],
  <tbl-a-uc04-map>,
  columns: (0.95fr, 0.6fr, 2.6fr, 0.55fr, 0.8fr),
  header: ([Analysis object], [Stereotype], [Design elements (layer, module)], [Strategies], [Figures]),
  [`JoinTeamUI`], [boundary], [Adapter: `StripeCheckoutController` (`POST /api/v1/payments/intents`). Frontend `ProductDetailPage`, `useCheckout`: not drawn (S2).], [S2, S6], [@fig-a-uc04-dc-intent],
  [`PaymentGateway`], [boundary], [`StripeWebhookController`, `CheckoutGateway`: designed in @sec-a-pay-upfront, not drawn here.], [S5], [—],
  [`JoinTeamControl`, before payment], [control], [Entry point `PurchaseIntentAppService.createIntent`, called by `PaymentBrowserService`; then `GroupDealPurchaseSupportImpl` (application layer).], [S3, S6], [@fig-a-uc04-dc-intent],
  [`JoinTeamControl`, after payment], [control], [Entry point `OrderFulfilmentAdapter.fulfil` (called by `StripeCheckoutService`, @sec-a-pay-upfront); then `PaidGroupDealService`; `GroupDealSettlementSupport` is implemented by `GroupDealSettlementSupportImpl` (not drawn).], [S3, S8], [@fig-a-uc04-dc-fulfil, @fig-a-uc04-dc-admit],
  [`Account`], [entity], [Port `UserAppService`; adapter `RemoteUserAppService` calls identity-svc. The `User` aggregate is identity-svc's (@sec-a-uc02).], [S3], [@fig-a-uc04-dc-intent],
  [`Product`], [entity], [Module interface `CatalogPurchaseSupport` (`requirePurchasable`, `consumeOneUnit`); the `Product` aggregate is catalog's (@sec-a-uc09).], [S3, S4], [@fig-a-uc04-dc-fulfil],
  [`Team`], [entity], [Aggregate root `GroupDeal` with `DealStatus`; `checkAdmission` → `lockJoinTerms` and the guards; `admit` → `joinPaid`, returning `PaidAdmission`. Port `PaidGroupDealRepository`, adapter `PaidGroupDealRepositoryImpl`, mapper `PaidGroupDealMapper`, table `biz_group_deal`.], [S4, S8], [@fig-a-uc04-dc-admit],
  [`Membership`], [entity], [A `biz_group_member` row of the `GroupDeal` aggregate, written by `insertJoinedMember`. `PaidParticipation` is the paid claim to a seat; `ParticipationResult` is the admission's result, frozen in `biz_group_participation_receipt`.], [S4, S8], [@fig-a-uc04-dc-fulfil, @fig-a-uc04-dc-admit],
  [`PurchaseIntent`], [entity], [Aggregate root `PurchaseIntent`, port `PurchaseIntentRepository`, table `biz_purchase_intent`.], [S4, S8], [@fig-a-uc04-dc-intent, @fig-a-uc04-dc-fulfil],
  [`Payment`], [entity], [Aggregate root `CheckoutPayment`, port `CheckoutPaymentRepository`: designed in @sec-a-pay-upfront, not drawn here.], [S4, S8], [—],
  [`Order`], [entity], [Aggregate root `Order`, port `OrderRepository`, table `biz_order`; confirmed by `OrderLifecycleSubscriber`.], [S4, S9], [@fig-a-uc04-dc-fulfil, @fig-a-uc04-dc-events],
  [`Refund`], [entity], [Created by `CheckoutPaymentRepository.requireRefund` (@sec-a-pay-upfront); its lifecycle is UC6's (@sec-a-uc06). Not drawn here.], [S4], [—],
  [`Invitation`], [entity], [Record `ShareInvite`, port `ShareInviteRepository`; after commit `ShareCreditSubscriber` writes through `ShareCreditRepository` (@sec-a-uc07).], [S4, S9], [@fig-a-uc04-dc-intent, @fig-a-uc04-dc-events],
  [(none)], [—], [Introduced by a strategy, with no analysis counterpart. S3: `PaymentIntentPort`, `PaymentBusinessPort`, `PaymentBusinessPort.Outcome`, `GroupDealPurchaseSupport`, `GroupDealSettlementSupport`. S6: `RateLimitGateway`. S9: `ApplicationEventPublisher`, `PaymentSucceeded`, `MemberJoined`, `TeamSucceeded`, `IntegrationOutboxWriter`, `OutboxMapper`, `OpenDealOfferCacheSubscriber`; the publishers `StripeCheckoutService` and `PaidGroupDealService` as event sources.], [S3, S6, S9], [@fig-a-uc04-dc-intent, @fig-a-uc04-dc-fulfil, @fig-a-uc04-dc-events],
)

=== Design Class Diagrams <sec-a-uc04-dc>

The design model is drawn in four slices along the flow: the purchase intent before payment (@fig-a-uc04-dc-intent), fulfilment in the order module (@fig-a-uc04-dc-fulfil), admission in the group-buy module (@fig-a-uc04-dc-admit) and the domain events with their subscribers (@fig-a-uc04-dc-events). The payment module's classes are drawn in @sec-a-pay-upfront. A class shared by two slices appears in both with the members that slice uses. All four slices run in commerce-svc; the only class that reaches another service is `RemoteUserAppService`. `ApplicationEventPublisher` is Spring's interface, the one framework type drawn, because the event sources depend on it (@sec-a-uc04-patterns). Each box carries its DDD or layer stereotype and the strategy tags that produced it. An association means the class holds a reference in a field; a dependency means it uses the other class as a parameter, return value or created object. Static operations are marked «static», because the kit can draw neither underlines nor braces. A nested type is named with its owner, for example `PaymentBusinessPort.Outcome`.

#afig("a-uc04-dc-intent", [UC4 design class diagram, slice 1: purchase intent before payment (UML, commerce-svc). The payment module knows only `PaymentIntentPort`; the order module implements it and reads the team and the listing only through the group-buy and catalog module interfaces.])

#afig("a-uc04-dc-fulfil", [UC4 design class diagram, slice 2: fulfilment in the order module (UML, commerce-svc). `OrderFulfilmentAdapter` implements `PaymentBusinessPort` and owns what the money buys: the membership through `PaidGroupDealService`, the order it creates, and the spending of the intent.])

#afig("a-uc04-dc-admit", [UC4 design class diagram, slice 3: admission in the group-buy module (UML, commerce-svc). `GroupDeal` decides admission and returns a new immutable snapshot in `PaidAdmission`; the repository chain down to the mapper hides the row locks and the guarded update. `DealStatus` is the type of `GroupDeal.status`.])

#afig("a-uc04-dc-events", [UC4 design class diagram, slice 4: domain events and their subscribers (UML, commerce-svc). The event sources depend only on `ApplicationEventPublisher`; each consequence of a join is a separate subscriber.])

=== Design Sequence Diagrams <sec-a-uc04-ds>

Each design sequence retells part of @tbl-a-uc04-flows with the classes above; the step tags in the notes link the two. A `critical` fragment is a database transaction, labelled with its propagation, isolation or timeout. HTTP calls and transaction-phase listener calls are synchronous (filled arrowhead) with dashed returns; UC4 sends no asynchronous message, because the only ones in the system, the outbox relay and SNS/SQS, belong to @sec-a-notifications. A lifeline names the interface the caller holds, unless the diagram shows what happens inside the implementation (then it names the class, for example `:GroupDealPurchaseSupportImpl`).

*Before payment.* @fig-a-uc04-ds-intent shows the request, the rate limit (A2) and the intent transaction; @fig-a-uc04-ds-terms shows what happens inside `join`. The team row lock taken by `lockJoinTerms` is held until the intent commits, so two buyers cannot both be quoted the last seat. The JOIN quote is the team's own `dealPrice`, never a fresh catalogue price.

*After payment.* Pay upfront (@sec-a-pay-upfront) receives and verifies the webhook, opens the settlement transaction, claims the event and calls into the order module. @fig-a-uc04-ds-fulfil starts there. The adapter takes lock 1 (team) and lock 2 (intent) before the payment module takes lock 4 (payment). A payment that arrives after the quote's or the team's deadline never reaches `fulfil`: `StripeCheckoutService` returns `BUSINESS_EXPIRED` itself (A5). Every check that can reject runs before the first business write, so a rejection commits only the receipt and the refund obligation; a technical failure rolls everything back (E1). @fig-a-uc04-ds-admit is the admission inside `joinConfirmed`, where the participation receipt is lock 5.

*At commit.* @fig-a-uc04-ds-events shows the subscribers. `publishEvent` returns at once; Spring's transaction synchronisation calls each `@TransactionalEventListener` when its phase comes, synchronously on the committing thread. `OrderLifecycleSubscriber` runs at `BEFORE_COMMIT`, after the joiner's own order has been saved, so all of the team's orders are confirmed in the same transaction (B10); because the call is synchronous, an exception there rolls the whole settlement back. The outbox writer, active only when integration events are enabled, records `PaymentSucceededV1` and `TeamSucceededV1` in the same transaction. The share credit (in its own transaction) and the cache eviction run after commit; a failure there is logged and never undoes the join. `TransactionTemplate` is Spring's; it is the `tx` field of `StripeCheckoutService`.

*The race for the last seat.* @fig-a-uc04-ds-race shows A7 with two buyers. The first settlement to lock the team row admits its buyer and completes the team; the second waits on the same row, then meets a team that is no longer open, so `GroupDeal.requireJoinWindow` refuses it with `DEAL_CLOSED` and its money becomes a refund obligation.

#afig("a-uc04-ds-intent", [UC4 design sequence diagram, B1–B4 with A2 and E2 (commerce-svc, identity-svc behind `UserAppService`). The rate limit refuses before any transaction opens; an unreachable identity-svc fails the request closed.])

#afig("a-uc04-ds-terms", [UC4 design sequence diagram, B3–B4 with A1 and A3, inside `PurchaseIntentAppService.join`. The sharer is resolved before the team lock, so its lookup stays out of the window in which buyers queue on the team row; `PurchaseIntent.issue` is a static factory.])

#afig("a-uc04-ds-fulfil", [UC4 design sequence diagram, B6–B8 with A5, A6, A8 and E1, order side (commerce-svc). Locks follow the fixed order; every rejection is returned before the `«create»` of the order, the first business write.])

#afig("a-uc04-ds-admit", [UC4 design sequence diagram, B7 with A6, A7 and A8, inside `PaidGroupDealService.joinConfirmed`. The receipt replays a repeated payment; a business refusal is committed as `compensation_required`, never thrown away with the money.])

#afig("a-uc04-ds-events", [UC4 design sequence diagram, B10 with A1: event subscribers at commit (commerce-svc). Subscribers that must agree with the join run before commit, in `@Order`; the rest run after commit and cannot undo it.])

#afig("a-uc04-ds-race", [UC4 design sequence diagram, A7: two payments race for the last seat (commerce-svc). `txA` and `txB` are two concurrent invocations (threads) of the one `StripeCheckoutService` bean; the team row lock orders them, and the loser takes the A6 path.])

=== Operation Specifications <sec-a-uc04-ops>

@tbl-a-uc04-ops specifies the eight operations that carry UC4's rules. Signatures are those in the code. An error is cited as its enum constant, with the wire code in brackets on first use.

#atable(
  [UC4 operation specifications (commerce-svc). Errors are `BizException` error codes unless named as another exception.],
  <tbl-a-uc04-ops>,
  columns: (1.25fr, 1fr, 1fr, 1.2fr, 1fr),
  header: ([Operation; transaction and lock], [Purpose; parameters and return], [Preconditions], [Postconditions and effects], [Errors]),
  [`PurchaseIntentAppService.createIntent(user: UUID, operation: String, businessId: UUID, inviteRef: String, quoteId: UUID): UUID`. `MANDATORY`, inside the transaction that `PaymentBrowserService.createIntent` (same signature) opens with `TransactionTemplate`, 10 s; holds lock 1 until commit.], [Entry for B1–B4. `user` from the session; `businessId` is the team for JOIN. Returns the intent id.], [JOIN rate allowance left; `X-BlocBuy-Request` header checked by the controller; no `quoteId` on JOIN.], [One `biz_purchase_intent` row committed with the team's `dealPrice`, `quoteValidUntil` = earlier of now + 2,400 s and the team's `expireTime`, and the sharer if attributable; or nothing.], [`RATE_LIMITED` (`COMMON_429_001`, from `PaymentBrowserService`); `PURCHASE_INTENT_TERMS_INVALID`; `USER_NOT_FOUND`, `ACCOUNT_DISABLED`; `IdentityUnavailableException` (HTTP 500); the errors of `lockJoinTerms`.],
  [`GroupDealPurchaseSupport.resolveSharer(inviteRef: String, dealId: UUID, buyerId: UUID): Optional<UUID>`. Caller's transaction, before lock 1; no lock.], [A1: turns an invite reference into the sharer to freeze on the intent.], [None; a blank reference is allowed.], [No write. The sharer if the invite names this team and another member; empty otherwise.], [None: an unknown or non-attributing reference is ignored, never refused.],
  [`GroupDealPurchaseSupportImpl.lockJoinTerms(dealId: UUID, buyerId: UUID): GroupDealPurchaseSupport.JoinTerms`. Caller's transaction; takes lock 1 (team, `SELECT … FOR UPDATE`).], [Checks B3 and returns the team's frozen terms.], [Both ids present.], [No write; team row locked; clock read after the lock.], [`GROUPBUY_PLACEHOLDER` (`GROUPBUY_404_001`, team not found), `DEAL_CLOSED`, `DEAL_FULL`, `ALREADY_MEMBER`, `INVALID_PARTICIPATION`.],
  [`OrderFulfilmentAdapter.lockForSettlement(quote: CheckoutQuote): void`. `MANDATORY`; takes lock 1 (team) then lock 2 (intent).], [Takes the business locks in the fixed order before the payment module takes lock 4.], [Settlement transaction open.], [No write; team and intent rows locked.], [`IllegalStateException` if the stored intent no longer equals the quote (rolls back).],
  [`OrderFulfilmentAdapter.fulfil(event: PaymentSucceeded): PaymentBusinessPort.Outcome`. `MANDATORY`; re-takes locks 1 and 2 (already held).], [Turns a confirmed payment into a membership and an order (B6–B8).], [Payment locked and `SUCCEEDED` by the caller; quote not expired; quote equals the stored intent.], [Accepted: member, `PENDING` order linked to the member, intent fulfilled, one unit consumed. Rejected: no business write.], [Rejections returned: `INTENT_ALREADY_FULFILLED`, `INTENT_EXPIRED`, `BUYER_INACTIVE`, `PRODUCT_UNAVAILABLE`, `GROUP_REJECTED`. Thrown: `IllegalStateException`, `IdentityUnavailableException`.],
  [`PaidGroupDealService.joinConfirmed(dealId: UUID, paid: PaidParticipation, sharerUserId: UUID): ParticipationResult`. Joins the caller's transaction (timeout 10 s); lock 5 (receipt), then lock 1 (already held).], [Admits a confirmed payment (B7) and publishes the events.], [Payment verified and bound to this buyer and team by the caller.], [Applied: size + 1, member row with the sharer (a self-referral is dropped), receipt `applied`, `MemberJoined`, and `TeamSucceeded` if full. Refused: receipt `compensation_required` with the reason code.], [Thrown: `INVALID_PARTICIPATION`, `PAYMENT_REUSED` (fingerprint differs). Business refusals are returned, not thrown.],
  [`GroupDeal.joinPaid(payment: PaidParticipation, alreadyMember: boolean, now: Instant): PaidAdmission`. Pure; called under lock 1.], [The admission rule, checked in order: price, new member, window, seat.], [`now` read after the team lock.], [Returns a new snapshot with `currentSize` + 1, `SUCCEEDED` with `successTime` when the target is reached.], [`PAYMENT_MISMATCH`, `ALREADY_MEMBER`, `DEAL_CLOSED`, `DEAL_FULL`, `INVALID_PARTICIPATION`.],
  [`OrderLifecycleSubscriber.onTeamSucceeded(event: TeamSucceeded): void`. `BEFORE_COMMIT`, `@Order(10)`, synchronous, inside the settlement transaction.], [Confirms the team's orders (B10).], [The joiner's own order already saved.], [All `pending` orders of the team become `confirmed`; a replay changes no rows.], [Any exception rolls back the whole settlement (E1).],
)

=== State Machines <sec-a-uc04-states>

UC4 changes the state of four objects, each drawn once in the part that owns its lifecycle. The team (`DealStatus`) is drawn in @sec-a-uc05: UC4 causes two of its changes: a join that leaves the team `OPEN` with one more member (an internal transition), and the join that fills it (`OPEN` → `SUCCEEDED`, allowed by `DealStatus.canTransitionTo`, which `requireJoinWindow` checks). The order (`OrderStatus`) is also in @sec-a-uc05: UC4 creates it `PENDING` and, on the filling join, `confirmByDealId` moves the team's orders to `CONFIRMED`. The purchase intent is in @sec-a-uc03 (UC4 marks it fulfilled), and the checkout payment (`CREATED` → `SUCCEEDED`) in @sec-a-pay-upfront.

=== Patterns Applied <sec-a-uc04-patterns>

Four patterns are in the UC4 code. Each answers a problem that the transition raised; @tbl-a-uc04-patterns maps their roles to classes as the code wires them. Only Observer is a GoF pattern, and it is realised through Spring rather than by hand. The module interfaces `GroupDealPurchaseSupport`, `GroupDealSettlementSupport` and `CatalogPurchaseSupport` are not claimed as a GoF Façade: they are each module's narrow published interface, kept narrow by the architecture tests (AD-18), and they play the inside role of Ports and Adapters. The Factory Method of team creation belongs to UC3 (@sec-a-uc03); a join never creates a team.

#atable(
  [Patterns in the UC4 design: the problem first, then the roles mapped to real classes, then the consequences with their liability.],
  <tbl-a-uc04-patterns>,
  columns: (0.75fr, 1fr, 1.3fr, 1fr),
  header: ([Pattern (kind)], [Problem in UC4], [Roles → classes], [Consequences]),
  [Observer (GoF, behavioural), realised as in-process publish–subscribe. @fig-a-uc04-dc-events, @fig-a-uc04-ds-events], [A filling join must confirm orders, write an integration record, credit the sharer and evict a cache. Calling each from `PaidGroupDealService` would tie group-buy to the order, outbox, sharing and cache code.], [Subject (holds and notifies the observers): Spring's multicaster behind `ApplicationEventPublisher`; the transaction synchronisation delivers each call at its phase. Event sources, which hold no observers and only call `publishEvent`: `PaidGroupDealService`, `StripeCheckoutService`. Notifications: `MemberJoined`, `TeamSucceeded`, `PaymentSucceeded`. ConcreteObservers: `OrderLifecycleSubscriber`, `IntegrationOutboxWriter`, `ShareCreditSubscriber`, `OpenDealOfferCacheSubscriber`; update() = their `on…` methods. Unlike GoF, observers register by annotation, not by `attach`.], [A new consequence is a new subscriber (open–closed). Liability: the order and phase of subscribers become part of the design (`@Order`, `BEFORE_COMMIT`, `AFTER_COMMIT`), and the flow is no longer visible in the publisher's code.],
  [Idempotent Receiver (enterprise integration pattern). @fig-a-uc04-ds-fulfil, @fig-a-uc04-ds-admit], [Stripe delivers a webhook at least once, and a settled payment can be reported again.], [Receiver: `StripeCheckoutService`, whose event claim is in @sec-a-pay-upfront. UC4's own guards: the spent intent in `OrderFulfilmentAdapter.fulfil` (`fulfilledPaymentId`), and the participation receipt in `PaidGroupDealRepository.reserveAndLock`.], [A duplicate changes nothing and returns the first result (A8). Liability: the guards are written in the same transaction, so a rolled-back settlement must be redelivered to be retried (E1).],
  [Ports and Adapters (architectural). @fig-a-uc04-dc-intent, @fig-a-uc04-dc-fulfil], [Payment must settle money without knowing teams or orders, and the account check must work whether identity is local or remote.], [Ports: `PaymentBusinessPort`, `PaymentIntentPort`, `UserAppService`. Adapters: `OrderFulfilmentAdapter`, `PurchaseIntentAppService`, `RemoteUserAppService`. (`CheckoutGateway` for Stripe: @sec-a-pay-upfront.)], [Payment depends only on interfaces it owns (dependency inversion); identity can be local or remote without changing UC4. Liability: one more indirection per call.],
  [Transactional Outbox (architectural). @fig-a-uc04-ds-events], [notification-svc must learn that a team succeeded, but a broker publish inside the settlement transaction could be lost or sent for a rolled-back join.], [Writer: `IntegrationOutboxWriter` (`BEFORE_COMMIT`) through `OutboxMapper` into `sys_outbox`; relay and consumer in @sec-a-notifications.], [The event exists if and only if the join committed. Liability: delivery is at least once and later than the commit.],
)

*What changes cheaply.* A new consequence of a join (for example a loyalty point) is one more subscriber, with no change to `PaidGroupDealService`. A second payment provider is a new implementation of `CheckoutGateway` behind Pay upfront; UC4's classes do not change. identity-svc can be local or remote by swapping the `UserAppService` implementation. What is expensive is changing the lock order, which every settling path shares.

=== Flow-to-Design Trace <sec-a-uc04-trace>

@tbl-a-uc04-trace shows that each step of @tbl-a-uc04-flows passes from an analysis message to design operations.

#atable(
  [UC4 flow-to-design trace: each flow step, its analysis message and the design operations that realise it.],
  <tbl-a-uc04-trace>,
  columns: (auto, 1fr, 1.6fr, auto),
  header: ([Step], [Analysis message], [Design operations], [Figures]),
  [B1], [`join`, `requestJoin`], [`StripeCheckoutController.intent` → `PaymentBrowserService.createIntent` → `PurchaseIntentAppService.createIntent`], [@fig-a-uc04-ds-intent],
  [B2], [`Account.isActive`], [`UserAppService.requireActive` (remote: `RemoteUserAppService`)], [@fig-a-uc04-ds-intent],
  [B3], [`Team.checkAdmission`], [`GroupDealPurchaseSupportImpl.lockJoinTerms` → `PaidGroupDealRepository.lockDeal`, `currentTime`, `hasActiveMember`; `GroupDeal.requireJoinWindow`, `requireAvailableSeat`, `requireNewMember`], [@fig-a-uc04-ds-terms],
  [B4], [«create» `PurchaseIntent`], [`CatalogPurchaseSupport.requirePurchasable`; `PurchaseIntent.issue` («static»), `attributedTo`; `PurchaseIntentRepository.save`], [@fig-a-uc04-ds-terms, @fig-a-uc04-ds-intent],
  [B5], [`requestPayment`, `paid`, `paymentConfirmed`], [Pay upfront: webhook, verification, event claim (@sec-a-pay-upfront)], [—],
  [B6], [`isPayable`, `isActive`, `isPurchasable`], [`OrderFulfilmentAdapter.lockForSettlement` → `GroupDealSettlementSupport.lockTeam`, `PurchaseIntentRepository.lockById`; `CheckoutQuote.expiredAt` (by the caller); `fulfil` → `PurchaseIntent.fulfilled`, `expiredAt`, `UserAppService.requireActive`, `CatalogPurchaseSupport.requirePurchasable`], [@fig-a-uc04-ds-fulfil],
  [B7], [`Team.admit`, «create» `Membership`], [`PaidGroupDealService.joinConfirmed` → `PaidGroupDealRepository.reserveAndLock`, `lockDeal`, `hasActiveMember`; `GroupDeal.joinPaid`; `insertJoinedMember` (`PaidGroupDealMapper.updateDeal`, `insertMember`); `ParticipationResult.applied`; `complete`], [@fig-a-uc04-ds-admit],
  [B8], [«create» `Order`, `markFulfilled`, `takeOneUnit`], [`Order.create` («static»), `OrderRepository.save`, `GroupDealSettlementSupport.linkOrder`, `PurchaseIntentRepository.markFulfilled`, `CatalogPurchaseSupport.consumeOneUnit`], [@fig-a-uc04-ds-fulfil],
  [B9], [`Payment.recordOutcome`], [`fulfil` returns an accepted `PaymentBusinessPort.Outcome`; the outcome `FULFILLED` is recorded by Pay upfront (@sec-a-pay-upfront)], [@fig-a-uc04-ds-fulfil],
  [B10], [`Order.confirm` (loop)], [`ApplicationEventPublisher.publishEvent(TeamSucceeded)`; `OrderLifecycleSubscriber.onTeamSucceeded` → `OrderRepository.confirmByDealId`; `IntegrationOutboxWriter.onTeamSucceeded` → `OutboxMapper.insert`], [@fig-a-uc04-ds-admit, @fig-a-uc04-ds-events],
  [B11], [`showJoined`], [Receipt read by `PaymentBrowserService.receipt` (@sec-a-pay-upfront); teams page by UC8 (@sec-a-uc08)], [—],
  [A1], [`resolveSharer`, `attributes`], [`GroupDealPurchaseSupport.resolveSharer` → `ShareInviteRepository.findByRef`, `ShareInvite.attributes`; after commit `ShareCreditSubscriber.onMemberJoined` → `ShareCreditRepository.recordIfAbsent`], [@fig-a-uc04-ds-terms, @fig-a-uc04-ds-events],
  [A2], [—], [`RateLimitGateway.tryAcquire` returns false → `RATE_LIMITED`], [@fig-a-uc04-ds-intent],
  [A3], [`showRejected`], [`BizException` from `lockJoinTerms`: `GROUPBUY_PLACEHOLDER`, `DEAL_CLOSED`, `DEAL_FULL`, `ALREADY_MEMBER`], [@fig-a-uc04-ds-terms],
  [A4], [—], [Pay upfront (@sec-a-pay-upfront)], [—],
  [A5], [`requireRefund`, «create» `Refund`], [`CheckoutQuote.expiredAt(now)` true → `Outcome` `BUSINESS_EXPIRED` without `fulfil`; or `fulfil` returns `INTENT_EXPIRED`, `BUYER_INACTIVE` or `PRODUCT_UNAVAILABLE`; then `CheckoutPaymentRepository.requireRefund`, outcome `REFUND_REQUIRED` (@sec-a-pay-upfront)], [@fig-a-uc04-ds-fulfil],
  [A6], [`admit` refused; `requireRefund`; «create» `Refund`], [`GroupDeal.joinPaid` throws → `ParticipationResult.compensation` → `complete` → `Outcome` `GROUP_REJECTED` → `requireRefund` (@sec-a-pay-upfront)], [@fig-a-uc04-ds-admit, @fig-a-uc04-ds-fulfil],
  [A7], [`admit` one at a time], [`OrderFulfilmentAdapter.lockForSettlement` serialises on the team row; the second `joinPaid` throws `DEAL_CLOSED`], [@fig-a-uc04-ds-race],
  [A8], [—], [Event claim in Pay upfront; here `fulfil` replays a fulfilled intent and `reserveAndLock` returns a completed receipt], [@fig-a-uc04-ds-fulfil, @fig-a-uc04-ds-admit],
  [E1], [—], [`IdentityUnavailableException` or `IllegalStateException` propagates and the settlement transaction rolls back; Stripe redelivers (@sec-a-pay-upfront)], [@fig-a-uc04-ds-fulfil],
  [E2], [—], [`RemoteUserAppService.requireActive` throws `IdentityUnavailableException`; the intent transaction rolls back], [@fig-a-uc04-ds-intent],
)
