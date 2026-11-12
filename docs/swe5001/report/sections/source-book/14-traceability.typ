#import "../../figures.typ": *

// Number-only references ("6.12", "5") for dense trace tables.
#let rr(..ls) = ls.pos().map(l => ref(l, supplement: none)).join[, ]
#let yes = sym.checkmark
#let no(why) = [– #text(fill: muted, why)]
#let via(p) = [#sym.checkmark#text(fill: muted)[ via #p]]
// Code in these dense tables prints at 8 pt (the report default, 0.88 em of the cell text, would fall below it).
#show table: it => { show raw: set text(size: 8pt); it }

#page(flipped: true)[

== Cross-Use-Case Traceability <sec-a-trace>

The tables below prove two things. First, every use case is realised by named design elements in one of BlocBuy's three services: each analysis control lands on an entry point (controller, job or event subscriber), application services, aggregates and tables, under named strategies. Second, a class shared by several parts is the same class everywhere, with one stereotype and one responsibility. Use @tbl-a-trace-uc to go from a use case to its code; @tbl-a-trace-strategy to see which strategies shaped it; @tbl-a-trace-shared to find every figure that draws a shared class; @tbl-a-trace-life to find who owns a lifecycle and who moves it; and @tbl-a-trace-ad to go from an architecture decision (report §3.1.1, §3.2.1 and §3.3) to the parts that realise it. Each row points to the part that holds the detail, whose mapping table (for example @tbl-a-uc04-map) names every class.

#[
#show table: set text(8pt)
#atable(
  [Use case realisation matrix (traceability matrix, analysis → design). Read a row left to right: the analysis control, the design element that receives the trigger, the application services, aggregates and tables it uses, the events it raises (→ their subscribers), the strategies applied, the owning service and the bounded contexts whose aggregates the row uses (identity's HTTP check not counted), and the part with the detail. "Settled" means after the Stripe webhook of part 12.],
  <tbl-a-trace-uc>,
  columns: (auto, 1fr, 1.9fr, 1.55fr, 1.3fr, 1.55fr, 1.45fr, 0.6fr, 0.95fr),
  header: ([UC], [Analysis control], [Entry point and route], [Application services], [Aggregates and records], [Tables], [Events raised → consumed], [Strate\-gies], [Service, contexts, part]),
  [UC1], [`BrowseListingsControl`], [`ProductController`: `GET /api/v1/products` (also search), `GET …/products/{id}`], [`ProductAppService`, `GroupDealQueryService`], [`Product`; `GroupDeal` read only], [`biz_product`, `biz_group_deal`; Valkey cache], [Consumes `MemberJoined`, `TeamSucceeded`, `TeamExpired` (cache eviction)], [S1–S6, S9], [commerce (catalog, groupbuy), #rr(<sec-a-uc01>)],
  [UC2], [`SignInWithGoogleControl`], [`AuthController`: `GET /api/v1/auth/oauth2/google` and `…/callback`, `GET …/me`, `POST …/logout`, with the web-layer helper `LoginSessionPublisher` («utility») for the session phase; `SaInterceptor` everywhere], [`OAuthLoginAppService`, `OAuthUserTxService`, `LoginAuthorizationService`], [`User` and `OAuthIdentity` (both «aggregate root»); S6 records: OAuth state, session with role snapshot], [`sys_user`, `sys_user_oauth`, `rel_user_role`; Valkey], [None], [S1–S6, S8], [identity (identity), #rr(<sec-a-uc02>)],
  [UC3], [`StartTeamControl`], [`TeamPriceController`: `POST /api/v1/team-price-quotes`; `StripeCheckoutController`: `POST /api/v1/payments/intents`; settled: `OrderFulfilmentAdapter.fulfil`; lapsed intents: `PurchaseIntentExpiryJob` (every minute)], [`TeamPriceAppService`, `PaymentBrowserService`, `PurchaseIntentAppService`, `PaidGroupDealService`], [`Product`, `TeamPricePolicy` (entity `TeamPriceQuote`), `PurchaseIntent`, `GroupDeal`, `Order`; settlement: five with `CheckoutPayment` (S8)], [`biz_team_price_policy`, `biz_team_price_quote`, `biz_purchase_intent`, `biz_group_deal`, `biz_group_member`, `biz_order`, `biz_payment`, `biz_product`], [None from group-buy; settlement raises `PaymentSucceeded`], [S1–S8], [commerce (catalog, order, groupbuy, payment), #rr(<sec-a-uc03>)],
  [UC4], [`JoinTeamControl`], [`StripeCheckoutController`: `POST /api/v1/payments/intents`; settled: `OrderFulfilmentAdapter.fulfil`], [`PaymentBrowserService`, `PurchaseIntentAppService`, `PaidGroupDealService`], [`GroupDeal`, `PurchaseIntent`, `Order`; settlement: five with `CheckoutPayment` and `Product` (S8); `ShareInvite` read], [`biz_group_deal`, `biz_group_member`, `biz_purchase_intent`, `biz_order`, `biz_payment`, `biz_product`, `biz_refund`, `sys_outbox`], [`MemberJoined`, `TeamSucceeded` → four subscribers (#rr(<fig-a-uc04-dc-events>))], [S1–S6, S8, S9], [commerce (groupbuy, order, payment, catalog), #rr(<sec-a-uc04>)],
  [UC5], [`ResolveTeamControl`], [`TeamExpirySweepJob.run` (every minute); success in `PaidGroupDealService.joinConfirmed`], [`GroupDealExpirySweeper`, `GroupDealExpiryService`, `PaidGroupDealService`], [`GroupDeal`, `Order`], [`biz_group_deal`, `biz_order`, `sys_outbox`, `sys_scheduler_lock`], [`TeamSucceeded`, `TeamExpired` → orders, refunds, outbox, cache], [S1, S4, S7–S9], [commerce (groupbuy, order), #rr(<sec-a-uc05>)],
  [UC6], [`RefundMembersControl`], [`TeamExpiryRefundSubscriber.onTeamExpired`; `RefundWorkerJob`; `RefundService.reconcile` (no caller)], [`TeamExpiryRefundService`, `RefundService`], [`CheckoutPayment`; `GroupDeal` locked; refund records (claim snapshot `RefundWork`; not a root)], [`biz_refund`, `log_refund_attempt`, `biz_payment`], [Consumes `TeamExpired`], [S1–S9], [commerce (payment, groupbuy), #rr(<sec-a-uc06>)],
  [UC7], [`ShareInvitationControl`, `ReferralCreditControl`], [`GroupDealController`: `POST /api/v1/group-deals/{dealId}/invites`; `InviteController`: `GET /api/v1/invites/{ref}`; `ShareCreditSubscriber`], [`ShareInviteAppService`; attribution in `GroupDealPurchaseSupport`], [`ShareInvite`, `ShareCredit`], [`biz_share_invite`, `biz_share_credit`, `biz_group_member`, `biz_purchase_intent`], [Consumes `MemberJoined`], [S1–S4, S6, S8, S9], [commerce (groupbuy), #rr(<sec-a-uc07>)],
  [UC8], [`ViewMyTeamsControl`, `ViewMyOrdersControl`], [`GroupDealController`: `GET /api/v1/group-deals/me`; `OrderController`: `GET /api/v1/orders/me`, `GET …/orders/{orderId}`], [`GroupDealAppService`, `OrderAppService`], [`Order`; `TeamMembership`, `ProductDisplay`], [`biz_group_member`, `biz_group_deal`, `biz_product`, `biz_order`], [None], [S1–S4, S6, S8], [commerce (groupbuy, order, catalog), #rr(<sec-a-uc08>)],
  [UC9], [`AdministerListingControl`], [`AdminListingController`: `POST`, `PUT`, `DELETE` on `/api/v1/admin/products` and its `/status`, `/team-terms`; `TeamPriceController`: `PUT /api/v1/products/{productId}/team-price-policy` (A5, its own transaction, #rr(<fig-a-uc09-ds-price>))], [`AdminListingAppService`, `TeamTermsAppService`, `TeamPriceAppService`], [`Product`, `TeamPricePolicy`; `TeamTermsChange`], [`biz_product`, `log_product_team_terms`, `biz_team_price_policy`], [None], [S1–S4, S6, S8], [commerce (catalog), #rr(<sec-a-uc09>)],
  [UC10], [`PayUpfrontControl`, `BaseUseCaseControl` (the abstract role that `StartTeamControl` and `JoinTeamControl` realise)], [`StripeCheckoutController`: `POST /api/v1/payments/checkout-sessions`, `GET …/payments/{id}`; `StripeWebhookController`: `POST /api/v1/webhooks/stripe`], [`PaymentBrowserService`, `StripeCheckoutService`; port `PaymentBusinessPort`], [`CheckoutPayment` (value object `CheckoutQuote`; review case, read model `PaymentReviewCase`); refund records], [`biz_payment`, `log_payment_webhook_event`, `biz_payment_review_case`, `biz_refund`], [`PaymentSucceeded` → `IntegrationOutboxWriter`], [S1–S6, S8, S9], [commerce (payment, order), #rr(<sec-a-pay-upfront>)],
  [UC11], [`ReceiveNotificationsControl` (the relay is S9 design, with no analysis control)], [`IntegrationOutboxWriter`; `OutboxRelayJob.run` (every 2 s); `SqsNotificationEventConsumer`; `NotificationController`: `GET /api/v1/notifications`], [`OutboxRelayService`, `NotificationEventAppService`, `NotificationAppService`], [`Notification`; `OutboxEvent`], [commerce `sys_outbox`; notification `sys_consumed_message`, `biz_notification`], [Consumes the three integration-bound events; sends their `V1` contracts over SNS → SQS], [S1–S9], [commerce and notification (notification), #rr(<sec-a-notifications>)],
)
]
]

=== Strategies Across Use Cases <sec-a-trace-strategy>

@tbl-a-trace-strategy crosses the nine strategies of @sec-a-strategies with UC1 to UC11 (UC12, the sharer's credit, is designed with UC7). It is the use-case view of the coverage matrix (@tbl-a-coverage), which crosses the strategies with analysis object types. S1 and S4 change every use case; S7 changes only the four that run on a timer. Each column equals the "Impacted use cases" row of that strategy in @sec-a-strategies.

#atable(
  [Strategy × use case matrix. #sym.checkmark: the strategy changes the use case's design (its line in the part's transition section); "via A.n": applied, but designed in the included part; –: not applied, with the reason.],
  <tbl-a-trace-strategy>,
  columns: (auto, 1fr, 1fr, 1fr, 1fr, 1fr, 1fr, 1fr, 1fr, 1fr),
  align: (left + top, center + top, center + top, center + top, center + top, center + top, center + top, center + top, center + top, center + top),
  header: ([Use case], [S1 Lang], [S2 Pres], [S3 Dist], [S4 Pers], [S5 Ext], [S6 Sec], [S7 Sched], [S8 Conc], [S9 Evt]),
  [UC1 Browse], yes, yes, yes, yes, yes, yes, no[no timer], no[read only], yes,
  [UC2 Sign in], yes, yes, yes, yes, yes, yes, no[expiry by TTL], yes, no[no event],
  [UC3 Start team], yes, yes, yes, yes, via[part 12], yes, yes, yes, no[no group event],
  [UC4 Join team], yes, yes, yes, yes, via[part 12], yes, no[no timer], yes, yes,
  [UC5 Resolve team], yes, no[no screen], no[no remote call], yes, no[no provider], no[no request], yes, yes, yes,
  [UC6 Refund], yes, via[part 12], yes, yes, yes, yes, yes, yes, yes,
  [UC7 Share], yes, yes, yes, yes, no[no provider], yes, no[manual query], yes, yes,
  [UC8 View mine], yes, yes, yes, yes, no[no provider], yes, no[no timer], yes, no[no event],
  [UC9 Administer], yes, yes, yes, yes, no[no provider], yes, no[no timer], yes, no[no event],
  [UC10 Pay upfront], yes, yes, yes, yes, yes, yes, no[no timed step], yes, yes,
  [UC11 Notify], yes, yes, yes, yes, yes, yes, yes, yes, yes,
  [*Applied*], [*11*], [*10*], [*10*], [*11*], [*7*], [*10*], [*4*], [*10*], [*7*],
)

=== Shared Design Elements <sec-a-trace-shared>

A class drawn in more than one part is one class: the same name, role stereotype, member signatures and responsibility wherever it appears; only its strategy tags may differ between slices (@tbl-a-stereotypes). A slice shows only the members it uses (template rule 17), so a class may show fewer members in one part than in another, never different ones. @tbl-a-trace-shared lists each shared class with the figures that draw it; its stereotype is the one that part 1 (@tbl-a-stereotypes) gives its role. The aggregate roots are the 11 of report §3.1.4: `User`, `OAuthIdentity`, `Product`, `TeamPricePolicy`, `GroupDeal`, `ShareInvite`, `ShareCredit`, `PurchaseIntent`, `Order`, `CheckoutPayment` and `Notification`. `TeamPriceQuote` is an entity of `TeamPricePolicy`; `RefundWork` is a claim snapshot of a refund record, and `PaymentReviewCase` is a read model inside `CheckoutPayment`; none of the three is a root.

#[
#show table: set text(8pt)
#atable(
  [Shared design element index. Figures are numbered A.n; the first figure listed is in the part that uses the class most. All classes are in commerce-svc unless stated.],
  <tbl-a-trace-shared>,
  columns: (1.3fr, 1fr, 1.45fr, 2fr),
  header: ([Class], [Stereotype], [Drawn in figures], [One responsibility]),
  [`PaymentBusinessPort`], [«interface»], [#rr(<fig-a-pay-dc-settle>, <fig-a-pay-dc-prepare>, <fig-a-uc03-dc-fulfil>, <fig-a-uc04-dc-fulfil>, <fig-a-uc06-dc-record>)], [The payment module's port to the business side: lock the quote, lock for settlement, fulfil a paid event, lock an expired team.],
  [`PaymentBusinessPort.Outcome`], [«value object, record»], [#rr(<fig-a-pay-dc-settle>, <fig-a-uc03-dc-fulfil>, <fig-a-uc04-dc-fulfil>)], [The result of `fulfil`: the team and order, or a rejection reason.],
  [`OrderFulfilmentAdapter`], [«adapter, application service»], [#rr(<fig-a-uc04-dc-fulfil>, <fig-a-uc03-dc-fulfil>, <fig-a-pay-dc-settle>, <fig-a-uc05-dc-resolve>, <fig-a-uc06-dc-record>, <fig-a-uc07-dc-credit>)], [Implements `PaymentBusinessPort` in the order module: takes the locks in the fixed order, starts or joins the team and creates the order.],
  [`PaymentIntentPort`], [«interface»], [#rr(<fig-a-uc04-dc-intent>, <fig-a-uc03-dc-intent>, <fig-a-pay-dc-receipt>, <fig-a-str-s3-after>)], [The payment module's port to create a purchase intent and to read the product it bought.],
  [`PurchaseIntentAppService`], [«adapter, application service»], [#rr(<fig-a-uc04-dc-intent>, <fig-a-uc03-dc-intent>, <fig-a-str-s3-after>)], [Implements `PaymentIntentPort`: creates a START or JOIN intent at a server-fixed price.],
  [`PaymentBrowserService`], [«application service»], [#rr(<fig-a-pay-dc-prepare>, <fig-a-pay-dc-receipt>, <fig-a-uc03-dc-intent>, <fig-a-uc04-dc-intent>, <fig-a-str-s3-after>)], [The buyer's payment requests: create an intent, prepare a checkout, read a receipt.],
  [`StripeCheckoutService`], [«application service»], [#rr(<fig-a-pay-dc-settle>, <fig-a-pay-dc-gateway>, <fig-a-pay-dc-prepare>, <fig-a-uc04-dc-events>)], [Prepares a Checkout session, verifies a webhook and settles a payment in its own transactions, with every provider call outside them; publishes `PaymentSucceeded`.],
  [`StripeCheckoutController`], [«controller»], [#rr(<fig-a-pay-dc-prepare>, <fig-a-pay-dc-receipt>, <fig-a-uc03-dc-intent>, <fig-a-uc04-dc-intent>, <fig-a-str-s3-after>)], [REST adapter for `/api/v1/payments`, with the request-marker check.],
  [`PaidGroupDealService`], [«application service»], [#rr(<fig-a-uc04-dc-admit>, <fig-a-uc04-dc-fulfil>, <fig-a-uc04-dc-events>, <fig-a-uc03-dc-create>, <fig-a-uc03-dc-fulfil>, <fig-a-uc05-dc-resolve>, <fig-a-uc07-dc-credit>)], [Admits one paid participation (start or join) under the team lock and publishes `MemberJoined` and `TeamSucceeded`.],
  [`GroupDeal`], [«aggregate root»], [#rr(<fig-a-uc04-dc-admit>, <fig-a-uc03-dc-create>, <fig-a-uc05-dc-resolve>, <fig-a-uc06-dc-record>); lifecycle #rr(<fig-a-uc05-st-deal>)], [The team: seats, price, deadline and `DealStatus`, with the admission and expiry rules.],
  [`PaidGroupDealRepository`], [«interface, repository»], [#rr(<fig-a-uc04-dc-admit>, <fig-a-uc04-dc-intent>, <fig-a-uc03-dc-create>, <fig-a-uc05-dc-resolve>, <fig-a-uc05-dc-sweep>, <fig-a-uc06-dc-record>, <fig-a-uc07-dc-invite>, <fig-a-uc07-dc-credit>)], [Locks and writes a team, its members and its participation receipts (the representative chain, with `PaidGroupDealRepositoryImpl` and `PaidGroupDealMapper`).],
  [`GroupDealSettlementSupport`], [«interface»], [#rr(<fig-a-uc04-dc-fulfil>, <fig-a-uc03-dc-fulfil>, <fig-a-uc06-dc-record>)], [Group-buy's module interface to settlement: lock a team, link an order, lock an expired team.],
  [`CatalogPurchaseSupport`], [«interface»], [#rr(<fig-a-uc04-dc-intent>, <fig-a-uc04-dc-fulfil>, <fig-a-uc03-dc-intent>, <fig-a-uc03-dc-fulfil>)], [Catalog's module interface to purchase: a purchasable product, a locked start quote, one unit of stock.],
  [`GroupDealQueryService`], [«interface»], [#rr(<fig-a-uc01-dc-read>, <fig-a-uc01-dc-price>, <fig-a-uc09-dc-listing>)], [Group-buy's read-only queries for catalog: best open offer per product, open-team count, product references.],
  [`UserAppService`], [«interface»], [#rr(<fig-a-uc02-dc-access>, <fig-a-uc03-dc-quote>, <fig-a-uc03-dc-intent>, <fig-a-uc04-dc-intent>, <fig-a-uc07-dc-invite>, <fig-a-uc09-dc-listing>, <fig-a-str-s3-after>) and the fulfil and terms slices], [The identity port: current user, active-account check, public nickname. identity-svc implements it locally.],
  [`RemoteUserAppService`], [«adapter»], [#rr(<fig-a-str-s3-after>, <fig-a-uc04-dc-intent>, <fig-a-uc07-dc-invite>, <fig-a-uc09-dc-listing>)], [commerce-svc's HTTP client for `UserAppService`: one call to identity-svc that fails closed (AD-06).],
  [`ApplicationEventPublisher`], [«interface»], [#rr(<fig-a-uc04-dc-events>, <fig-a-uc05-dc-resolve>, <fig-a-uc05-dc-events>, <fig-a-uc06-dc-record>, <fig-a-uc07-dc-credit>, <fig-a-pay-dc-settle>)], [Spring's in-process publisher: raises a domain event inside the producing transaction.],
  [`MemberJoined`, `TeamSucceeded`, `TeamExpired`, `PaymentSucceeded`], [«domain event, record»], [#rr(<fig-a-uc04-dc-events>, <fig-a-uc05-dc-events>, <fig-a-uc05-dc-resolve>, <fig-a-uc06-dc-record>, <fig-a-uc07-dc-credit>, <fig-a-pay-dc-settle>)], [In-process facts: a member joined, a team filled, a team expired, a payment settled.],
  [`TeamSucceededV1`, `TeamExpiredV1`], [«integration event, record»], [#rr(<fig-a-ntf-dc-produce>)], [Versioned cross-service contracts in `blocbuy-client`, carried in `IntegrationEventEnvelope`.],
  [`IntegrationOutboxWriter`], [«subscriber»], [#rr(<fig-a-ntf-dc-produce>) in full; one element in #rr(<fig-a-uc04-dc-events>, <fig-a-uc05-dc-events>)], [Before commit, at lowest precedence, writes one `sys_outbox` row for `TeamSucceeded`, `TeamExpired` or `PaymentSucceeded`.],
  [`OrderLifecycleSubscriber`], [«subscriber»], [#rr(<fig-a-uc05-dc-events>, <fig-a-uc04-dc-events>)], [Before commit (`@Order(10)`), confirms or cancels a team's pending orders.],
  [`TeamExpiryRefundSubscriber`], [«subscriber»], [#rr(<fig-a-uc06-dc-record>, <fig-a-uc05-dc-events>)], [Before commit (`@Order(20)`), records a refund obligation per paid member of an expired team.],
  [`ShareCreditSubscriber`], [«subscriber»], [#rr(<fig-a-uc07-dc-credit>, <fig-a-uc04-dc-events>)], [After commit, in its own transaction, credits the sharer of an attributed join.],
  [`OpenDealOfferCacheSubscriber`], [«subscriber»], [#rr(<fig-a-uc04-dc-events>, <fig-a-uc05-dc-events>)], [After commit, evicts the product's cached open offer.],
  [`GroupDealExpiryService`], [«application service»], [#rr(<fig-a-uc05-dc-resolve>, <fig-a-uc05-dc-sweep>, <fig-a-uc06-dc-record>)], [Expires one due team in one transaction and publishes `TeamExpired`.],
  [`LeasedJobRunner`], [«utility»], [#rr(<fig-a-uc05-dc-sweep>, <fig-a-uc06-dc-worker>, <fig-a-ntf-dc-relay>)], [Runs a job's work only while this task holds the job's database lease, and renews it.],
  [`SchedulerLeaseRepository`], [«interface, repository»], [#rr(<fig-a-uc05-dc-sweep>, <fig-a-uc06-dc-worker>)], [Acquires, renews and releases a lease row in `sys_scheduler_lock`.],
  [`CheckoutPaymentRepository`], [«interface, repository»], [#rr(<fig-a-pay-dc-settle>, <fig-a-pay-dc-prepare>, <fig-a-pay-dc-receipt>, <fig-a-uc06-dc-record>)], [Locks and writes payments and their webhook records, review cases and refund obligations.],
  [`RefundRepository`], [«interface, repository»], [#rr(<fig-a-uc06-dc-worker>, <fig-a-uc06-dc-provider>, <fig-a-pay-dc-receipt>)], [Claims, finishes and reports `biz_refund` rows.],
  [`OrderRepository`], [«interface, repository»], [#rr(<fig-a-uc04-dc-fulfil>, <fig-a-uc03-dc-fulfil>, <fig-a-uc04-dc-events>, <fig-a-uc05-dc-events>, <fig-a-uc08-dc-orders>)], [Saves an order, confirms or cancels a team's orders in bulk, pages a buyer's orders.],
  [`PurchaseIntentRepository`], [«interface, repository»], [#rr(<fig-a-uc03-dc-intent>, <fig-a-uc03-dc-fulfil>, <fig-a-uc04-dc-intent>, <fig-a-uc04-dc-fulfil>)], [Saves, locks and marks fulfilled a purchase intent.],
  [`ProductRepository`], [«interface, repository»], [#rr(<fig-a-uc09-dc-product>, <fig-a-uc09-dc-listing>, <fig-a-uc09-dc-terms>, <fig-a-uc01-dc-read>, <fig-a-uc03-dc-quote>, <fig-a-uc08-dc-teams>)], [Reads, locks and version-checks listings; the representative catalog chain.],
  [`GroupDealRepository`], [«interface, repository»], [#rr(<fig-a-uc01-dc-price>, <fig-a-uc07-dc-invite>, <fig-a-uc08-dc-teams>)], [Read-side team queries: by id, best open teams, a buyer's memberships.],
  [`PricingStrategy`, `DualPricingStrategy`], [«interface, domain service», «domain service»], [#rr(<fig-a-uc01-dc-price>, <fig-a-uc03-dc-quote>)], [Computes the listing price and the team price from product and policy.],
  [`PricingStrategyResolver`], [«utility»], [#rr(<fig-a-uc01-dc-price>, <fig-a-uc03-dc-quote>)], [Picks the `PricingStrategy` for a product's pricing mode from an index built at start-up; holds no state of its own.],
  [`TeamPriceAppService`], [«interface, application service»], [#rr(<fig-a-uc03-dc-quote>, <fig-a-uc09-dc-terms>); called in #rr(<fig-a-uc09-ds-price>)], [Issues team-price quotes and configures the price policy.],
  [`TeamPricePolicy`], [«aggregate root, record»], [#rr(<fig-a-uc09-dc-terms>, <fig-a-uc03-dc-quote>)], [A product's versioned team-price policy.],
  [`ShareInvite`], [«aggregate root, record»], [#rr(<fig-a-uc07-dc-invite>, <fig-a-uc04-dc-intent>)], [One sharer's invitation reference for one team.],
  [`RateLimitGateway`], [«interface, gateway»], [#rr(<fig-a-uc04-dc-intent>, <fig-a-uc07-dc-invite>, <fig-a-pay-dc-prepare>)], [A per-subject request budget in Valkey.],
  [`StpInterface`, `SessionStpInterface`, `LoginSnapshotGateway`], [«interface», «adapter», «interface, gateway»], [#rr(<fig-a-uc02-dc-access>, <fig-a-uc02-dc-session>, <fig-a-str-s6-rbac>)], [The session's roles: written at sign-in, read from Valkey by commerce-svc and notification-svc, from its database by identity-svc.],
  [`LoginSessionPublisher`], [«utility»], [#rr(<fig-a-uc02-dc-session>, <fig-a-str-s6-rbac>)], [identity-svc's web-layer helper that opens the shared session at sign-in: publishes the role snapshot, then sets the token cookie.],
)
]

=== Lifecycle Owners <sec-a-trace-life>

Each lifecycle is drawn once, in the part that owns the entity (template rule 23). @tbl-a-trace-life names the parts whose steps move it. A part that only reads a state draws no statechart and names the owner.

#atable(
  [Lifecycle owners. Each statechart, the part that owns it, and the parts that cause or only read its transitions.],
  <tbl-a-trace-life>,
  columns: (1fr, 1.05fr, 2.4fr, 1fr),
  header: ([Lifecycle (state type)], [Statechart, owner], [Parts that cause transitions (transition)], [Parts that only read it]),
  [Purchase intent], [#rr(<fig-a-uc03-st-intent>), UC3 (#rr(<sec-a-uc03>))], [UC3, UC4: issue; Pay upfront through `OrderFulfilmentAdapter`: mark fulfilled, or refund a payment on a lapsed or expired intent; the intent sweep (part 2, S7): expire], [Pay upfront (the frozen `CheckoutQuote`)],
  [Team (`DealStatus`)], [#rr(<fig-a-uc05-st-deal>), UC5 (#rr(<sec-a-uc05>))], [UC3: start (→ OPEN); UC4: join, and fill (→ SUCCEEDED); a late payment on a closed team (refund owed); UC5: expiry tick (→ EXPIRED)], [UC1 (open offers), UC7, UC8, UC9, Notifications],
  [Order (`OrderStatus`)], [#rr(<fig-a-uc05-st-order>), UC5 (#rr(<sec-a-uc05>))], [UC3, UC4: create PENDING in `OrderFulfilmentAdapter`; UC5: confirm or cancel in `OrderLifecycleSubscriber`], [UC8],
  [Checkout payment (`CheckoutPayment.Status`)], [#rr(<fig-a-pay-st-payment>), Pay upfront (#rr(<sec-a-pay-upfront>))], [Pay upfront: prepare, bind, settle, fail, cancel, recover (the buyer acts from UC3 or UC4; Stripe reports through the webhook)], [UC6 (a team's succeeded payments)],
  [Refund obligation (`biz_refund.status`)], [#rr(<fig-a-uc06-st-refund>), UC6 (#rr(<sec-a-uc06>))], [UC5 expiry through `TeamExpiryRefundSubscriber`: create pending; Pay upfront: create for a rejected or late payment (`requireRefund`); UC6: process, finish, reconcile], [Pay upfront (receipt)],
  [Listing (`ProductStatus`)], [#rr(<fig-a-uc09-st-listing>), UC9 (#rr(<sec-a-uc09>))], [UC9: create, publish, delist, revise, configure team terms, delete], [UC1 (ON_SALE only); UC3, UC4 (stock, not status)],
  [Outbox event (`sys_outbox` row)], [#rr(<fig-a-ntf-st-outbox>), Notifications (#rr(<sec-a-notifications>))], [UC4, UC5, Pay upfront: record, through their domain events; Notifications: relay, retry, fail, replay, retention], [—],
)

=== Decisions in the Source Book <sec-a-trace-ad>

@tbl-a-trace-ad starts from the architecture decision register (report §3.1.1, §3.2.1 and §3.3) and shows where the source book realises each decision. Ten of the twenty decisions shape the design model. The other ten govern deployment, scaling and delivery and have no design class. S5 rests on backend ADRs rather than a chapter-3 decision, so it has its own row.

#[
#show table: set text(8pt)
#atable(
  [AD → source book index. Each architecture decision, the strategy that carries it (@tbl-a-strategies), the design elements and parts that realise it, and the NFRs it serves through that strategy.],
  <tbl-a-trace-ad>,
  columns: (1.7cm, 1.3fr, auto, 2.5fr, 0.85fr),
  header: ([AD], [Decision (short)], [Strategy], [Realised in (design elements, parts)], [NFRs]),
  [AD-01], [Strangler migration; monolith kept as fallback], [S1], [One codebase whose classes run in either topology; wiring in `PaymentIntegrationConfiguration`. The S1 line of parts 3–8 and 11–13.], [05, 06, 26],
  [AD-02], [Split where no transaction crosses], [S3, S8], [Payment and order stay in commerce-svc, so settlement is one local transaction behind `PaymentBusinessPort` → `OrderFulfilmentAdapter` (parts 5, 6, 8, 12); notification-svc shares no transaction (part 13).], [05, 07, 13, 27, 64, 66],
  [AD-03], [One database per service], [S4, S8], [Each table has one owning service: identity (part 4), commerce (parts 3, 5–12), notification (part 13). Several aggregates share one local transaction under the S8 justification (parts 4–8, 11, 12).], [05, 13, 21, 30],
  [AD-04], [Outbox → SNS → SQS, idempotent consumers], [S9], [`IntegrationOutboxWriter`, `OutboxRelayService`, `SqsNotificationEventConsumer`, `ConsumedMessageRepository` (part 13); producers in parts 6, 7, 12.], [30, 39, 59–61, 63],
  [AD-05], [Shared session in Valkey, roles inside], [S6], [`LoginSessionPublisher`, `RedisLoginSnapshotGateway`, `SessionStpInterface` (part 4, #rr(<fig-a-str-s6-rbac>)); the buyer's id from the session in parts 5, 6 and 9–13.], [01, 46, 47, 58, 68],
  [AD-06], [One synchronous call, commerce → identity, fail closed], [S3; S5 for the webhook], [`UserAppService` → `RemoteUserAppService` (parts 5, 6, 9, 11, 12); the webhook's settlement depends on it at run time (part 12).], [66],
  [Backend ADRs 0001, 0004, 0005], [Provider isolation; no chapter-3 AD], [S5], [One port and one adapter per external system: `OAuthIdentityGateway` (part 4, fails closed), `ListingSearchReranker` (part 3, fails open to the database order), `CheckoutGateway` (part 12), `RefundGateway` (part 8), `OutboxEventTransport` (part 13).], [21–23, 31, 50, 66],
  [AD-09], [CloudFront → restricted ALB], [S2, S3, S6], [Same-origin `/api/v1` routes of every controller in @tbl-a-trace-uc; ALB path routing of `/api/v1/notifications` to notification-svc (part 13).], [07, 12, 16, 25],
  [AD-11], [One RDS server, three databases], [S4], [The identity, commerce and notification databases of parts 4, 5–12 and 13.], [05, 13],
  [AD-16], [Jobs stay with their data, under a lease], [S7], [`LeasedJobRunner`, `SchedulerLeaseRepository`; `PurchaseIntentExpiryJob` (part 5), `TeamExpirySweepJob` (part 7), `RefundWorkerJob` (part 8), `OutboxRelayJob` (part 13).], [15, 32, 61],
  [AD-18], [COLA layers enforced by ArchUnit], [S1], [The layer of every design class (controller, application service, domain, infrastructure) under the mapping rule (#rr(<sec-a-mapping-rule>)); every mapping table.], [05, 06, 26],
  [AD-07, 08, 10, 12–15, 17, 19, 20], [Compute, scaling, network, account, pipeline and release], [—], [No design class; see report §3.2.1 and §3.3.], [—],
)
]
