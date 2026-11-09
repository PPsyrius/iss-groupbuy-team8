#import "lib-a.typ": *

== Transition Strategies <sec-a-strategies>

A transition strategy deals with one design concern, usually one or more non-functional requirements, and turns every analysis object it touches into design objects in the same way (SAD 04a, pages 35–37). Nine strategies take BlocBuy from the platform-free analysis model of each part to the code of the three services. Each use-case part applies them and cites them by number. A designer who adds a use case applies the same nine, so every use case is designed alike.

The strategies take the architecture of chapter 3 as given: one Maven reactor of shared COLA modules packaged as three services, `identity-svc`, `commerce-svc` and `notification-svc` (AD-01, AD-02, backend ADR 0008); a React single-page application reached through CloudFront and one Application Load Balancer (ALB); one PostgreSQL server with a database per service (AD-03, AD-11); one Valkey node for sessions (AD-05); and an SNS topic with SQS queues for events (AD-04). @tbl-a-strategies summarises the nine. Its last column is the citation that every part copies, in the form `Sn Name (AD-nn, NFR-xx)`. NFR numbers refer to the team's NFR register (`docs/quality/nfr-register.md`); AD numbers to the decisions of chapter 3.

#atable(
  [The nine transition strategies: the concern each handles, the requirements and decisions behind it, and the tag that marks its design elements.],
  <tbl-a-strategies>,
  columns: (1fr, 1.4fr, 1.1fr, 0.9fr),
  header: ([Strategy (tag)], [Design concern], [Decisions and records], [Requirements]),
  [S1 Language and framework (Lang)], [One maintainable codebase that deploys as a monolith or as three services], [AD-01, AD-18; backend ADR 0008], [NFR-05, NFR-06, NFR-26],
  [S2 Presentation (Pres)], [Usable screens that stay in step with the API contract], [AD-09; frontend ADRs 0001, 0003, 0009, 0014], [NFR-07, NFR-12, NFR-16, NFR-25],
  [S3 Distribution (Dist)], [Client/server and service partitioning without splitting a transaction], [AD-02, AD-06, AD-09; backend ADRs 0001, 0008], [NFR-05, NFR-07, NFR-64, NFR-66],
  [S4 Persistence (Pers)], [Data integrity and one owner per table], [AD-03, AD-11; backend ADR 0008], [NFR-05, NFR-13],
  [S5 External interface (Ext)], [Isolation from Stripe and Google; money moved exactly once], [AD-06; backend ADRs 0001, 0004, 0005], [NFR-21, NFR-22, NFR-23, NFR-31, NFR-50, NFR-66],
  [S6 Security (Sec)], [Authentication, authorisation and abuse limits in every service], [AD-05, AD-09; backend ADR 0008; frontend ADR 0002], [NFR-01, NFR-46, NFR-47, NFR-58, NFR-68],
  [S7 Scheduling (Sched)], [Timely time-triggered work with one runner across tasks], [AD-16; backend ADR 0002 (addendum), ADR 0007], [NFR-15, NFR-32, NFR-61],
  [S8 Concurrency (Conc)], [No oversold seat, no payment settled or refunded twice], [AD-02, AD-03; backend ADRs 0001, 0002], [NFR-13, NFR-21, NFR-27, NFR-30],
  [S9 Domain and integration events (Evt)], [Consequences decoupled across modules and services, and never lost], [AD-04; backend ADRs 0002, 0003, 0007], [NFR-30, NFR-39, NFR-59, NFR-60, NFR-61, NFR-63],
)

Each strategy below has the four parts of 04a, page 37, in one table: the design concern and its requirements; on the analysis side, the impacted object types, use cases, static structure and dynamic interactions; on the design side, the resultant design objects, static structure and dynamic interactions, each with its justification. A figure follows where a picture says more than the table. The "before" of every strategy is the analysis model of a use case; UC4 (@sec-a-uc04) is the running example because it has an object of every kind except the scheduler.

=== S1 Language and Framework <sec-a-s1>

#atable(
  [S1 Language and framework in the four-part form.],
  <tbl-a-s1>,
  columns: (3.3cm, 1fr),
  header: ([Part], [S1 Language and framework (AD-01, AD-18, NFR-05, NFR-06, NFR-26)]),
  [Design concern], [Maintainability: one codebase that the SWE5006 monolith on `dev` and the three SWE5001 services both run, with layers that cannot erode.],
  [Impacted analysis object types], [Every «control» and «entity». Boundaries are handled by S2, S3 and S5.],
  [Impacted use cases], [All: UC1 to UC9, Pay upfront and Receive notifications.],
  [Impacted static structure], [Each analysis class is one platform-free class with business operations and analysis-typed attributes.],
  [Impacted dynamic interactions], [A control creates and asks entities; how an object comes into being is not analysed.],
  [Resultant design objects], [Java 21 on Spring Boot 4. Shared COLA modules `blocbuy-client`, `-adapter`, `-application`, `-domain`, `-infrastructure`, `-platform` and `-bootstrap`. Three thin assemblies under `backend/services/`, each an entry class with its own component scan and `@MapperScan`: `IdentityServiceApplication`, `CommerceServiceApplication`, `NotificationServiceApplication` (the monolith keeps `BlocBuyApplication`). A «control» becomes Spring-managed application services with constructor injection into `final` fields. An «entity» becomes a framework-free class or record in `blocbuy-domain`. A broken rule becomes a `BizException` carrying an `ErrorCode` constant of its context (`GroupbuyErrorCode.DEAL_FULL`). Immutable values are records (`JoinTerms`, `CheckoutQuote`, `PaidAdmission`); lifecycles are enumerations (`DealStatus`, `OrderStatus`).],
  [Resultant static structure and justification], [The services split by assembly, not by moving code: business code compiles once, and each service scans only the contexts it owns, so "which service" is a packaging choice and the monolith stays a working fallback (AD-01). A domain free of Spring, MyBatis, Jackson and Sa-Token is testable with `new` and test doubles. Eighteen ArchUnit and contract checks (`domain_is_framework_free`, `no_field_injection`, the service-boundary rules) fail the build when a layer or service boundary erodes (AD-18, NFR-05). Only deliberate Java constructs are drawn: interfaces for ports, an abstract class for the Factory Method, records and enumerations; `java.util` and framework types stay out of the boxes (04a, page 43).],
  [Resultant dynamic interactions and justification], [Services exist before a use case starts, so a design sequence begins at a controller or a job, never at a newly created control. Entities are created by static factories that validate the new state (`PurchaseIntent.issue`, `Order.create`), rebuilt by `GroupDeal.reconstitute`, or created through the Factory Method `PaidGroupDealCreator.initiate` (UC3). An aggregate returns a new immutable snapshot instead of changing itself (`GroupDeal.joinPaid` returns `PaidAdmission`). One place enforces each invariant, and the caller holds both the state it read and the state it writes, which the guarded update of S8 needs.],
)

=== S2 Presentation <sec-a-s2>

#atable(
  [S2 Presentation in the four-part form.],
  <tbl-a-s2>,
  columns: (3.3cm, 1fr),
  header: ([Part], [S2 Presentation (AD-09, NFR-07, NFR-12, NFR-16, NFR-25)]),
  [Design concern], [Usability, and screens that cannot drift from the API contract.],
  [Impacted analysis object types], [Every «boundary» used by a human actor, for example `JoinTeamUI`. Controls stay on the server.],
  [Impacted use cases], [UC1–UC4, UC7–UC9, Pay upfront (receipt page) and Receive notifications (notification list). UC5 and UC6 have no screen; their results appear on existing ones.],
  [Impacted static structure], [One boundary class per screen with business operations (`join`, `showJoined`, `showRejected`).],
  [Impacted dynamic interactions], [Actor → boundary → control, in one step.],
  [Resultant design objects], [React 19 single-page application: a TanStack Router route module per screen (`products.$productId.tsx`, component `ProductDetailPage`), feature components and hooks (`useCheckout` in `features/payment/use-checkout.ts`), server state in TanStack Query, and a client generated from `contracts/openapi.json`. `unwrapResult` (`src/api/envelope.ts`) turns an error envelope into an `ApiError`. On the server every JSON answer is a `Result<T>` envelope, and `GlobalExceptionHandler` maps each `BizException` to the HTTP status its `ErrorCode` declares.],
  [Resultant static structure and justification], [The generated client is the client-side stub of the distribution (S3): a renamed field fails the frontend build, not the running page (NFR-07). Screens hold no business rule; prices, seats and deadlines are decided on the server, so a rule has one home. The browser sees one origin and one public contract; it does not know there are three services (backend ADR 0008, AD-09).],
  [Resultant dynamic interactions and justification], [A component raises an event to its route; the route calls a hook; the hook calls a generated mutation over HTTPS to `/api/v1/...`; CloudFront forwards it to the ALB, which picks the service by path (S3). The answer is unwrapped, and an error envelope becomes a message on the page. Every failure takes the same path, so the use-case diagrams show only the business decision (NFR-12).],
)

=== S3 Distribution <sec-a-s3>

#atable(
  [S3 Distribution in the four-part form.],
  <tbl-a-s3>,
  columns: (3.3cm, 1fr),
  header: ([Part], [S3 Distribution (AD-02, AD-06, AD-09, NFR-05, NFR-07, NFR-64, NFR-66)]),
  [Design concern], [Scalability and fault isolation by running three services, without splitting any business transaction; interoperability between browser and services.],
  [Impacted analysis object types], [Every «control» that a «boundary» calls (client/server split). Every «control» that uses an «entity» owned by another bounded context, above all `Account`, which lives in identity.],
  [Impacted use cases], [All use cases with a screen (client/server). UC3, UC4, Pay upfront (settlement), UC7 (public nicknames) and UC9 (the administrator is active) cross from commerce to identity. Receive notifications crosses from commerce to notification by events only (S9).],
  [Impacted static structure], [A control associated directly with entities of several contexts: `JoinTeamControl` with `Account`, `Team`, `Product`, `Payment` and `Order` (@fig-a-uc04-ac).],
  [Impacted dynamic interactions], [A plain message from the control to an entity of another context, such as B2 `Account.isActive` in UC4 (@fig-a-uc04-as-basic-1).],
  [Resultant design objects], [Client/server: a `@Validated` REST controller per entry point (`StripeCheckoutController`) with request and response records. Between services: the port `UserAppService` (`requireActive`, `publicNickname`, `publicNicknames`), implemented locally by `UserAppServiceImpl` in identity-svc and the monolith, and remotely by `RemoteUserAppService` in the commerce-svc assembly (imported through `RemoteIdentityConfiguration`). It calls identity over ECS Service Connect with a 500 ms connect and 1,500 ms read timeout, no retry and no cache. `InternalUserController` (identity, `..web.internal..`) serves `GET /internal/users/{id}/active`, `GET /internal/users/{id}/public-nickname` and `POST /internal/users/public-nicknames` (1 to 100 ids). `IdentityUnavailableException` (domain, not a `BizException`) reports an outage. Inside commerce: ports owned by the caller, `PaymentIntentPort` and `PaymentBusinessPort` (payment), implemented by order (`PurchaseIntentAppService`, `OrderFulfilmentAdapter`, wired in `PaymentIntegrationConfiguration`), and supplier facades `CatalogPurchaseSupport`, `GroupDealPurchaseSupport`, `GroupDealSettlementSupport`.],
  [Resultant static structure and justification], [Services are cut where no transaction crosses (AD-02): catalog, group-buy, order and payment share the settlement transaction (S8) and stay together in commerce-svc; identity and notification share none. The control sees only the interface, so whether identity is local or remote is a wiring choice (Ports and Adapters, @fig-a-str-s3-after). The ArchUnit rule `commerce_reaches_identity_only_through_user_port` keeps it that way; `internal_controllers_are_hidden_and_distributed_only` and `InternalPathsContractTest` keep internal endpoints out of the public contract and out of the monolith. The ALB routes `/api/v1/auth/*` and `/api/v1/users/*` to identity, `/api/v1/notifications*` to notification and the rest of `/api/*` to commerce, and answers 404 for `/internal/*` (section 3.2).],
  [Resultant dynamic interactions and justification], [The analysis message `Account.isActive` becomes `UserAppService.requireActive(userId)` inside the caller's transaction; in commerce-svc that is one synchronous HTTP call (@fig-a-str-s3-call). A 200 continues. A 404 or 403 carrying the identity error code becomes the same `BizException` as the local implementation, a business refusal (on the settlement path, a refund obligation). Any other answer, a timeout included, raises `IdentityUnavailableException`: the caller's transaction rolls back and the request fails with 500; a Stripe webhook is then delivered again. This fails closed: an unverified buyer is never admitted and a passing outage never creates a false refund (AD-06). The cost is that checkout pauses while identity is down (AISS-03, NFR-66). It is the only synchronous call between services; browsing, sessions and role checks need none (S6).],
)

Before this strategy, `JoinTeamControl` simply asks `Account` (@fig-a-uc04-ac, @fig-a-uc04-as-basic-1). @fig-a-str-s3-after and @fig-a-str-s3-call show the same step after it.

#afig("a-str-s3-after", [S3 after: the account check as design classes (UML class notation). `UserAppService` is one port with two implementations: `RemoteUserAppService` in commerce-svc calls `InternalUserController` in identity-svc over HTTP, which uses `UserAppServiceImpl`; the callers never know which one they hold.])

#afig("a-str-s3-call", [S3 after: the one synchronous call between services (UML sequence diagram, commerce-svc and identity-svc). Only a refusal that identity itself declares becomes a business error; every other answer fails closed and rolls the caller back.])

=== S4 Persistence <sec-a-s4>

#atable(
  [S4 Persistence in the four-part form.],
  <tbl-a-s4>,
  columns: (3.3cm, 1fr),
  header: ([Part], [S4 Persistence (AD-03, AD-11, NFR-05, NFR-13)]),
  [Design concern], [Data integrity, and one owner for every table so that a service can be moved or scaled on its own.],
  [Impacted analysis object types], [Every persistent «entity», and every «control» that reads or writes one, including the collective behaviour (find, page) that the course places in a list object.],
  [Impacted use cases], [All.],
  [Impacted static structure], [Entities with attributes and associations, with no notion of storage, keys or ownership (@fig-a-sys-entities).],
  [Impacted dynamic interactions], [Entities are simply there; a control asks them.],
  [Resultant design objects], [Per aggregate root: a repository port in `blocbuy-domain` (`..repository..`), for example `PaidGroupDealRepository`, `PurchaseIntentRepository`, `CheckoutPaymentRepository`, `OrderRepository`, `UserRepository`, `NotificationRepository`. In `blocbuy-infrastructure` (`..persistence..`): an `…RepositoryImpl`, a MyBatis mapper with SQL in XML (the course's DAO, for example `PaidGroupDealMapper`), a row-shaped data object (the course's DTO, `BizGroupDealDO`) and a converter (`GroupDealConverter.toDomain`). Three databases, `identity`, `commerce` and `notification`, on one RDS server, each used by its own runtime role. UUIDv7 keys; references between aggregates by id, without foreign keys. Section 3.2.3 lists the tables of each aggregate.],
  [Resultant static structure and justification], [The application depends only on ports (`application_uses_ports_not_tech`), and persistence classes never leave infrastructure (`persistence_is_internal`), so the storage technology can change without touching the domain. The team aggregate has a read port (`GroupDealRepository`) and a write protocol for the paid path (`PaidGroupDealRepository`: receipt, lock, guarded update), because the two have different callers and SQL. Ownership is enforced twice: `afterMigrate__service_grants.sql` grants each runtime role only its own tables, and `identity_persistence_is_owned`, `commerce_persistence_is_owned`, `notification_persistence_is_owned` and `IdentityTablesStayInIdentityMappersTest` fail the build on a cross-service mapper or table name (AD-03). An association that crosses a service line becomes an id column that is never joined; identity data is reached only through S3, notification data only through S9.],
  [Resultant dynamic interactions and justification], [A service loads an aggregate through its port (`lockDeal` → mapper → data object → converter), lets it decide, and writes the result back with a guarded `UPDATE` that names the state it read; the repository refuses any row count other than one (`requireOne`), which rolls the transaction back. The domain never sees SQL, and a concurrent change is refused instead of overwritten (S8).],
)

=== S5 External Interface <sec-a-s5>

#atable(
  [S5 External interface in the four-part form.],
  <tbl-a-s5>,
  columns: (3.3cm, 1fr),
  header: ([Part], [S5 External interface (AD-06, NFR-21, NFR-22, NFR-23, NFR-31, NFR-50, NFR-66)]),
  [Design concern], [Isolation from the payment and identity providers, and funds safety: money is moved once, and an unknown outcome stays recoverable.],
  [Impacted analysis object types], [Every «boundary» to an external system (`PaymentGateway`, `IdentityProvider`) and the external actors behind them (Stripe, Google).],
  [Impacted use cases], [UC2 (Google), Pay upfront, UC3 and UC4 (Stripe Checkout and its webhook), UC6 (Stripe refunds).],
  [Impacted static structure], [A control associated with a gateway boundary.],
  [Impacted dynamic interactions], [One message to the provider and its answer, which look like one atomic step.],
  [Resultant design objects], [A port in the domain whose signatures carry only provider-free records: `CheckoutGateway`, `RefundGateway`, `OAuthIdentityGateway`, `OAuthStateStore`. An adapter in infrastructure, the only code that imports the provider SDK: `StripeCheckoutGateway`, `StripeRefundGateway`, `GoogleOAuthGateway`, `RedisOAuthStateStore`. `StripeClientProvider` sets the network limits; `StripeFailureClassifier` maps a provider failure to `PaymentErrorCode.PAYMENT_PROVIDER_REJECTED` (permanent) or `PAYMENT_PROVIDER_ERROR` (retryable). Inbound controllers that hold no logic: `StripeWebhookController` (`POST /api/v1/webhooks/stripe`) and `AuthController` (the Google callback). Keys that make a call safe to repeat: the refund's provider key (`RefundWork.providerKey`) and the Stripe event id claimed in `log_payment_webhook_event`.],
  [Resultant static structure and justification], [Each adapter is an anti-corruption layer: a provider or SDK change touches one adapter and its test, and tests replace the port. The webhook enters through CloudFront and the ALB like any request (AD-09).],
  [Resultant dynamic interactions and justification], [A database rollback cannot undo a payment or a refund, so every money call has three steps: a short transaction records the intent and the key; the provider call runs outside any transaction (`StripeCheckoutService` refuses to start one inside); a second short transaction applies the verified answer. A webhook is a hint, not a fact: it arrives as a synchronous HTTP call, its signature is verified and the Checkout session is read back with BlocBuy's own key before the settlement transaction opens, and that transaction claims the event id first, so a redelivery stops there (@sec-a-pay-upfront). In the distributed topology the settlement asks identity whether the buyer is active (S3), so the webhook depends on identity-svc at run time: during an outage it answers 500, nothing is stored, and Stripe delivers it again (AD-06, AISS-03, NFR-66).],
)

=== S6 Security <sec-a-s6>

#atable(
  [S6 Security in the four-part form.],
  <tbl-a-s6>,
  columns: (3.3cm, 1fr),
  header: ([Part], [S6 Security (AD-05, AD-09, NFR-01, NFR-46, NFR-47, NFR-58, NFR-68)]),
  [Design concern], [Authentication, authorisation and abuse limits that hold in every service without a call to identity on each request; internal endpoints that the public cannot reach.],
  [Impacted analysis object types], [Every actor-facing «boundary» (who is acting), every «control» (may this actor do this to this object) and every «entity» that belongs to a user (orders, teams, notifications).],
  [Impacted use cases], [All with a signed-in actor: UC2 to UC4, UC7 to UC9, Pay upfront and Receive notifications. UC1 is anonymous; UC5 and UC6 have no actor request.],
  [Impacted static structure], [The analysis model names the actor but not how it is identified or what it may do.],
  [Impacted dynamic interactions], [A boundary calls its control directly.],
  [Resultant design objects], [A Sa-Token session in Valkey, shared by all services, with the token in the `HttpOnly`, `Secure`, `SameSite=Lax` cookie `blocbuy-token` (24 hours). `SaTokenConfig` registers an interceptor that requires a login on every path except an explicit public list. Controllers check roles and permissions (`StpUtil.checkRole(RoleCode.ADMIN)`, `checkPermission("catalog:product:write")`). Sa-Token's `StpInterface` has two implementations, chosen by `RbacAutoConfiguration`: `StpInterfaceImpl` reads roles through `RoleRepository` in identity-svc and the monolith; `SessionStpInterface` reads the per-account snapshot in commerce-svc and notification-svc. Identity writes the snapshot at login: `LoginSessionPublisher` → `LoginAuthorizationService.snapshot` → `LoginSnapshotGateway` (`RedisLoginSnapshotGateway`). Money and invite writes need the `X-BlocBuy-Request` header and pass `RateLimitGateway` (`RedisRateLimitGateway`). Internal controllers are `@Hidden` and exist only when `blocbuy.topology=distributed`.],
  [Resultant static structure and justification], [Identity is the only writer of roles, and every other service authorises a request from the session alone, without SQL or a remote call, so identity is never on the path of an ordinary request (AD-05, NFR-58, @fig-a-str-s6-rbac). The trade-off: outside identity a role change applies at the next login, and a lost Valkey node signs everyone out (AISS-10). The domain stays free of Sa-Token. The user id always comes from the session, never from the request, and ownership is checked where the object is loaded. `/internal/**` relies on network rules: the ALB answers 404 and only peer services reach it (AISS-04).],
  [Resultant dynamic interactions and justification], [A request passes the checks cheapest first and is refused before any business read, lock or provider call: no session gives 401 (NFR-68); a missing or wrong header gives 400 or 403; a missing role or permission gives 403; too many requests give 429 (NFR-46); an object of another user is not found. In commerce-svc and notification-svc the role check reads one validated snapshot per request; a missing or malformed snapshot denies both roles and permissions (NFR-58).],
)

#afig("a-str-s6-rbac", float: true, [S6 after: where roles come from (UML class notation). `RbacAutoConfiguration` gives identity-svc the database-backed `StpInterfaceImpl` and the other two services the session-backed `SessionStpInterface`; the snapshot it reads is written once, at login, through `LoginSnapshotGateway`.])

=== S7 Scheduling <sec-a-s7>

#atable(
  [S7 Scheduling in the four-part form.],
  <tbl-a-s7>,
  columns: (3.3cm, 1fr),
  header: ([Part], [S7 Scheduling (AD-16, NFR-15, NFR-32, NFR-61)]),
  [Design concern], [Timeliness (a team expires within about a minute of its deadline, NFR-15), exactly one runner while commerce-svc runs two to six tasks, and failures that stay with one item.],
  [Impacted analysis object types], [The Scheduler actor, its «boundary» (`SchedulerTrigger`) and the «control» it starts, and the entities that control sweeps.],
  [Impacted use cases], [UC5 (team expiry), UC6 (refund worker), Pay upfront (lapsed purchase intents) and Receive notifications (outbox relay).],
  [Impacted static structure], [`SchedulerTrigger` associated with the control of the use case.],
  [Impacted dynamic interactions], [One message from the Scheduler, after which every overdue item has changed.],
  [Resultant design objects], [One timer class per job in `blocbuy-bootstrap` (`..scheduling..`), each with one `@Scheduled` fixed-delay method: `TeamExpirySweepJob` (1 min), `PurchaseIntentExpiryJob` (1 min), `RefundWorkerJob` (30 s), `OutboxRelayJob` (2 s) and `OutboxRetentionJob` (6 h). Each hands its work to `LeasedJobRunner.run(job, lease, work)`, which takes, renews and releases a lease through the port `SchedulerLeaseRepository` (`tryAcquire`, `renew`, `release`; `SchedulerLeaseRepositoryImpl` on `sys_scheduler_lock`; a two-minute lease, five minutes for retention). The work stays in application services: `GroupDealExpirySweeper.sweep(limit, leaseValid)` → `GroupDealExpiryService.expireOne(dealId)`, `RefundService.processNext()`, `OutboxRelayService.relayBatch(leaseValid)`. `SchedulingConfiguration` creates the jobs only when `blocbuy.scheduling.enabled` (or, for the relay, the event switches) is on.],
  [Resultant static structure and justification], [Jobs stay in the service that owns their data (AD-16): only `CommerceServiceApplication` scans `bootstrap.scheduling`, so identity-svc and notification-svc run no job. Every commerce task carries the timers, and the lease makes exactly one of them do the work. The lease lives in PostgreSQL, the lock of record, not in Valkey; no scheduler product, EventBridge rule or Lambda function is added. Because the work is in ordinary services, tests call `sweep` and `expireOne` directly.],
  [Resultant dynamic interactions and justification], [One run takes the lease or skips; reads a bounded batch (up to 100) on the database clock; handles each item in its own transaction; stops early when the lease cannot be renewed; and releases the lease. One transaction per item keeps each row lock short, which matters because a webhook locks the same team first (S8), and keeps one bad item from rolling back the others; a failing team is retried after five minutes through `sys_scheduled_item_retry` (NFR-32).],
)

=== S8 Concurrency <sec-a-s8>

#atable(
  [S8 Concurrency in the four-part form.],
  <tbl-a-s8>,
  columns: (3.3cm, 1fr),
  header: ([Part], [S8 Concurrency (AD-02, AD-03, NFR-13, NFR-21, NFR-27, NFR-30)]),
  [Design concern], [Data consistency and funds safety under concurrent buyers, webhooks, sweeps and workers: a team never sells more seats than its target, a payment is settled once, a refund is paid once.],
  [Impacted analysis object types], [Every «entity» with limited capacity or shared state (`Team`, `PurchaseIntent`, `Payment`, `Order`, `Refund`, `Product`); every «control» that changes several entities in one step (settlement, expiry, refund); every scheduled «control» (one runner).],
  [Impacted use cases], [UC3, UC4 and Pay upfront (the last seat, the last unit of stock, a repeated webhook), UC5 (expiry against a late payment), UC6 (concurrent refund workers), UC7 (one credit per join), UC9 (concurrent listing edits), Receive notifications (repeated deliveries).],
  [Impacted static structure], [No notion of concurrency.],
  [Impacted dynamic interactions], [Two actors send messages to one entity at once, in no defined order.],
  [Resultant design objects], [Lock methods on the ports, implemented with `SELECT … FOR UPDATE`: `PaidGroupDealRepository.lockDeal` (`PaidGroupDealMapper.lockDeal`), `PurchaseIntentRepository.lockById` (`BizPurchaseIntentMapper.lockById`), `CheckoutPaymentRepository.lock` (`PaymentMapper.lock`), `ProductRepository.lockById`. Guarded updates (`PaidGroupDealMapper.updateDeal` writes only where `current_size` and `status` still match). Unique indexes as the last line of defence (section 3.2.3). Work queues with `FOR UPDATE SKIP LOCKED` (`RefundMapper.claimNext`, the purchase-intent sweep, outbox retention). Idempotency records: the Stripe event claim (`CheckoutPaymentRepository.claimEvent`), the participation receipt (`PaidGroupDealRepository.reserveAndLock`) and `sys_consumed_message`. A version check on administrator edits (`AdminListingAppServiceImpl.lockForChange`). The job lease of S7.],
  [Resultant static structure and justification], [Locks are methods on the ports, so the lock order can be checked from the interfaces, not only from SQL. The locks live in PostgreSQL, so they hold across every commerce task; an in-memory lock would not (commerce scales to six tasks, NFR-64).],
  [Resultant dynamic interactions and justification], [Every path that changes a team's seats, orders or payments takes the team's row lock first, so two such paths on one team run one after the other. In the race for the last seat both settlements ask for the team lock; the second waits, then finds the team no longer open, and `GroupDeal.requireJoinWindow` refuses it with `DEAL_CLOSED`, so its money becomes a refund obligation (@fig-a-uc04-ds-race). The expiry sweep queues on the same lock, so a sweep and a late payment cannot both win.],
)

*One transaction across several aggregates.* This is the canonical justification that the other parts cite. DDD's usual rule is one aggregate per transaction. BlocBuy breaks it on purpose in two places. The settlement of a paid participation (UC3, UC4, Pay upfront) changes four aggregates in one database transaction: `CheckoutPayment` (succeeded, with its outcome), `GroupDeal` (one more member, perhaps succeeded), `PurchaseIntent` (fulfilled) and `Order` (created, and confirmed when the team fills), plus one unit of `Product` stock. A team's expiry (UC5, UC6) changes the `GroupDeal`, all its orders and one refund obligation per paid member in one transaction (backend ADR 0002). Five reasons make this the right choice:

+ *Money and seat must agree.* A succeeded payment must end with exactly one seat and one order, or with exactly one refund obligation, never with money taken and neither (NFR-27, NFR-30). Only one atomic commit guarantees that without a window in which it is false.
+ *All of them are in one database.* AD-02 placed catalog, group-buy, order and payment in commerce-svc precisely because they share this transaction; their tables are all in the `commerce` database (AD-03). A local ACID transaction is therefore available, with no two-phase commit across services.
+ *One fixed lock order.* Every path takes its locks as a subset of team → purchase intent → payment, then the participation receipt and, last, the product row when stock is taken (backend ADR 0001; the order is stated in the `PurchaseIntentRepository` contract and implemented by `OrderFulfilmentAdapter.lockForSettlement`, which locks the team, then the intent, before `StripeCheckoutService` locks the payment). The expiry path locks the team, then the orders (`OrderLifecycleSubscriber`, `@Order(10)`), then the payments (`TeamExpiryRefundSubscriber`, `@Order(20)`). No path waits for an earlier lock while holding a later one, so the paths cannot deadlock. The webhook claims its event id before the first row lock, so a redelivery waits on the claim, not on a row.
+ *Short and bounded.* The settlement runs in one `TransactionTemplate` transaction, `READ COMMITTED` with a 10-second timeout; the expiry in `@Transactional(timeout = 10)`. Port implementations declare `Propagation.MANDATORY`, so they cannot run outside it. No provider call happens inside it (S5).
+ *Why not a saga.* Splitting the four aggregates across services would need compensating steps for a seat sold, stock taken and money received: refunds issued by compensation, stock released, and an orchestrator. The team estimated 50 to 55 person-days for it, the whole budget (AD-02), and a saga exposes states in which money and seat disagree. A saga is the roadmap path if payment is ever extracted (AISS-02).

#atable(
  [Lock order per path, as implemented. Each path takes a subset of one combined order, so no two paths can deadlock.],
  <tbl-a-lock-order>,
  columns: (auto, 1fr, auto),
  header: ([Path], [Locks, in the order taken], [Use cases]),
  [Checkout preparation], [team (JOIN only) → purchase intent → payment], [Pay upfront, UC3, UC4],
  [Webhook settlement], [event claim (unique insert) → team → purchase intent → payment → participation receipt → product stock], [Pay upfront, UC3, UC4],
  [Team expiry], [team → orders of the team → payments of the team], [UC5, UC6],
  [Purchase-intent expiry], [team → purchase intent (`SKIP LOCKED`)], [Pay upfront],
  [Refund worker], [one refund obligation (`SKIP LOCKED`), no other lock], [UC6],
  [Listing edit and team-price quote], [product (with a version check on edits)], [UC9, UC3],
)

=== S9 Domain and Integration Events <sec-a-s9>

#atable(
  [S9 Domain and integration events in the four-part form.],
  <tbl-a-s9>,
  columns: (3.3cm, 1fr),
  header: ([Part], [S9 Domain and integration events (AD-04, NFR-30, NFR-39, NFR-59, NFR-60, NFR-61, NFR-63)]),
  [Design concern], [Extensibility and decoupling: a fact in one module or service has consequences in others without the publisher knowing them; across services, no event is lost or sent for a rolled-back fact.],
  [Impacted analysis object types], [Every «control» whose result has consequences in another context (admission, settlement, expiry); every «control» that carries out a consequence (confirm or cancel orders, record refund obligations, credit a sharer, refresh a cached offer, notify members); the «entity» objects of the consuming contexts (`Order`, `Refund`, `ShareCredit`, `Notification`).],
  [Impacted use cases], [UC3 and UC4 (a payment succeeds, a member joins, a team fills), UC5 (orders confirmed or cancelled), UC6 (refund obligations), UC7 (share credit), Pay upfront and Receive notifications.],
  [Impacted static structure], [A control associated with entities of other contexts: the resolving control reaches `Order` and `Refund` directly.],
  [Impacted dynamic interactions], [One message from the control for each consequence.],
  [Resultant design objects], [In process: domain event records in the publisher's domain package (`MemberJoined`, `TeamSucceeded`, `TeamExpired` in `groupbuy.event`; `PaymentSucceeded` in `payment.event`), published through Spring's `ApplicationEventPublisher` by `PaidGroupDealService`, `GroupDealExpiryService` and `StripeCheckoutService`. Subscribers with `@TransactionalEventListener`: before commit, `OrderLifecycleSubscriber` (`@Order(10)`), `TeamExpiryRefundSubscriber` (`@Order(20)`) and `IntegrationOutboxWriter` (lowest precedence); after commit, `ShareCreditSubscriber` (its own `REQUIRES_NEW` transaction) and `OpenDealOfferCacheSubscriber`. Across services (commerce-svc, only when `blocbuy.events.enabled`): `IntegrationOutboxWriter` maps an event to `TeamSucceededV1`, `TeamExpiredV1` or `PaymentSucceededV1` in an `IntegrationEventEnvelope` (`blocbuy-client`), resolves recipients through `IntegrationRecipientRepository` and inserts a `sys_outbox` row through `OutboxMapper`. `OutboxRelayJob` runs `OutboxRelayService.relayBatch`, which reads `OutboxRelayRepository` and sends through the port `OutboxEventTransport` (Template Method base `BatchingOutboxEventTransport`; `SnsOutboxEventTransport` on AWS, `SqsOutboxEventTransport` against ElasticMQ locally). One SNS topic fans out to one SQS queue per consumer, each with a dead-letter queue after five receives. In notification-svc, `SqsNotificationEventConsumer` decodes with `IntegrationEventDecoder` (an anti-corruption layer to `NotificationEvent`) and calls the port `NotificationEventHandler`, implemented by `NotificationEventAppService`.],
  [Resultant static structure and justification], [The publisher names no subscriber; Spring's event multicaster finds subscribers by event type. This is publish–subscribe by implicit invocation, not a textbook GoF Observer: no subject holds a list of observers. A new consequence is a new subscriber class in the consuming module, and a new consumer service is a new queue and subscription: the audit queue was added in Terraform without changing commerce-svc (AD-04). Domain events stay inside commerce; only versioned contracts with ids and no personal data cross services (NFR-60). notification-svc depends on no other service's code (`notification_depends_on_no_other_service`).],
  [Resultant dynamic interactions and justification], [Inside the publisher's transaction, `publishEvent` only registers the event. Before commit, the before-commit subscribers run synchronously on the same connection in `@Order`; an exception in any of them rolls back the whole fact, so a team cannot expire while nobody is owed money (NFR-30). After commit, the after-commit subscribers run synchronously on the same thread in their own work and must not undo the fact (NFR-39). The outbox row commits with the fact or not at all, which removes the dual write of "commit, then send". Later, the relay publishes it at least once; this is the only asynchronous hop. The consumer records `(eventId, consumer)` and the notification in one transaction, so a duplicate changes nothing (NFR-59), then deletes the message; a malformed one is left for the dead-letter queue (NFR-63). The price is eventual consistency of about two seconds (AISS-11). @fig-a-str-s9-flow shows the whole path; A.13 designs the relay and the consumer in full.],
)

#afig("a-str-s9-flow", [S9 after: one fact travelling in process and across services (UML sequence diagram, commerce-svc and notification-svc). Listener callbacks are synchronous calls inside the transaction; the only asynchronous messages are the relay's publish and the queue's delivery, which come after the commit.])

=== Coverage of the Analysis Object Types <sec-a-coverage>

@tbl-a-coverage shows that every kind of analysis object is transformed by at least one strategy, and every strategy transforms at least one kind. A designer starts from the object's kind and applies every strategy in its row.

#atable(
  [Coverage matrix: analysis object types (rows) and the strategies that transform them (columns). ● = applies.],
  <tbl-a-coverage>,
  columns: (1fr, auto, auto, auto, auto, auto, auto, auto, auto, auto),
  align: (left, center, center, center, center, center, center, center, center, center),
  header: ([Analysis object type (example)], [S1], [S2], [S3], [S4], [S5], [S6], [S7], [S8], [S9]),
  [«boundary» used by a human actor (`JoinTeamUI`)], [], [●], [●], [], [], [●], [], [], [],
  [«boundary» to an external system (`PaymentGateway`, `IdentityProvider`)], [], [], [●], [], [●], [●], [], [●], [],
  [«boundary» of the Scheduler actor (`SchedulerTrigger`)], [], [], [], [], [], [], [●], [], [],
  [«control» started by a person (`JoinTeamControl`)], [●], [], [●], [], [], [●], [], [●], [●],
  [«control» started by time (team expiry, refund worker, relay)], [●], [], [], [], [], [], [●], [●], [●],
  [«entity» owned by commerce (`Team`, `Payment`, `Order`)], [●], [], [], [●], [], [], [], [●], [●],
  [«entity» owned by another service (`Account`, `Notification`)], [●], [], [●], [●], [], [●], [], [], [●],
)

=== Mapping Rule from BCE to COLA and DDD <sec-a-mapping-rule>

@tbl-a-mapping-rule is the rule that each part's mapping table applies to its own objects. Boundaries become adapters, controls become application services, and entities become aggregates with their persistence chain.

#atable(
  [Mapping rule: where each kind of analysis element lands in the design, and the strategies that put it there.],
  <tbl-a-mapping-rule>,
  columns: (auto, 1.6fr, 1fr, auto),
  header: ([Analysis element], [Design elements], [Layer, module], [Strategies]),
  [«boundary» (screen)], [Route, feature component and hook with the generated client; REST «controller» with request and response records], [Frontend; adapter (`blocbuy-adapter`, `..web..`)], [S2, S3, S6],
  [«boundary» (external system)], [«gateway» port with provider-free records; «adapter» with the SDK; inbound «controller» for callbacks], [Domain; infrastructure; adapter], [S5],
  [«boundary» (Scheduler)], [«scheduled job» with `LeasedJobRunner`], [`blocbuy-bootstrap` (`..scheduling..`), commerce-svc only], [S7],
  [«control»], [«application service» per step and module, owning the transaction; a «domain service» where a rule spans several aggregates (`DualPricingStrategy`); cross-module calls through ports and «facade» interfaces; the cross-service call through `UserAppService`], [Application (`blocbuy-application`)], [S1, S3, S8],
  [A consequence inside a «control»], [«subscriber» to a «domain event»; across services an «integration event» through the outbox], [Application; infrastructure; `blocbuy-client`], [S9],
  [«entity»], [«aggregate root» (or an «entity» or «value object» inside one) + «repository» port + `…RepositoryImpl` + «mapper» + «data object» + «converter» + table in the owning service's database], [Domain; infrastructure], [S1, S4, S8],
  [Association between entities], [Id reference without a foreign key; a composition becomes rows written by the root's repository; across services an id that is never joined], [Domain; database], [S4, S3],
  [Analysis operation], [One design operation with typed parameters and return; creation by a static factory or the Factory Method], [Any], [S1],
)

=== Subsystems and Their Interfaces <sec-a-subsystems>

A subsystem is a package whose only public elements are interfaces (04a, pages 79–81). @fig-a-sys-subsystems partitions the design by service and, inside each service, by COLA layer. Dependencies point toward the domain: adapters use application services, application services use domain ports, and infrastructure implements those ports. Six interfaces cross a subsystem line, and nothing else does; they are listed below the figure.

#afig("a-sys-subsystems", [Subsystem view (UML class notation with «component» boxes; the diagram kit cannot draw packages, so each box stands for the package of one COLA layer of one service and lists its key elements). Dependencies point toward each service's domain layer. The services meet only through the internal HTTP call, the broker («async»: SNS to SQS) and the login snapshot in Valkey, which is shared data, not a code dependency, and is not drawn.])

The six interfaces are:

+ *`UserAppService` and the internal HTTP endpoints* (commerce → identity, synchronous; S3). In commerce-svc the port is realised by `RemoteUserAppService` in the assembly, which calls `InternalUserController`.
+ *`PaymentIntentPort` and `PaymentBusinessPort`* (payment → order, inside commerce; S3, S8). Payment owns both ports; order implements them, so payment never depends on order code.
+ *Supplier facades* `CatalogPurchaseSupport`, `GroupDealPurchaseSupport` and `GroupDealSettlementSupport` (inside commerce; S3).
+ *`OutboxEventTransport`* (commerce application → infrastructure → SNS; S9), with the contracts of `blocbuy-client` as the message schema.
+ *The SQS consumer and `NotificationEventHandler`* (SQS → notification; S9). There is no code dependency between commerce and notification.
+ *The login snapshot in Valkey* (identity writes, commerce and notification read through `SessionStpInterface`; S6).


*What the strategies make cheap, and what they couple.* A new payment provider is one new gateway adapter (S5). A new consequence of a team's success is one new subscriber (S9), and a new consumer service one new queue. Moving identity in or out of a process is a wiring change (S3), and the monolith still runs from the same code (S1). The coupling that remains is deliberate and recorded: checkout depends on identity at run time (AISS-03); payment shares a transaction, and so a service, with the team and the order (S8, AISS-02); and every service depends on one Valkey node for sessions (AISS-10).
