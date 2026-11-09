#import "lib-a.typ": *

// Code in this part's tables prints at 8 pt (the report default would scale it below that).
#show table: it => {
  show raw: set text(size: 8pt)
  it
}

== UC3 Initiate a Team by Paying <sec-a-uc03>

UC3 opens a new team for a listing. A signed-in buyer is shown a team price quote of their own and pays it first; only after Stripe confirms the payment does commerce create the team, with the payer as leader and first paid member, and the buyer's pending order. The team does not exist while the buyer pays, so the design reserves its identity when it freezes the terms. Payment, settlement and replay work as in UC4 (@sec-a-uc04) and are referenced, not redrawn. This part draws what is specific to a start: the quote, the listing lock, the reserved team, team creation by Factory Method and the purchase intent's lifecycle. Every name here exists in the code on this branch. UC3 is designed and built; the evidence for each flow is listed in section 2.2.

=== Use Case and Flows of Events <sec-a-uc03-flows>

- *Actors.* Buyer (primary, the future team leader). Stripe (secondary, through the included use case Pay upfront, @sec-a-pay-upfront).
- *Trigger.* The buyer presses "Start a bloc" on a listing page.
- *Services.* commerce-svc owns every step (its catalog, payment, order and group-buy modules). identity-svc answers one question: is this buyer's account active?
- *Preconditions.* The buyer is signed in (UC2) and active. Online payment is switched on. The listing is on sale, in stock and priced in Singapore dollars, and an admin has set its team terms and a team price (UC9, @sec-a-uc09).
- *Postconditions on success.* Exactly one open team exists with the payer as its leader and only member, a target size of at least two and a deadline from the listing's team lifetime. The buyer has one pending order, the stock has dropped by one, the purchase intent is fulfilled and the payment has exactly one outcome, `FULFILLED`.
- *Postconditions on refusal after payment.* No team, membership or order exists. The payment's outcome is `REFUND_REQUIRED` and exactly one refund obligation is recorded.

@tbl-a-uc03-flows numbers every step. The diagrams repeat these numbers on their messages, and @tbl-a-uc03-trace maps each step to the design operations that realise it.

#atable(
  [UC3 flows of events. B = basic flow, A = alternate flow, E = exception flow; "At" is the step where an alternate or exception flow branches off.],
  <tbl-a-uc03-flows>,
  columns: (auto, auto, 1fr),
  header: ([Step], [At], [What happens]),
  [*B1*], [], [The buyer asks for a team price for the listing.],
  [*B2*], [], [The system finds the listing and checks that the buyer's account is active.],
  [*B3*], [], [The system checks that the listing's latest team price is in force now and is below the solo price, and issues a price quote that belongs to this buyer: the team price and the time until which it is valid, at most 15 minutes and never past the end of the team price.],
  [*B4*], [], [The listing page shows the quote with a countdown. The buyer chooses to continue to payment.],
  [*B5*], [], [The system checks again that the buyer's account is active.],
  [*B6*], [], [The system checks that the listing can start a team (on sale, in stock, in Singapore dollars, team terms set) and that the quote is this buyer's, is for this listing, has not lapsed and is still below the solo price.],
  [*B7*], [], [The system freezes the purchase terms as a purchase intent: the quoted price, the currency, the target size and the team's deadline from the listing's team lifetime. It reserves the identity of the team that does not exist yet. The intent is payable for 40 minutes.],
  [*B8*], [], [The buyer pays the frozen amount: «include» Pay upfront via Stripe Checkout (@sec-a-pay-upfront). The included use case ends when Stripe reports a paid Checkout session.],
  [*B9*], [], [The system checks again that the intent is unspent and has not lapsed, that the buyer is active and that the listing is purchasable.],
  [*B10*], [], [The system opens the team: the paid amount must equal the frozen price and the team's deadline must lie ahead. The payer becomes the leader and only member; the team is open with one of its seats taken.],
  [*B11*], [], [The system creates the buyer's order as pending, marks the intent fulfilled and takes one unit of stock.],
  [*B12*], [], [The included use case records the payment's outcome as fulfilled (@sec-a-pay-upfront).],
  [*B13*], [], [The receipt shows "Payment received"; the account page shows the team with one member, and the listing page offers the leader a share link (UC7, @sec-a-uc07).],
  [*A1*], [B1], [A caller starts without a quote (the web page never does): B3–B4 are skipped, and B6 uses the latest team price, which must stay in force for the whole 40-minute payment window.],
  [*A2*], [B3], [The listing is not on sale, has no team price in force, or its team price is not below the solo price: no quote is issued, the sheet shows the reason and nothing is stored.],
  [*A3*], [B6], [The quote has lapsed, belongs to another buyer or listing, or no longer undercuts the solo price; or the listing can no longer start a team: no intent is created. The sheet stays open with "Get a new price", which returns to B1.],
  [*A4*], [B8], [The buyer abandons the payment or the Checkout session expires: no team, membership or order exists. The unpaid intent lapses and is later marked expired (@sec-a-uc03-states).],
  [*A5*], [B9], [The intent has lapsed, the buyer is no longer active, or the listing is no longer purchasable: the payment is owed back, no team, membership or order is created, and one refund obligation is recorded. The receipt shows "Refund pending".],
  [*A6*], [B10], [The team refuses to open: the paid amount differs from the frozen price, or the deadline has passed. The participation receipt records the refusal and the payment is owed back as in A5. A passed deadline is almost always caught at B9.],
  [*A7*], [B8], [Stripe reports the same payment again: the recorded outcome, the spent intent or the participation receipt returns the first result, and nothing is created twice. A payment presented again for different terms is refused.],
  [*E1*], [B9–B11], [A technical failure, such as identity-svc being unreachable or another team's settlement taking the last unit of stock between B9 and B11: nothing of B9–B12 is kept. Stripe reports the payment again, which repeats B9; a listing now out of stock is refused there (A5).],
  [*E2*], [B2, B5], [identity-svc is unreachable when the quote or the intent is requested: the request fails and nothing is stored.],
)

=== Analysis Class Diagram <sec-a-uc03-ac>

@fig-a-uc03-ac is the analysis model, with no platform. The Buyer works through `StartTeamUI`; Stripe reaches the system only through the gateway boundary `PaymentGateway`. `StartTeamControl` realises UC3. Entity names are the canonical ones of A.1; the association between a product and the teams it offers is drawn here. `TeamPricePolicy` (a dated team price set by an admin, UC9) and `PriceQuote` (the price shown to one buyer for a short time) belong to the product: the control asks the `Product` to issue a quote and later to accept it, so the pricing rule stays with the listing. Every team is opened by exactly one purchase intent; that link is how the design reserves the team before it exists.

=== Analysis Sequence Diagrams <sec-a-uc03-as>

The basic flow is drawn before payment (@fig-a-uc03-as-basic-1) and after it (@fig-a-uc03-as-basic-2); each message carries its step. After payment the checks are repeated, because the terms may lapse while the buyer pays. @fig-a-uc03-as-alt-refund is the significant alternate path, a start refused after the money has moved (A5, A6). A1 is a path the web page never takes, and A7, E1 and E2 need design concepts (a replayed event, a transaction, a remote call), so they appear only in the design sequences.

#afig("a-uc03-ac", [UC3 analysis class diagram (UML class notation, BCE stereotypes). `StartTeamControl` is the only object that talks to both boundaries and to the entities; the `Product` owns the team price and the quotes, and each `Team` is opened by exactly one `PurchaseIntent`.])

#afig("a-uc03-as-basic-1", float: true, [UC3 analysis sequence diagram, basic flow part 1 (B1–B8), with A2 and A3. The `Product` both issues the quote and later accepts it; the two `break` fragments refuse a buyer before any money moves.])

#afig("a-uc03-as-basic-2", float: true, [UC3 analysis sequence diagram, basic flow part 2 (B8–B13). The checks of B9 repeat those before payment; the control creates the `Team`, which creates the leader's `Membership`, and then the `Order`.])

#afig("a-uc03-as-alt-refund", float: true, [UC3 analysis sequence diagram, alternate flows A5 and A6 (start refused after payment). When any check fails, the control creates a `Refund` through the `Payment` instead of a team.])

=== Transition: Strategies and Mapping <sec-a-uc03-transition>

The strategies of @sec-a-strategies, tagged in the design diagrams, change UC3 as follows:

- *S1 Language and framework* (AD-01, AD-18; NFR-05, 06, 26): impacted `PriceQuote`, `TeamPricePolicy`, `Membership` → resultant immutable records (`TeamPriceQuote`, `TeamPricePolicy`, `CatalogPurchaseSupport.StartQuote`, `NewDealTerms`, `PaidParticipation`, `ParticipationResult`) and the configuration `GroupDealCreationConfig`; dynamics @fig-a-uc03-ds-create.
- *S2 Presentation* (AD-09; NFR-07, 12, 16, 25): impacted `StartTeamUI` → resultant `ProductDetailPage`, `QuoteSheet`, `useQuote`, `useCheckout` (not drawn); the browser sends ids, never a price (NFR-28).
- *S3 Distribution* (AD-02, AD-06, AD-09; NFR-05, 07, 64, 66): impacted `StartTeamControl`, `Account` → resultant a control split by step and module behind `TeamPriceAppService`, `PaymentIntentPort`, `CatalogPurchaseSupport`, `PaymentBusinessPort` and `GroupDealSettlementSupport`; `UserAppService` is, in commerce-svc, the HTTP client `RemoteUserAppService`, which fails closed; dynamics @fig-a-uc03-ds-quote, @fig-a-uc03-ds-intent.
- *S4 Persistence* (AD-03, AD-11; NFR-05, 13): impacted `Product`, `TeamPricePolicy`, `PriceQuote`, `PurchaseIntent`, `Team`, `Membership`, `Order` → resultant MyBatis repositories in commerce's database (`ProductRepository`, `TeamPriceRepository`, `PurchaseIntentRepository`, `PaidGroupDealRepository`, `OrderRepository`). @fig-a-uc03-dc-create shows the representative chain `PaidGroupDealRepository` → `PaidGroupDealRepositoryImpl` → `PaidGroupDealMapper`; every other repository follows it.
- *S5 External interface* (AD-06; NFR-21, 22, 23, 31, 50, 66): impacted `PaymentGateway` → resultant the webhook boundary and `CheckoutGateway` of @sec-a-pay-upfront; UC3 starts its own design after the webhook, at `PaymentBusinessPort.fulfil`.
- *S6 Security* (AD-05, AD-09; NFR-01, 46, 47, 58, 68): impacted `StartTeamUI`, `PriceQuote` → resultant buyer identity from the Sa-Token session, never from the request; the `X-BlocBuy-Request` check in `StripeCheckoutController.checkCsrf`; owner-only quote reads (`findQuoteForOwner`), so another buyer's quote looks missing.
- *S7 Scheduling* (AD-16; NFR-15, 32, 61): not applied in UC3's steps; the sweep of lapsed intents is in @sec-a-pay-upfront, its effect in @fig-a-uc03-st-intent.
- *S8 Concurrency* (AD-02, AD-03; NFR-13, 21, 27, 30): impacted `Product`, `PurchaseIntent`, `Team` → resultant the product row lock `ProductRepository.lockById` while a quote or intent is priced; the global lock order team → purchase intent → order → payment, which in settlement (no order row yet) is team (no row yet for a start) → intent → payment, then the participation receipt and the conditional stock update `consumeOneUnit`; unique indexes `uq_biz_purchase_intent_fulfilled_payment`, `uq_biz_group_participation_receipt_payment`, `uq_biz_group_member_payment_id`, `uq_biz_order_payment_id`. One settlement transaction writes the receipt, team, leader, order, intent and stock; the S8 justification in @sec-a-strategies (atomic seat and money, one database, lock order, 10 s timeout) covers it. Dynamics @fig-a-uc03-ds-fulfil, @fig-a-uc03-ds-create.
- *S9 Domain and integration events* (AD-04; NFR-30, 39, 59, 60, 61, 63): impacted none of UC3's entities: a new team has one member and a target size of at least two, so a start publishes no group-buy event. The only event is the payment side's `PaymentSucceeded` (outbox `PaymentSucceededV1`, @sec-a-notifications).

@tbl-a-uc03-map shows where each analysis object lands. Every class on the four design class diagrams appears in it.

#atable(
  [UC3 analysis → design mapping. Every analysis object of @fig-a-uc03-ac, and every design class of @fig-a-uc03-dc-quote to @fig-a-uc03-dc-create.],
  <tbl-a-uc03-map>,
  columns: (0.95fr, 0.6fr, 2.6fr, 0.55fr, 0.8fr),
  header: ([Analysis object], [Stereotype], [Design elements (layer, module)], [Strategies], [Figures]),
  [`StartTeamUI`], [boundary], [`TeamPriceController` (`POST /api/v1/team-price-quotes`), `StripeCheckoutController` (`POST /api/v1/payments/intents`). Frontend: not drawn.], [S2, S6], [@fig-a-uc03-dc-quote, @fig-a-uc03-dc-intent],
  [`PaymentGateway`], [boundary], [Designed in @sec-a-pay-upfront; not drawn here.], [S5], [—],
  [`StartTeamControl` (quote, B1–B4)], [control], [Entry `TeamPriceAppService.issueQuote` → `TeamPriceAppServiceImpl` (application, catalog).], [S3, S8], [@fig-a-uc03-dc-quote],
  [`StartTeamControl` (intent, B5–B7)], [control], [Entry `PurchaseIntentAppService.createIntent` (application, order), via `PaymentBrowserService` (payment); `CatalogPurchaseSupportImpl` (catalog, not drawn) prices the start.], [S3, S8], [@fig-a-uc03-dc-intent],
  [`StartTeamControl` (after payment, B9–B11)], [control], [Entry `OrderFulfilmentAdapter.fulfil` (application, order) → `PaidGroupDealService.startConfirmed` (group-buy).], [S3, S8], [@fig-a-uc03-dc-fulfil, @fig-a-uc03-dc-create],
  [`Account`], [entity], [Port `UserAppService`; adapter `RemoteUserAppService` (not drawn); `User` lives in identity-svc (@sec-a-uc02).], [S3], [@fig-a-uc03-dc-quote, @fig-a-uc03-dc-intent, @fig-a-uc03-dc-fulfil],
  [`Product`], [entity], [Aggregate root `Product`, port `ProductRepository`; other modules use `CatalogPurchaseSupport`. `issueTeamQuote` → `issueQuote`; `startTerms` → `lockQuotedStart`.], [S3, S4, S8], [@fig-a-uc03-dc-quote, @fig-a-uc03-dc-intent, @fig-a-uc03-dc-fulfil],
  [`TeamPricePolicy`], [entity], [Record `TeamPricePolicy`; `priceAt` → `PricingStrategy.quoteTeamPrice` (`DualPricingStrategy`, via `PricingStrategyResolver`) → `requireQuotable`. Port `TeamPriceRepository`, table `biz_team_price_policy`.], [S1, S4], [@fig-a-uc03-dc-quote],
  [`PriceQuote`], [entity], [Record `TeamPriceQuote`; `insertQuote`, `findQuoteForOwner`; table `biz_team_price_quote`.], [S1, S4, S6], [@fig-a-uc03-dc-quote],
  [`PurchaseIntent`], [entity], [Aggregate root `PurchaseIntent`, port `PurchaseIntentRepository`, table `biz_purchase_intent`; the reserved team is `Terms.dealId` from `UuidV7.next`.], [S4, S8], [@fig-a-uc03-dc-intent, @fig-a-uc03-dc-fulfil],
  [`Team`], [entity], [Aggregate root `GroupDeal`, created through `GroupDeal.startPaid`; port `PaidGroupDealRepository`, table `biz_group_deal`.], [S4, S8], [@fig-a-uc03-dc-create],
  [`Membership`], [entity], [Leader row in `biz_group_member` (`insertStartedDeal`); `PaidParticipation`, `ParticipationResult`, `ParticipationReceipt` (`biz_group_participation_receipt`).], [S4, S8], [@fig-a-uc03-dc-fulfil, @fig-a-uc03-dc-create],
  [`Order`], [entity], [Aggregate root `Order`, port `OrderRepository`, table `biz_order`.], [S4], [@fig-a-uc03-dc-fulfil],
  [`Payment`], [entity], [`CheckoutPayment` (@sec-a-pay-upfront); UC3 sees only `PaymentBusinessPort` and its `Outcome`.], [S4, S5], [@fig-a-uc03-dc-fulfil],
  [`Refund`], [entity], [No class here: `requireRefund` in @sec-a-pay-upfront; lifecycle in @sec-a-uc06.], [S4], [—],
  [(none)], [—], [Ports `TeamPriceAppService`, `PaymentIntentPort`, `CatalogPurchaseSupport`, `PaymentBusinessPort`, `GroupDealSettlementSupport`, service `PaymentBrowserService` (S3); `CatalogPurchaseSupport.StartQuote`, `NewDealTerms`, `PaymentBusinessPort.Outcome`, `UuidV7` (S1); `PaidGroupDealCreator`, `StandardPaidGroupDealCreator`, `GroupDealCreationConfig` (S1, Factory Method); `PaidGroupDealRepositoryImpl`, `PaidGroupDealMapper` (S4).], [S1, S3, S4], [@fig-a-uc03-dc-intent, @fig-a-uc03-dc-fulfil, @fig-a-uc03-dc-create],
)

=== Design Class Diagrams <sec-a-uc03-dc>

The design model has 39 classes and interfaces, drawn in four slices along the flow: the quote in the catalog module (@fig-a-uc03-dc-quote), the purchase intent before payment (@fig-a-uc03-dc-intent), fulfilment in the order module (@fig-a-uc03-dc-fulfil) and the creation of the team in the group-buy module (@fig-a-uc03-dc-create). All four run in commerce-svc; notation is as in UC4 (@sec-a-uc04-dc), and «leaf» marks a final operation, «abstract» an abstract one.

#afig("a-uc03-dc-quote", float: true, [UC3 design class diagram, slice 1: the team price quote in the catalog module (UML, commerce-svc). `TeamPriceAppServiceImpl` prices through the `PricingStrategy` interface and never reads a price from the request; the quote is an immutable record stored through `TeamPriceRepository`.])

#afig("a-uc03-dc-intent", float: true, [UC3 design class diagram, slice 2: the START purchase intent before payment (UML, commerce-svc). The order module implements the payment module's `PaymentIntentPort` and reads the listing and the quote only through the catalog facade `CatalogPurchaseSupport`, which hands back a `StartQuote` snapshot.])

#afig("a-uc03-dc-fulfil", float: true, [UC3 design class diagram, slice 3: fulfilment of a START in the order module (UML, commerce-svc). `OrderFulfilmentAdapter` is the Adapter behind `PaymentBusinessPort`; it builds `NewDealTerms` and `PaidParticipation` from the frozen intent and creates the `Order`.])

#afig("a-uc03-dc-create", float: true, [UC3 design class diagram, slice 4: creation of the team by Factory Method in the group-buy module (UML, commerce-svc). `PaidGroupDealService` holds only the abstract Creator; `GroupDealCreationConfig` alone names the ConcreteCreator, and only the creator can call the package-private `GroupDeal.startPaid`.])

=== Design Sequence Diagrams <sec-a-uc03-ds>

*Quote.* @fig-a-uc03-ds-quote issues the quote in one short transaction. It locks the product row first, the same lock UC9 takes to change a team price, so a quote never reads a price that an admin is revising (NFR-28). The clock is the database's, read after the lock. The quote reserves nothing: no stock, no team and no payment.

#afig("a-uc03-ds-quote", float: true, [UC3 design sequence diagram, B1–B4 with A2 and E2 (commerce-svc, identity-svc behind `UserAppService`). The product row lock comes first; the price is decided by the strategy for the listing's pricing mode and frozen in a new `TeamPriceQuote`.])

*Intent.* @fig-a-uc03-ds-intent creates the START intent inside the intent transaction. The HTTP request, the CSRF check and the controller are UC4's (@fig-a-uc04-ds-intent), except that `PaymentBrowserService` applies its rate limit to JOIN only. Inside `lockQuotedStart` catalog locks the product row, rejects a listing that cannot start a team, reads the database clock after the lock and accepts only the buyer's own unexpired quote for this listing that still undercuts the solo price. It never reads the latest policy, so a revision made after the quote was shown does not move its price (NFR-28). An unquoted start (A1) calls `lockStartQuote` instead, which takes the latest policy only if `TeamPricePolicy.startableAt` holds for the whole 40-minute window. The reserved team id comes from `UuidV7.next` (drawn as the class, because the call is static). It gives the settlement a stable lock target and a stable team id before the team exists.

#afig("a-uc03-ds-intent", float: true, [UC3 design sequence diagram, B5–B7 with A1, A3 and E2 (commerce-svc). One transaction holds the product row lock until the intent commits; the team's id is reserved here, and the team is created only after payment.])

*Settlement.* The webhook, the event claim, the payment lock (lock 3), the `BUSINESS_EXPIRED` check and the recording of the outcome belong to the included use case (@sec-a-pay-upfront). @fig-a-uc03-ds-fulfil starts where that settlement transaction calls the order module. For a start, lock 1 finds no team row: the reserved id is unique to one intent, and the intent's row lock (lock 2) serialises every writer that can reach it. Every check that can reject runs before the first write, so a rejection commits only the claim, the receipt and the refund obligation. The start's race is for the last unit of stock. Two settlements on different teams of the same listing hold no common lock, so `consumeOneUnit` is a conditional update that fails for the loser; the loser rolls back and is refused at B9 on redelivery (E1). @fig-a-uc03-ds-create is the creation of the team inside `startConfirmed`, where the participation receipt is lock 4.

#afig("a-uc03-ds-fulfil", float: true, [UC3 design sequence diagram, B9–B11 with A5, A6, A7 and E1, order side (commerce-svc). The adapter takes the team lock, which finds no row for a start, before the intent lock, as every path does; checks that reject run before the first write, and a lost race for stock rolls everything back.])

#afig("a-uc03-ds-create", float: true, [UC3 design sequence diagram, B10 with A6 and A7, inside `PaidGroupDealService.startConfirmed`. The receipt replays a repeated payment; the Creator's final `initiate` checks the terms and the price before its factory method creates the `GroupDeal`, and a refusal is committed as `compensation_required`.])

=== Operation Specifications <sec-a-uc03-ops>

@tbl-a-uc03-ops specifies the six operations that carry UC3's rules; `PurchaseIntent.issue` is specified by its statechart (@fig-a-uc03-st-intent) and `PricingStrategy.quoteTeamPrice` inside the `issueQuote` row. Signatures are those in the code. Errors are `BizException` error codes, enum constant first and wire code in brackets, unless named as another exception.

#atable(
  [UC3 operation specifications (commerce-svc).],
  <tbl-a-uc03-ops>,
  columns: (1.3fr, 0.85fr, 0.8fr, 1.15fr, 1.35fr),
  header: ([Operation; transaction and lock], [Purpose; parameters and return], [Preconditions], [Postconditions and effects], [Errors]),
  [`TeamPriceAppServiceImpl.issueQuote(productId: UUID, ownerId: UUID): TeamPriceQuote`. `REQUIRED`, 10 s; product row lock.], [Issues a quote (B2–B3). `ownerId` from the session. Returns the stored quote.], [`ownerId` not null.], [One `biz_team_price_quote` row priced by `quoteTeamPrice` after `requireQuotable` (on sale, below `originalPrice`, `validFrom` ≤ now < `validUntil`); `expiresAt` = earlier of now + 15 min and `validUntil`.], [`PRODUCT_NOT_FOUND` (`CATALOG_404_001`), `TEAM_PRICE_UNAVAILABLE` (`CATALOG_409_001`); `USER_NOT_FOUND` (`IDENTITY_404_001`), `ACCOUNT_DISABLED` (`IDENTITY_403_001`); `IdentityUnavailableException`.],
  [`PurchaseIntentAppService.createIntent(user: UUID, operation: String, businessId: UUID, inviteRef: String, quoteId: UUID): UUID`. `MANDATORY` (caller's 10 s transaction); holds the product lock until commit.], [Freezes server-owned terms (B5–B7); for START `businessId` is the product. Returns the intent id.], [Caller's transaction open.], [One `biz_purchase_intent` row: quote's amount and currency, the listing's target size, `dealId` = `UuidV7.next()`, `quoteValidUntil` = now + 2,400 s, `businessDeadline` = now + `lifetimeSeconds`.], [`PURCHASE_INTENT_TERMS_INVALID` (`ORDER_400_001`); `USER_NOT_FOUND`, `ACCOUNT_DISABLED`; `IdentityUnavailableException` (HTTP 500); the errors of `lockQuotedStart` or `lockStartQuote`.],
  [`CatalogPurchaseSupport.lockQuotedStart(productId: UUID, quoteId: UUID, buyerId: UUID): CatalogPurchaseSupport.StartQuote`, implemented by `CatalogPurchaseSupportImpl`. Caller's transaction; product row lock.], [Accepts the buyer's quote (B6) and adds the team terms.], [`quoteId` and `buyerId` not null.], [No write. Returns the quote's amount with the listing's `targetSize` and `lifetimeSeconds`; the latest policy is not read.], [`PRODUCT_NOT_FOUND`, `PRODUCT_NOT_PURCHASABLE` (`CATALOG_409_005`), `PRODUCT_NOT_STARTABLE` (`CATALOG_409_006`), `TEAM_PRICE_QUOTE_NOT_FOUND` (`CATALOG_404_002`), `TEAM_PRICE_QUOTE_EXPIRED` (`CATALOG_409_007`), `TEAM_PRICE_UNAVAILABLE`.],
  [`OrderFulfilmentAdapter.fulfil(event: PaymentSucceeded): PaymentBusinessPort.Outcome`. `MANDATORY`; re-takes lock 1 (no row for START) and lock 2.], [Turns a confirmed START payment into a team, a membership and an order (B9–B11).], [Payment locked and `SUCCEEDED`.], [Accepted: team, leader, `PENDING` order linked to the leader, intent fulfilled, one unit consumed. Rejected: no business write.], [Rejections returned: `INTENT_ALREADY_FULFILLED`, `INTENT_EXPIRED`, `BUYER_INACTIVE`, `PRODUCT_UNAVAILABLE`, `GROUP_REJECTED`. Thrown: `IllegalStateException` (stock gone, quote mismatch), `IdentityUnavailableException`.],
  [`PaidGroupDealService.startConfirmed(terms: NewDealTerms, paid: PaidParticipation): ParticipationResult`. Joins the caller's transaction (10 s if new); lock 4 (receipt).], [Opens the team for a confirmed payment (B10).], [Payment verified by the caller.], [Applied: `biz_group_deal` row `open`, size 1, leader row, receipt `applied`. Refused: receipt `compensation_required` with the reason code. No event.], [Thrown: `INVALID_PARTICIPATION` (`GROUPBUY_400_001`) for null input, `PAYMENT_REUSED` (`GROUPBUY_409_005`). Business refusals are returned, not thrown.],
  [`PaidGroupDealCreator.initiate(terms: NewDealTerms, payment: PaidParticipation, now: Instant): GroupDeal` «leaf». Pure; template method of the Factory Method.], [Applies the start rules, then calls the factory method `createGroupDeal`.], [`now` from the database clock.], [Returns an `OPEN` `GroupDeal` of size 1 led by the payer; persists nothing.], [`INVALID_PARTICIPATION` when an argument is null or now ≥ `terms.expireTime`; `PAYMENT_MISMATCH` (`GROUPBUY_409_001`) from `PaidParticipation.requirePrice`.],
)

=== State Machines <sec-a-uc03-states>

UC3 owns the lifecycle of the purchase intent, which UC4 and Pay upfront also drive. `PurchaseIntent` has no status column. Its state follows from two fields, `fulfilledPaymentId` and `expiredAt`, and from whether `quoteValidUntil` or `businessDeadline` has passed (`expiredAt(now: Instant): boolean`). @fig-a-uc03-st-intent draws the four states that result. `Lapsed` is real but unrecorded: a deadline has passed and the sweep (`PurchaseIntentExpiryService.expireOne`, every minute by default, @sec-a-pay-upfront) has not yet marked the row. A payment arriving in `Lapsed` is refused by the payment side (`BUSINESS_EXPIRED`), one arriving in `Expired` by `fulfil` (`INTENT_EXPIRED`); both become a refund (A5). In `Fulfilled`, a different payment gets `INTENT_ALREADY_FULFILLED`. The sweep never expires a paid intent; it drops it from its queue.

#afig("a-uc03-st-intent", [UML statechart of the purchase intent (`PurchaseIntent`, commerce-svc), drawn once for UC3, UC4 and Pay upfront. The state is derived from `fulfilledPaymentId`, `expiredAt` and the two deadlines; only `markFulfilled` and the sweep write it, and a late payment never fulfils a lapsed intent.])

UC3 also creates the team `OPEN` (`GroupDeal.startPaid`) and the order `PENDING` (`Order.create`); both lifecycles are drawn in @sec-a-uc05, the checkout payment's in @sec-a-pay-upfront.

=== Patterns Applied <sec-a-uc03-patterns>

@tbl-a-uc03-patterns lists the three patterns in the UC3 code. Factory Method is UC3's own; the other two are shared with UC1 (@sec-a-uc01) and UC4 (@sec-a-uc04-patterns).

#atable(
  [Patterns in the UC3 design: the problem first, then the roles mapped to real classes, then the consequences.],
  <tbl-a-uc03-patterns>,
  columns: (0.7fr, 1fr, 1.4fr, 1fr),
  header: ([Pattern (kind)], [Problem in UC3], [Roles → classes], [Consequences]),
  [Factory Method (GoF, creational)], [A paid start must always apply the same start rules: the terms are unexpired and the payment matches the frozen price. The class of team it creates may vary; that is the variable aspect. Naming the team class in the service would tie the settlement to it, and building the team in each caller would scatter the start rules.], [Creator: abstract `PaidGroupDealCreator`, whose final `initiate` checks the rules and then calls the factory method `createGroupDeal`. ConcreteCreator: `StandardPaidGroupDealCreator` (final), which calls the package-private `GroupDeal.startPaid`. Product: `GroupDeal` (no Product interface; it is the only product). Client: `PaidGroupDealService`, which holds the Creator in a field. Composition root: `GroupDealCreationConfig.paidGroupDealCreator`, the only place that names the ConcreteCreator. Collaboration: @fig-a-uc03-dc-create, @fig-a-uc03-ds-create.], [The client depends only on the abstract Creator; tests inject their own creator. A new kind of team needs a new subclass, not an edit of the service, and cannot skip the start rules. Liability: one ConcreteCreator per team class, and today only one exists, so the flexibility is unused. `GroupDeal` is `public final`, so a second team type would also need a new product class.],
  [Strategy (GoF, behavioural)], [The first-team price must follow the listing's pricing mode without branching on it in the quote service.], [Context `TeamPriceAppServiceImpl` (via `PricingStrategyResolver`); Strategy `PricingStrategy.quoteTeamPrice`; ConcreteStrategy `DualPricingStrategy`. @fig-a-uc03-dc-quote.], [A new mode adds a class, not a branch. Liability: one implementation today.],
  [Idempotent Receiver (enterprise integration pattern)], [Stripe delivers the same payment report at least once, and a replay must not open a second team.], [Receiver `PaidGroupDealService`; key: the payment id in `reserveAndLock` (unique receipt), with `ParticipationFingerprint` refusing reuse for other terms. @fig-a-uc03-ds-create.], [A replay returns the first result (A7). Liability: a rolled-back start waits for Stripe's redelivery (E1).],
)

=== Flow-to-Design Trace <sec-a-uc03-trace>

@tbl-a-uc03-trace shows that each step of @tbl-a-uc03-flows passes from an analysis message to design operations.

#atable(
  [UC3 flow-to-design trace: each flow step, its analysis message and the design operations that realise it.],
  <tbl-a-uc03-trace>,
  columns: (auto, 0.8fr, 2.2fr, 0.6fr),
  header: ([Step], [Analysis message], [Design operations], [Figures]),
  [B1], [`start`, `requestQuote`], [`TeamPriceController.issue` → `TeamPriceAppServiceImpl.issueQuote`], [@fig-a-uc03-ds-quote],
  [B2], [`Account.isActive`], [`ProductRepository.lockById`; `UserAppService.requireActive` (remote: `RemoteUserAppService`)], [@fig-a-uc03-ds-quote],
  [B3], [`Product.issueTeamQuote`, `TeamPricePolicy.priceAt`, «create» `PriceQuote`], [`TeamPriceRepository.findLatestPolicy`, `currentTime`; `PricingStrategyResolver.strategyFor`; `quoteTeamPrice` → `requireQuotable`; «create» `TeamPriceQuote`; `insertQuote`], [@fig-a-uc03-ds-quote],
  [B4], [`showQuote`, `continueToPayment`], [`QuoteResponse`; the frontend counts down], [@fig-a-uc03-ds-quote],
  [B5], [`requestStart`, `Account.isActive`], [`StripeCheckoutController.intent`, `checkCsrf` → `PaymentBrowserService.createIntent` → `PurchaseIntentAppService.createIntent` → `requireActive`], [@fig-a-uc04-ds-intent, @fig-a-uc03-ds-intent],
  [B6], [`Product.startTerms`, `PriceQuote.isValidFor`], [`start` → `CatalogPurchaseSupport.lockQuotedStart` → `lockById`, `currentTime`, `findQuoteForOwner`; «create» `StartQuote`], [@fig-a-uc03-ds-intent],
  [B7], [«create» `PurchaseIntent`], [`UuidV7.next`; `PurchaseIntent.issue`; `PurchaseIntentRepository.save`], [@fig-a-uc03-ds-intent],
  [B8], [`requestPayment`, `paid`, `paymentConfirmed`], [Pay upfront (@sec-a-pay-upfront) up to `PaymentBusinessPort.lockForSettlement` (`GroupDealSettlementSupport.lockTeam`, `PurchaseIntentRepository.lockById`)], [@fig-a-uc03-ds-fulfil],
  [B9], [`isPayable`, `isActive`, `isPurchasable`], [`OrderFulfilmentAdapter.fulfil` → `lockTeam`, `lockById`; `UserAppService.requireActive`; `CatalogPurchaseSupport.requirePurchasable`], [@fig-a-uc03-ds-fulfil],
  [B10], [`acceptsPayment`, «create» `Team`, «create» `Membership`], [`admit` → `startConfirmed` → `reserveAndLock`, `currentTime`; `PaidGroupDealCreator.initiate` → `requirePrice`, `createGroupDeal` → «create» `GroupDeal.startPaid`; `insertStartedDeal` (`insertDeal`, `insertMember`); `complete`], [@fig-a-uc03-ds-fulfil, @fig-a-uc03-ds-create],
  [B11], [«create» `Order`, `markFulfilled`, `takeOneUnit`], [«create» `Order.create`; `OrderRepository.save`; `GroupDealSettlementSupport.linkOrder`; `PurchaseIntentRepository.markFulfilled`; `CatalogPurchaseSupport.consumeOneUnit`], [@fig-a-uc03-ds-fulfil],
  [B12], [(note) outcome recorded], [`recordOutcome` in Pay upfront (@sec-a-pay-upfront)], [—],
  [B13], [`showStarted`], [Receipt (@sec-a-pay-upfront); UC8 (@sec-a-uc08); UC7 (@sec-a-uc07)], [—],
  [A1], [—], [`start` without `quoteId` → `lockStartQuote` → `findLatestPolicy`, `TeamPricePolicy.startableAt`], [@fig-a-uc03-ds-intent],
  [A2], [`showRejected`], [`TeamPricePolicy.requireQuotable` throws `TEAM_PRICE_UNAVAILABLE`; `PRODUCT_NOT_FOUND` from `issueQuote`; the transaction rolls back], [@fig-a-uc03-ds-quote],
  [A3], [`showRejected`], [`lockQuotedStart` or `lockStartQuote` throws `PRODUCT_NOT_FOUND`, `PRODUCT_NOT_PURCHASABLE`, `PRODUCT_NOT_STARTABLE`, `TEAM_PRICE_QUOTE_NOT_FOUND`, `TEAM_PRICE_QUOTE_EXPIRED` or `TEAM_PRICE_UNAVAILABLE`; the intent transaction rolls back], [@fig-a-uc03-ds-intent],
  [A4], [—], [Pay upfront (@sec-a-pay-upfront); the intent becomes `Lapsed`, then `Expired`], [@fig-a-uc03-st-intent],
  [A5], [`requireRefund`, «create» `Refund`, `recordOutcome`], [`BUSINESS_EXPIRED` (@sec-a-pay-upfront), or `fulfil` returns `INTENT_EXPIRED`, `BUYER_INACTIVE`, `PRODUCT_UNAVAILABLE` → `requireRefund`, `recordOutcome`], [@fig-a-uc03-ds-fulfil, @fig-a-uc03-st-intent],
  [A6], [`acceptsPayment` refused; `requireRefund`], [`PaidGroupDealCreator.initiate` throws `INVALID_PARTICIPATION` or `PAYMENT_MISMATCH` → `complete` with `compensation_required` → `Outcome` with `"GROUP_REJECTED"` → `requireRefund`], [@fig-a-uc03-ds-create, @fig-a-uc03-ds-fulfil],
  [A7], [—], [Event claim (@sec-a-pay-upfront); `fulfil` replay or `INTENT_ALREADY_FULFILLED`; completed receipt from `reserveAndLock`, or `PAYMENT_REUSED`], [@fig-a-uc03-ds-fulfil, @fig-a-uc03-ds-create],
  [E1], [—], [`consumeOneUnit` false → `IllegalStateException`, or `IdentityUnavailableException`: rollback, Stripe redelivers], [@fig-a-uc03-ds-fulfil],
  [E2], [—], [`RemoteUserAppService.requireActive` throws `IdentityUnavailableException`; the quote or intent transaction rolls back], [@fig-a-uc03-ds-quote, @fig-a-uc03-ds-intent],
)
