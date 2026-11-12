// Logical Architecture & Design: part of 03-solution-overview.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *
#import "../figures.typ": afig
#import "../diagrams/d4a-eventstorming-timeline.typ": d4a

// AD and AISS numbers come from ../registers.md.
#let ad-table(..rows) = table(
  columns: (auto, 1fr),
  table.header([Identifier], [Description]),
  ..rows.pos().map(((id, d)) => ([#id], [#d])).flatten(),
)

BlocBuy is one codebase that runs as three services on AWS. The split follows transaction boundaries (§3.1); the AWS design favours cost and isolation over always-on capacity (§3.2); four further decisions govern how it runs and changes (§3.3); and §3.4 lists what these choices cost. @tbl-views lists the views that describe it.

#figure(
  table(
    columns: (1.2fr, 0.8fr, 1.2fr, 1.5fr, 0.9fr, 1.3fr),
    table.header([View], [4+1 view], [Notation], [Concern answered], [Main reader], [Figure or table]),
    [System context], [Scenarios], [UML component diagram at C4 level 1], [Who uses BlocBuy and which systems it calls], [Assessor and business], [@fig-d1-context],
    [Use cases], [Scenarios], [UML use case diagram], [What BlocBuy does for each actor], [Business and assessor], [@fig-d2-use-cases],
    [Logical (logical detail)], [Logical], [UML component diagrams], [Which elements exist and how they talk], [Architect and developer], [@fig-d3-logical-view, @fig-d3b-logical-events-data],
    [Logical deployment], [Logical], [UML deployment diagram], [Which node type hosts each tier], [Architect], [@fig-d5b-logical-deployment],
    [Context map], [Logical], [UML component diagram with DDD relations], [Which context depends on which], [Architect and developer], [@fig-d4-context-map],
    [EventStorming timeline], [Process], [EventStorming table], [Where the hot spots are], [Architect], [@tbl-d4a-eventstorming],
    [Aggregates], [Logical], [UML class diagrams], [What one transaction protects], [Developer], [@fig-d7a-aggregates, @fig-d7b-aggregates, @fig-d7c-aggregates, @fig-d7d-aggregates],
    [Lifecycles], [Logical], [UML state machine diagrams and a table], [Which state changes the money rules allow], [Developer], [@fig-st-team, @fig-st-intent, @fig-st-payment, @tbl-lifecycles],
    [Domain and integration events], [Process], [UML component diagrams], [Which events cross which boundary], [Developer and operator], [@fig-d8-domain-events, @fig-d8b-integration-events, @tbl-domain-events],
    [Code layers], [Development], [COLA layer list and rule table], [Where code may depend on what], [Developer], [@tbl-arch-rules],
    [Physical deployment (physical detail deployment)], [Physical], [UML deployment diagrams], [Where each part runs and how the nodes talk], [Operator and architect], [@fig-d5-deployment, @fig-d5a-communication-paths],
    [Network and security], [Physical], [AWS group notation], [What can reach the data tier], [Security reviewer], [@fig-d6-network],
    [Workload placement], [Physical], [Table], [Which compute runs each kind of workload], [Architect and operator], [@tbl-workload-compute],
    [Data ownership], [Physical], [UML class diagrams with «table»], [Which service owns which table], [Developer and database administrator], [@fig-d9a-identity-data, @fig-d9b-notification-data, @fig-d9c-commerce-team-data, @fig-d9d-commerce-payment-data],
    [Delivery pipeline], [Development], [UML activity diagrams], [How a change reaches production], [Operator], [@fig-d10a-ci-image-chain, @fig-d10c-image-scan-gate, @fig-d10b-delivery],
    [Settlement design], [Scenarios and logical], [UML class, sequence and object diagrams], [How a paid join is admitted or refunded], [Developer], [@fig-d11-uc4-design-classes, @fig-d12a-two-buyers-start, @fig-d12b-two-buyers-join, @fig-d15-team-succeeded-objects, @fig-race-last-seat],
    [Expiry design], [Process], [UML class and sequence diagrams], [How expiry and refunds stay consistent], [Developer], [@fig-d13-expiry-design-classes, @fig-d14a-team-expiry, @fig-d14b-refund-retry, @fig-d14c-outbox-relay],
  ),
  kind: table,
  caption: [Architectural views, with the concern and the reader each one serves. Source: this report.],
) <tbl-views>

== Logical Architecture & Design

BlocBuy runs as three services: `identity-svc` and `notification-svc` are reusable platform services, and `commerce-svc` holds the business. They talk through one synchronous call and an event bus. We split only where no transaction crosses the boundary (AD-02). Code boundaries keep the six DDD bounded contexts apart, so a context can be extracted later (AD-01, AD-18); database permissions separate only the three services, because the four contexts in `commerce-svc` share one runtime role (AD-03, @tbl-ownership).

The logical elements, interfaces and interactions in §3.1 are roles, not products; §3.2 maps them to AWS products and network placement.

=== Key Architectural Decisions

The logical design rests on one rule: split services only where no database transaction crosses the boundary (AD-02). The other five decisions keep that split safe: one codebase with two topologies (AD-01), a database per service (AD-03), events through an outbox (AD-04), a shared session (AD-05) and one fail-closed synchronous call (AD-06). @tbl-ad-logical states each with its driver, the options we rejected and the trade-off.

#figure(
  ad-table(
    ("AD-01", [
      *One codebase, two topologies: a strangler migration from the SWE5006 modular monolith to three services, with the monolith kept as a fallback* (#src("backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008]). \
      *Driver:* Modifiability; cost (@tbl-drivers). \
      *Context:* The same code serves two modules with different needs. SWE5006 needed a stable single-host environment (`dev`) until late October 2026; SWE5001 needs independently scalable services on AWS within a budget of about 50 person-days. \
      *Options considered:* (a) one monolithic deployment for both modules: rejected, because it cannot separate the scaling signals or failure domains of sign-in, checkout and notifications; (b) separate repositories and pipelines per service: rejected, because duplicated pipelines and cross-repository contract changes would use the whole schedule and destabilise `dev`; (c) one Maven reactor with the packaging chosen per assembly (`blocbuy.topology=monolith|distributed`). \
      *Choice:* (c). Business code stays in the shared COLA modules (`blocbuy-client` to `blocbuy-infrastructure`). Three thin assemblies under `backend/services/` each package their own entry class (`IdentityServiceApplication`, `CommerceServiceApplication`, `NotificationServiceApplication`) with a restricted `@SpringBootApplication(scanBasePackages = …)` and `@MapperScan`. \
      *Trade-off:* Each image carries the shared dependencies, so it is slightly larger, and CI must stop accidental imports across contexts (AD-18). \
      *Consequence:* On 17 October 2026 on AWS, all three services were built and deployed independently from one codebase while `dev` stayed healthy (#run(39008208244)[promotion run 39008208244]).
    ]),
    ("AD-02", [
      *Split services along transaction boundaries, not domain nouns: `identity-svc`, `commerce-svc` and `notification-svc`* (#src("backend/docs/adr/0001-payment-business-integration.md")[ADR 0001], #src("backend/docs/adr/0002-team-lifecycle-events.md")[ADR 0002], #src("backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008]). \
      *Driver:* Consistency over scalability (@tbl-drivers). \
      *Context:* The SWE5006 code has six bounded contexts (`identity`, `catalog`, `groupbuy`, `order`, `payment`, `notification`); the EventStorming study tested which of them must share one transaction (§3.1.4.1). Settling a member's paid admission (`StripeCheckoutService.settle`, then `OrderFulfilmentAdapter.fulfil`) checks the payment, locks the `GroupDeal` and admits the buyer, creates the `Order`, marks the `PurchaseIntent` fulfilled and decrements `Product` stock. When the last seat fills, `BEFORE_COMMIT` listeners also confirm all the team's orders. \
      *Options considered:* (a) five or six services, one per bounded context, coordinated by a saga orchestrator: rejected, because we estimated 50–55 person-days (all of our capacity) to build and test compensations, such as releasing stock and refunding through Stripe, across the network; (b) separate services for `groupbuy` and `order`: rejected, because the team's transition decides an order's lifecycle (`PENDING` to `CONFIRMED` or `CANCELLED`) in the same transaction; (c) keep the four contexts that share the transaction (`catalog`, `groupbuy`, `order`, `payment`) in `commerce-svc`, and extract `identity-svc` and `notification-svc`, which no transaction crosses. \
      *Choice:* (c). \
      *Trade-off:* `commerce-svc` is a coarser unit of scaling and deployment (AISS-02, with a planned extraction path for `payment`). \
      *Consequence:* Tested locally, not on AWS: `StripeLocalIntegrationTest` admits exactly one of 20 concurrent buyers for the last seat and commits 19 refund obligations. By design, one local PostgreSQL transaction settles payment, seat, order and stock together.
    ]),
    ("AD-03", [
      *A database per service: separate PostgreSQL databases, per-service runtime roles, Flyway grant callbacks and ArchUnit mapper guards* (#src("backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008]). \
      *Driver:* Modifiability; security (@tbl-drivers). \
      *Context:* No service may read or change another service's tables, yet SWE5006 and SWE5001 share one Flyway migration stream (`V1`–`V28`) while both are developed. \
      *Options considered:* (a) one shared schema with the same credentials for all three services: rejected, because SQL joins across contexts couple deployments silently (the legacy `PaymentAttentionMapper.xml` joined `sys_user` until the payment-attention view was rebuilt); (b) three RDS instances with diverging Flyway streams: rejected, because of three times the database cost and migration drift before SWE5006 ended; (c) three databases (`identity`, `commerce`, `notification`) on one RDS instance, where each runtime role (`*_app`) gets `SELECT, INSERT, UPDATE, DELETE` only on the tables its service owns (`afterMigrate__service_grants.sql`). \
      *Choice:* (c), backed in the build by `ArchitectureTest.persistenceOwnedBy` and `IdentityTablesStayInIdentityMappersTest` (@tbl-arch-rules). \
      *Trade-off:* The other services' tables still exist, empty, in each database until each stream is baselined after SWE5006, and the three databases share one server's capacity (AISS-05). \
      *Consequence:* Tested in CI: the ownership rules fail the build when a service imports another service's persistence package. Configured, not tested: the PostgreSQL grants deny a cross-service query at run time.
    ]),
    ("AD-04", [
      *Cross-service events through a transactional outbox, broker choreography (one SNS topic fanned out to one SQS queue per consumer, each with a dead-letter queue) and idempotent consumers* (#src("backend/docs/adr/0007-cross-service-events-sns-sqs.md")[ADR 0007]). \
      *Driver:* Availability; modifiability (@tbl-drivers). \
      *Context:* When a team succeeds or expires, or a payment settles in `commerce-svc`, `notification-svc` and future consumers must react reliably. The checkout transaction must not depend on them, and a crash must not lose an event. \
      *Options considered:* (a) synchronous REST calls from `commerce-svc` to `notification-svc`, or a broker publish `AFTER_COMMIT` without an outbox: rejected, because a crash between commit and send drops the event (the dual-write hazard), and synchronous calls pass notification outages on to checkout; (b) a central saga orchestrator (Step Functions) or an always-on broker (Kafka on MSK, RabbitMQ on Amazon MQ): rejected, because notifications after commit need fan-out, not multi-step orchestration, and an always-on broker breaks the disposable cost model; (c) point-to-point SQS without SNS: rejected, because a second consumer would need a change in `commerce-svc`; (d) `BEFORE_COMMIT` writes to `sys_outbox`, a leased `OutboxRelayService` that publishes to an SNS standard topic, one SQS queue per consumer with a dead-letter queue (DLQ) after five receives, and de-duplication in `sys_consumed_message`. \
      *Choice:* (d). \
      *Trade-off:* Consumers are eventually consistent, with a relay polling lag of about 2 s (AISS-11, by design), and one relay copy runs at a time. \
      *Consequence:* The business change and its outbox row commit in one transaction, so an event exists only if its change committed; the relay publishes it afterwards, at least once, and consumers drop duplicates through `sys_consumed_message`. The `audit` queue, which has no consumer, was subscribed with one `consumers` element and one filter line in the messaging module and no change to `commerce-svc` (§4.4). Its throughput limit is in AD-16.
    ]),
    ("AD-05", [
      *A shared sign-in session and a role snapshot in the session store* (#src("backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008]). \
      *Driver:* Availability; scalability (@tbl-drivers). \
      *Context:* Every authenticated request to `commerce-svc` or `notification-svc` needs its token checked and the caller's roles and permissions (buyer or administrator). In the monolith, `StpInterfaceImpl` read the identity tables (`sys_role`, `sys_permission`) on every request. \
      *Options considered:* (a) a synchronous call from `commerce-svc` and `notification-svc` to `identity-svc` on every request: rejected, because it adds network latency to every call and makes `identity-svc` a hard dependency for browsing and notifications; (b) stateless JWTs checked at an API gateway or by Amazon Cognito: rejected, because sign-out revocation and the one-time OAuth state were already built on Sa-Token; (c) one shared session store holding the Sa-Token session and a per-account role-based access control (RBAC) snapshot (`${tokenName}:rbac:${loginType}:{${userId}}`), written atomically by a Lua script with a 120 s refresh fence at sign-in. \
      *Choice:* (c). Identity writes the snapshot at login (`RedisLoginSnapshotGateway`). The other assemblies read it with `SessionStpInterface`, which loads it through Sa-Token's `SaTokenDao` and memoises it once per request, without SQL or remote calls. \
      *Trade-off:* A role change takes effect outside `identity-svc` only at the user's next sign-in, and a session-store outage ends active sessions (AISS-10). \
      *Consequence:* Configured, not tested: no authentication or RBAC check calls another service; each reads the shared session.
    ]),
    ("AD-06", [
      *One synchronous call between services (`commerce-svc` to `identity-svc` through `UserAppService`), failing closed on the money path* (#src("backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008]). \
      *Driver:* Consistency; security (@tbl-drivers). \
      *Context:* Browsing and session checks need no call to `identity-svc`. Issuing a purchase intent, settling a payment (`OrderFulfilmentAdapter.fulfil`) and the administrator's payment-attention views must check that a buyer is `ACTIVE` (`UserAppService.requireActive`) or resolve public nicknames (`UserAppService.publicNicknames`). \
      *Options considered:* (a) replicate `sys_user` into `commerce-svc` through events: rejected for the course window, because a stale copy could admit a disabled user to a paid team; (b) fail open when `identity-svc` cannot be reached: rejected, because admitting an unchecked or disabled buyer breaks the payment rules; (c) a synchronous HTTP adapter (`RemoteUserAppService`) over internal service discovery (`GET /internal/users/{id}/active`, `GET /internal/users/{id}/public-nickname` and `POST /internal/users/public-nicknames`), with a 500 ms connect and 1,500 ms read timeout, no retry, and failing closed. \
      *Choice:* (c). Only HTTP 404 (`IDENTITY_404_001`) and HTTP 403 (`IDENTITY_403_001`) map to business refusals, which record a refund obligation. A timeout, a 5xx answer or a routing error throws `IdentityUnavailableException` (HTTP 500) and rolls back the settlement, so Stripe retries later. \
      *Trade-off:* New checkouts and webhook settlements pause while `identity-svc` is down (AISS-03). Personally identifiable information (PII) stays in `identity-svc`, and a network fault never causes a false business refund. \
      *Consequence:* Tested locally, not on AWS, on 15 October: in the #src("docs/swe5001/evidence/20261015-local-failure-rehearsal/README.md")[identity-outage rehearsal], START and a signed webhook returned HTTP 500 while identity was down, and redelivery succeeded once it was back (AISS-13).
    ]),
  ),
  kind: table,
  caption: [Logical architectural decisions AD-01 to AD-06. Source: `registers.md` and the ADRs linked in each row.],
) <tbl-ad-logical>

=== Tiers and Layers

We organise the logical elements along two dimensions: four system tiers (@tbl-tiers) that separate client delivery, business processing, messaging and data storage, and four COLA layers inside every service that keep the domain model free of framework and infrastructure code (AD-18).

==== System tiers

#figure(
  table(
    columns: (auto, 1.3fr, 2.2fr, 1.4fr),
    table.header([Tier], [Logical elements], [Responsibility], [Communication style]),
    [1. Presentation and edge],
    [Web client, CDN and edge gateway],
    [Serves the static client files of the single-page application (SPA), ends public TLS, routes everything through one origin (`/api/v1/*`) and blocks outside access to internal endpoints (`/internal/*`).],
    [Synchronous HTTPS from browsers and from the payment provider's webhook.],

    [2. Application],
    [`identity-svc`, `commerce-svc`, `notification-svc`],
    [Runs stateless use cases, enforces domain invariants, owns local ACID transactions and serves REST endpoints from one public contract.],
    [Synchronous REST from the edge; one internal REST call (`commerce-svc` to `identity-svc`, AD-06).],

    [3. Integration],
    [Outbox relay, message broker, event queues (notification, audit) and their DLQs],
    [Decouples committed domain facts from their consumers through broker choreography, with at-least-once delivery, event-type filtering and dead-letter isolation.],
    [Asynchronous batch publish and long-poll queue reads (AD-04).],

    [4. Data],
    [Identity, commerce and notification data stores; shared session store],
    [Stores each service's aggregates, its outbox and consumed-message tables, the scheduler leases, and the shared sign-in sessions and RBAC snapshots.],
    [Connection pools with per-service credentials (AD-03).],
  ),
  kind: table,
  caption: [System tiers and their responsibilities. Source: code.],
) <tbl-tiers>

The logical deployment view (@fig-d5b-logical-deployment) maps the tiers to node types without products.

==== Service layering and automated guardrails

Inside the backend reactor, every bounded context (`identity`, `catalog`, `groupbuy`, `order`, `payment`, `notification`) follows the Alibaba COLA package structure. The reactor has ten modules: the six COLA modules, `blocbuy-platform` and the three service assemblies.

1. *Adapter layer* (`blocbuy-adapter`, `..web..` and `..web.internal..`). It holds the `@Validated @RestController` classes (`ProductController`, `GroupDealController`, `StripeWebhookController`, `NotificationController`, `InternalUserController`). Adapters bind HTTP, validate parameters and check Sa-Token permissions, then call application services. They never import persistence classes or MyBatis mappers.
2. *Application layer* (`blocbuy-application`, `..command..`, `..query..`, `..service..`, `..assembler..`). It runs the use cases, owns the `@Transactional` boundaries, publishes and handles in-process events (`OrderLifecycleSubscriber`, `TeamExpiryRefundSubscriber`, `ShareCreditSubscriber`) and turns domain objects into views. It depends only on `blocbuy-domain` ports and `blocbuy-client` contracts.
3. *Domain layer* (`blocbuy-domain`, `..model..`, `..pricing..`, `..event..`, `..repository..`, `..gateway..`, `..exception..`). It holds aggregate roots, entities, value objects, domain services (`DualPricingStrategy`), domain event records and the repository and gateway ports. `blocbuy-domain` depends only on the JDK and Lombok (its `pom.xml`). In CI, `domain_is_framework_free` checks that its model, repository, pricing, event and exception packages import no Spring, MyBatis, Jackson, Jakarta Validation or Sa-Token class.
4. *Infrastructure layer* (`blocbuy-infrastructure`, `..persistence..`, `..messaging..`, `..outbox..`). It implements the repository and gateway ports with MyBatis-Plus data objects (`*DO`), mappers (`*Mapper`), converters (`*Converter`), Valkey templates, Stripe adapters and AWS SDK transports (`SnsOutboxEventTransport`, `SqsNotificationEventConsumer`).
5. *Contract and assembly modules* (`blocbuy-client`, `blocbuy-bootstrap`, `blocbuy-platform`, `services/*`). `blocbuy-client` holds the versioned integration event contracts (`IntegrationEventEnvelope`, `TeamSucceededV1`, `TeamExpiredV1`, `PaymentSucceededV1`). `blocbuy-bootstrap` and the three `services/*` modules wire the Spring Boot configuration and the scheduled jobs (`LeasedJobRunner`). `blocbuy-platform` holds the per-service database grants (`db/swe5001/afterMigrate__service_grants.sql`).

Five developers worked in parallel, so 19 automated architecture checks (@tbl-arch-rules) enforce these layer and service boundaries on every pull request: 17 `@ArchTest` rules in `ArchitectureTest.java` and two plain tests for what ArchUnit cannot see. This table is the home of the checks; AD-18 (§3.3) and §4.4 refer to it.

#figure(
  table(
    columns: (auto, 1.6fr, 2.2fr),
    table.header([Group], [Rule or test], [Boundary protected]),
    [Layers and coding style (8 rules)],
    [`domain_is_framework_free` \ `application_uses_ports_not_tech` \ `controllers_never_touch_persistence` \ `persistence_is_internal` \ `domain_has_no_outward_dependencies` \ `no_field_injection` \ `controllers_are_validated` \ `numeric_request_params_have_range_constraints`],
    [The domain imports no framework class and depends on no outer layer. Application services use ports, not MyBatis or Redis. Controllers never touch persistence, and `*DO`, `*Mapper` and `*RepositoryImpl` stay inside `..persistence..`. Injection is through constructors only. Controllers are `@Validated`, and numeric request parameters have range constraints.],

    [Service boundaries (9 rules)],
    [`commerce_reaches_identity_only_through_user_port` \ `only_identity_reads_current_user` \ `nothing_depends_on_notification` \ `notification_depends_on_no_other_service` \ `identity_persistence_is_owned` \ `commerce_persistence_is_owned` \ `notification_persistence_is_owned` \ `internal_controllers_are_hidden_and_distributed_only` \ `internal_paths_live_in_internal_controllers`],
    [`commerce` reaches `identity` only through the `UserAppService` port, and only `identity` reads the current user. `notification` has no code dependency on, or from, `commerce` or `identity`. In bytecode, the three ownership rules stop a service from using another service's persistence package. Controllers in `..web.internal..` are `@Hidden`, exist only with `blocbuy.topology=distributed` and alone hold the `/internal/*` paths.],

    [Plain tests (2)],
    [`IdentityTablesStayInIdentityMappersTest` \ `InternalPathsContractTest`],
    [The first scans the MyBatis XML for identity tables only (`sys_user`, `sys_user_oauth`, `sys_role`, `sys_permission`, `rel_*`); `biz_notification` is guarded only by the package rules. The second fails if an `/internal/*` path leaks into `contracts/openapi.json`.],
  ),
  kind: table,
  caption: [Automated architecture checks, run in CI by `./mvnw verify`. Source: `ArchitectureTest.java` and the two plain tests.],
) <tbl-arch-rules>

=== Nodes and Subsystems

@fig-d3-logical-view shows the request path: who calls which interface, and the external systems. @fig-d3b-logical-events-data shows the published events and the data stores. Both use UML component notation with role names only; @tbl-logical-physical maps each element to its product.

#afig("d5b-logical-deployment", [Logical deployment view (UML deployment diagram). Tiers map to node types without products: each service is one service package in a language runtime on its own application server node, and all three use the session store and the database server; @tbl-logical-physical names the products.])

#afig("d3-logical-view", [Logical view, request path (UML component diagram; red fill = core subdomain, blue = web client). Producers and consumers reach the subsystems only through the edge gateway, and the one synchronous call between subsystems is `commerce-svc` requiring `UserAppService`.])

#afig("d3b-logical-events-data", [Logical view, events and data (UML component diagram; red fill = holds the core subdomains, amber = messaging). `commerce-svc` publishes PublishedEvents V1 once and the message broker fans it out to one queue per consumer; the audit queue has no consumer, so a new subscriber needs only a new queue. Each subsystem owns its data store, and all three use the shared session store (the line from the tier frame).])

==== Subsystem and solution element catalogue

@tbl-logical-elements catalogues every element of @fig-d3-logical-view and @fig-d3b-logical-events-data with its stereotype, tier, provider (built by us, commercial off-the-shelf (COTS) cloud infrastructure or external software as a service (SaaS)) and interfaces.

#figure(
  table(
    columns: (1.2fr, auto, 1fr, 1.3fr, 1.8fr),
    table.header([Logical element], [Stereotype and tier], [Provider], [Provided and required interfaces], [Responsibility in the solution]),
    [Web client],
    [«component» \ Presentation],
    [Custom (built by us)],
    [*Req:* AuthAPI, CommerceAPI, NotificationAPI (sync)],
    [Single-page browser client for buyers and the platform administrator; its API client is generated from the public contract.],

    [CDN and edge gateway],
    [«component» \ Presentation],
    [COTS (managed edge and layer-7 router)],
    [*Req:* AuthAPI, CommerceAPI, NotificationAPI (sync)],
    [Delivers the client files, routes `/api/v1/auth/*` and `/api/v1/users/*` to `identity-svc`, `/api/v1/notifications*` to `notification-svc` and the other public paths to `commerce-svc`; blocks `/internal/*`. The payment webhook passes through it physically (§3.2).],

    [`identity-svc`],
    [«subsystem» \ Application (platform)],
    [Custom (built by us)],
    [*Prov:* AuthAPI (public), `UserAppService` (internal) \ *Req:* OIDC sign-in, identity data store, session store],
    [Reusable platform service for sign-in through the identity provider, the user lifecycle (`ACTIVE`, `DISABLED`), RBAC snapshots in the session store and internal user-status checks.],

    [`commerce-svc`],
    [«subsystem» \ Application (business)],
    [Custom (built by us)],
    [*Prov:* CommerceAPI, PaymentWebhookAPI, PublishedEvents V1 (async) \ *Req:* `UserAppService` (sync, `RemoteUserAppService`), CheckoutAPI (sync), commerce data store, session store],
    [Core business application that holds the `catalog`, `groupbuy`, `order` and `payment` modules with `IntegrationOutboxWriter` and the leased `OutboxRelayService` (AD-02, AD-04).],

    [`notification-svc`],
    [«subsystem» \ Application (platform)],
    [Custom (built by us)],
    [*Prov:* NotificationAPI (public) \ *Req:* event queue (notification, async poll), notification data store, session store],
    [Reusable platform service that consumes versioned integration events idempotently, then stores and serves in-app buyer notifications.],

    [Message broker],
    [«component» \ Integration],
    [COTS (managed publish–subscribe)],
    [*Req:* PublishedEvents V1 (async)],
    [Fans out each published `IntegrationEventEnvelope<T>` message by its `eventType` filter to one queue per consumer.],

    [Event queues (notification, audit)],
    [«component» \ Integration],
    [COTS (managed queues)],
    [*Prov:* one queue endpoint per consumer],
    [Hold each consumer's copy, with a dead-letter queue after five receives. The audit queue has no consumer (it demonstrates fan-out, ADR 0007).],

    [Identity, commerce and notification data stores],
    [«database» \ Data],
    [COTS (managed relational database)],
    [*Prov:* SQL access through a per-service role],
    [Durable ACID storage for each service's own aggregates and technical tables (outbox, consumed messages, scheduler lease; §3.2.3).],

    [Session store],
    [«database» \ Data],
    [COTS (managed in-memory key-value store)],
    [*Prov:* key-value access],
    [Holds single-use OAuth state tokens, sign-in sessions and per-account RBAC role and permission snapshots (AD-05).],

    [Identity provider],
    [«external system» \ External],
    [External SaaS (identity provider)],
    [*Prov:* OIDC sign-in],
    [Authenticates buyers and the administrator at sign-in (UC2); kept behind `OAuthIdentityGateway`.],

    [Payment provider],
    [«external system» \ External],
    [External SaaS (payment provider)],
    [*Prov:* CheckoutAPI \ *Req:* PaymentWebhookAPI],
    [Runs hosted card checkout sessions, sends signed payment webhooks (UC3, UC4) and makes refunds (UC6).],
  ),
  kind: table,
  caption: [Logical elements with their tier, provider, interfaces and responsibility. Source: @fig-d3-logical-view and @fig-d3b-logical-events-data.],
) <tbl-logical-elements>

=== Platform Design

Six bounded contexts map onto three services: only `catalog`, `groupbuy`, `order` and `payment` must share the settlement transaction, so they stay together in `commerce-svc` (AD-02). We modelled the domain in four steps: a simulated EventStorming study of all use cases (§3.1.4.1); subdomains and bounded contexts linked by context-map patterns (§3.1.4.2 and §3.1.4.3); transaction boundaries that map six contexts onto three services (§3.1.4.3); and aggregates, state machines and integration events (§3.1.4.4 and §3.1.4.5). The result lets a new producer or consumer join without a change to the existing core (§3.1.4.6).

==== EventStorming method and decision traceability

We ran EventStorming as a desk study, not as a live workshop: two concurrent part-time modules left no time for one. The simulated study is dated 25 September in its header (#src("docs/swe5001/architecture-options-study/06-eventstorming-ddd.md")[study record]) and follows Alberto Brandolini's three levels (big picture, process level, software design). The study modelled code that already existed. Its inputs were six repository artefacts: the business proposal (UC1–UC9), the `blocbuy-domain` models and events, the `blocbuy-application` services and subscribers, backend ADR 0001 and ADR 0002 (22 September), the Flyway migrations `V1`–`V17` (later extended to `V28`) and the 23 HTTP operations the study counted in `contracts/openapi.json` (31 on 8 November, §3.1.4.6).

1. *Big picture and hot spots.* We placed 34 domain events in time order across six phases (listing, access and discovery, pay-first team formation, team resolution, refund, growth). Four events divide the life of a team into acts: Product Published (supply exists), `PaymentSucceeded` (the buyer's money is committed), Team Started (an open team's join window begins), and `TeamSucceeded` or `TeamExpired` (the team's fate is fixed). Annotating the timeline exposed ten concurrency and consistency hot spots, HS-1 to HS-10. Examples are HS-1 (20 buyers paying for the last seat at once), HS-2 (duplicate or out-of-order Stripe webhooks), HS-4 (the expiry sweep racing a settling webhook on the same team) and HS-8 (order confirmation and refund recording running `BEFORE_COMMIT` inside the team's transaction). The others, as found at study time: HS-3 (a payment arriving after the team expired), HS-5 (the last stock unit taken by another team during settlement), HS-6 (Stripe's 24 h refund idempotency window), HS-7 (scheduling switched off by default), HS-9 (no API for team terms) and HS-10 (a reserved `deal_id` left on unpaid START intents). The two big-picture boards are in the study (#src("docs/swe5001/architecture-options-study/diagrams/eventstorming-big-picture.svg")[board 1: listing to paid membership], #src("docs/swe5001/architecture-options-study/diagrams/eventstorming-big-picture-2.svg")[board 2: resolution, refund and growth]), with all ten hot spots in its §2.3.
2. *Process level for UC3–UC6.* For each core flow we traced the causal chain read model, actor or time trigger, command, aggregate, domain event, policy (`BEFORE_COMMIT`, `AFTER_COMMIT` or outbox) and next command (@tbl-d4a-eventstorming).
3. *Software design and grouping test.* We grouped aggregates and policies around the hot spots and tested whether `groupbuy` and `order` could be separate services. HS-1 and HS-8 showed that they share one consistency boundary.

#figure(
  d4a(),
  caption: [EventStorming process-level timeline for UC3–UC6: actor, command, domain event, policy and hot spot per act, each row in its sticky colour. Only the four events in code font are Java records; the others are workshop concepts. Source: architecture options study 06 (25 Sep 2026), checked against the code on 11 Nov 2026.],
  kind: table,
) <tbl-d4a-eventstorming>

@tbl-es-traceability traces each EventStorming finding to the decision it explained or proposed.

#figure(
  table(
    columns: (1fr, 1.3fr, 1.4fr, 1.2fr, 1.2fr),
    table.header([EventStorming finding], [Problem exposed], [Decision], [Recorded artefact and code], [Status at study time]),
    [`PaymentSucceeded` and HS-8],
    [Confirming the payment, admitting the member, creating the order and taking stock must all commit or all roll back.],
    [Keep `catalog`, `groupbuy`, `order` and `payment` in one service (`commerce-svc`, AD-02) instead of the five- or six-service saga option.],
    [ADR 0001, ADR 0002, ADR 0008; `OrderFulfilmentAdapter.fulfil`],
    [ADR 0001 and ADR 0002 existing (22 Sep), explained; three-service split proposed by the study, decided in ADR 0008 (12 Oct)],

    [HS-1 (last-seat race) and HS-4 (`TeamExpirySweepJob` versus webhook race)],
    [Concurrent webhooks and the one-minute expiry job contend for the same `GroupDeal` row.],
    [Global lock order (§3.1.4.4) and status-guarded updates.],
    [ADR 0001; `OrderFulfilmentAdapter.lockForSettlement`, `StripeCheckoutService.settle`],
    [Existing (ADR 0001), explained],

    [HS-2 (duplicate or replayed Stripe webhooks)],
    [At-least-once delivery can replay a settled event on several threads.],
    [Idempotent event claim (`log_payment_webhook_event`), `ParticipationReceipt` and the unique index `uq_biz_order_payment_id`.],
    [ADR 0001; `StripeLocalIntegrationTest` (tested locally: eight concurrent replays of one webhook leave one member and one order)],
    [Existing (ADR 0001), explained],

    [`TeamSucceeded` and `TeamExpired` for other services],
    [Notification and analytics need team outcomes without coupling to the checkout transaction.],
    [Write enriched V1 events to `sys_outbox` at `BEFORE_COMMIT` (lowest precedence) and relay them through the broker (AD-04).],
    [ADR 0007; `IntegrationOutboxWriter`, `OutboxRelayService`],
    [Proposed by the study (HS-8: "There is no outbox"), decided in ADR 0007 (11 Oct)],

    [`MemberJoined` and UC12 (credit the sharer)],
    [Crediting a sharer is not financial; a credit failure must never roll back a paid seat.],
    [Publish `MemberJoined` in-process and record the credit in an `AFTER_COMMIT` (`REQUIRES_NEW`) subscriber that catches `RuntimeException`.],
    [ADR 0003; `ShareCreditSubscriber`, `ShareInvite`, `ShareCredit`],
    [Existing (ADR 0003, 25 Sep), explained],

    [HS-6 (Stripe's 24 h idempotency window on refunds)],
    [Retrying a failed refund with a new key after 24 h risks paying the buyer twice.],
    [Execute refunds outside database transactions (`RefundWorkerJob`); after 23 h the worker only searches for an existing refund and never creates one, and a mismatch becomes a review case for the platform administrator.],
    [Study HS-6; `RefundService.processNext`; `RefundWorkerJob` (ADR 0002); `PaymentReviewCase`],
    [Existing in code (`RefundService`), explained],
  ),
  kind: table,
  caption: [EventStorming findings traced to the decisions they explained or proposed. Source: the EventStorming study and backend ADRs 0001, 0002, 0003, 0007 and 0008.],
) <tbl-es-traceability>

*What EventStorming decided, and what it did not.* The six bounded contexts predate the study: SWE5006 built them as modules. The study explained the existing transaction and event timing (ADR 0001, ADR 0002, ADR 0003) and proposed two new decisions: the three-service boundary (ADR 0008) and the integration from the outbox through SNS to SQS (ADR 0007). It did not decide the language and framework, the COLA layering, buying Stripe and Google, the session design (AD-05) or the AWS runtime (§3.2.1).

==== Subdomain classification and build-versus-buy strategy

We classified BlocBuy's business capabilities as core, supporting or generic subdomains (@tbl-subdomains). We build the core ourselves and buy commodity capabilities as external SaaS or managed cloud services.

#figure(
  table(
    columns: (1.3fr, auto, auto, 1.5fr, 1.5fr),
    table.header([Subdomain], [Classification], [Build or buy], [Rationale], [Package or provider]),
    [Team formation and resolution],
    [Core],
    [Build],
    [The platform's differentiator: time-boxed team size, a fair last seat and one shared fate for the team.],
    [`com.blocbuy.groupbuy` (`commerce-svc`)],

    [Team purchase settlement],
    [Core],
    [Build],
    [Pay-first rule: every verified payment yields either one seat and one order, or a durable refund obligation.],
    [`com.blocbuy.order` and `groupbuy` (`commerce-svc`)],

    [Share attribution and growth (UC7, UC12)],
    [Core],
    [Build],
    [Drives buyer-led growth: random 128-bit invite links (`ShareInvite`) and credit for attributed joins (`ShareCredit`).],
    [`com.blocbuy.groupbuy` (`ShareInviteAppService`, `ShareCreditSubscriber`)],

    [Catalogue and dual pricing],
    [Supporting],
    [Build, plus an external ranker],
    [Supports the core with the individual-versus-team `DualPricingStrategy` and `ProductTeamTerms`; search reranking is an optional external service.],
    [`com.blocbuy.catalog` (`commerce-svc`) and the TypeSafe reranker (`ListingSearchReranker`)],

    [Payment and refund orchestration],
    [Supporting],
    [Build the rules, buy the payments],
    [The escrow-like rules (frozen `CheckoutQuote`, refund obligations at expiry, the 23 h review guard) are ours; Payment Card Industry (PCI) card processing and money movement are bought.],
    [`com.blocbuy.payment` (`commerce-svc`) and the Stripe Checkout and Refunds API (`CheckoutGateway`, `RefundGateway`)],

    [Identity and access],
    [Generic],
    [Build the roles, buy the sign-in],
    [Standard authentication and role-based access control; Google verifies credentials, and Sa-Token holds the sessions and RBAC snapshots.],
    [`com.blocbuy.identity` (`identity-svc`) and Google Sign-In (`OAuthIdentityGateway`)],

    [Notification],
    [Generic],
    [Build on managed queues],
    [Standard event-driven in-app inbox, built as an independent platform service to show that a consumer scales apart from the producer.],
    [`com.blocbuy.notification` (`notification-svc`)],
  ),
  kind: table,
  caption: [Subdomain classification and build-or-buy choice. Source: EventStorming study §4.2.],
) <tbl-subdomains>

==== Context mapping and service decomposition

We mapped events to contexts in four steps, each recorded in the study: the big-picture timeline and hot spots (§2), the subdomains (§4.2), the bounded contexts with their language (§4.3) and the test of whether `order` is separate from `groupbuy` (§4.4); the context map (§4.5) follows. @tbl-es-to-context shows the path from the events to the services.

#figure(
  table(
    columns: (1.4fr, 1.1fr, 1fr, 0.9fr, 1.5fr),
    table.header([Pivotal events and hot spots], [Subdomain], [Bounded context (package)], [Service], [Why]),
    [Product Published; HS-9], [Catalogue and dual pricing], [`catalog`], [`commerce-svc`], [Shares the stock decrement with settlement (HS-5).],
    [Team Started, `MemberJoined`, `TeamSucceeded`, `TeamExpired`; HS-1, HS-4, HS-7, HS-10], [Team formation and resolution], [`groupbuy`], [`commerce-svc`], [The team lock decides the last seat and the expiry.],
    [`PaymentSucceeded`; HS-1, HS-3, HS-8], [Team purchase settlement], [`order` (with `groupbuy`)], [`commerce-svc`], [One invariant: one paid seat and one order, or a refund (study §4.4).],
    [`PaymentSucceeded`; HS-2, HS-6], [Payment and refund orchestration], [`payment`], [`commerce-svc`], [Settlement and refund obligations commit with the team.],
    [`MemberJoined` (credit)], [Share attribution and growth], [`groupbuy` (`ShareInvite`, `ShareCredit`)], [`commerce-svc`], [The credit follows a paid join, in its own transaction (ADR 0003).],
    [Sign-in], [Identity and access], [`identity`], [`identity-svc`], [No transaction crosses; one synchronous call (AD-06).],
    [`TeamSucceeded`, `TeamExpired` (published)], [Notification], [`notification`], [`notification-svc`], [Only consumes events (AD-04).],
  ),
  kind: table,
  caption: [From EventStorming findings to subdomains, bounded contexts and services. Source: EventStorming study §2–§4.],
) <tbl-es-to-context>

The study recommended one Team Purchase context for `groupbuy` and `order` (§4.4); the code keeps them as two packages with separate aggregates inside one service, which is the same consistency boundary.

*How we mapped the contexts.* We drew a boundary where a term changed meaning, where data ownership could be separated without foreign keys and where an external model needed translation:
1. *Language test.* A "quote" in `catalog` (`TeamPriceQuote`) is an exploratory price for a browsing buyer; it holds no stock and lapses without side effects. In `payment` (`CheckoutQuote`), a "quote" is a frozen financial commitment bound to a `PurchaseIntent` and a Stripe session. Likewise, `GroupDeal` in `groupbuy` models the team's size and window rules, while `Order` in `order` models one buyer's receipt and product snapshot.
2. *Upstream and downstream test.* For every pair of collaborating contexts and external providers, we decided which side sets the contract (upstream, U) and which side adapts (downstream, D), and chose the DDD pattern in @fig-d4-context-map and @tbl-context-map.

Arrows in @fig-d4-context-map point from the downstream context to the upstream one; @tbl-context-map lists each pair upstream first. The map capitalises the context names; the table uses the Java package names. Shaded contexts are the core subdomains. One edge uses a label from outside the standard context-map set: `order` depends on the ports that `payment` declares, as a downstream context does, but at run time `payment` calls `order` through them. Customer–Supplier or Conformist would name the dependency and hide that inversion, so the map labels the edge with Fowler's Separated Interface.

#figure(
  table(
    columns: (1.2fr, 1fr, 0.9fr, 1.7fr, 1.4fr),
    table.header([Upstream (U) → downstream (D)], [Context-map pattern], [Interaction], [Mechanism in code], [Why this pattern]),
    [Google Sign-In (OIDC) (U) → `identity` (D)],
    [Anti-corruption layer (ACL)],
    [Synchronous call],
    [`OAuthIdentityGateway` port, implemented by the Google HTTP adapter in `identity` infrastructure.],
    [Keeps Google token schemas and OAuth protocol details out of the `User` aggregate.],

    [Stripe (U) → `payment` (D)],
    [ACL],
    [Synchronous call; signed webhook],
    [`CheckoutGateway` and `RefundGateway`; webhook signatures are verified and translated into the domain `CheckoutGateway.Session`.],
    [Keeps Stripe SDK objects and webhook payloads inside `payment` infrastructure; domain logic sees only `CheckoutPayment` and `RefundWork`.],

    [TypeSafe reranker (U) → `catalog` (D)],
    [ACL (fail-open)],
    [Synchronous call],
    [`ListingSearchReranker` port, with a fallback to deterministic PostgreSQL ordering.],
    [Treats external semantic search as an optional enhancement that never blocks browsing when it fails.],

    [`identity` (U) → `catalog`, `groupbuy`, `order` and `payment` in `commerce-svc` (D)],
    [Customer–Supplier and Open Host Service (OHS)],
    [Synchronous call],
    [User status through `UserAppService` (`requireActive`, `publicNicknames`; route `/internal/users/{id}/active`, client `RemoteUserAppService`); the session store is shared read-only through Sa-Token (`SaTokenDao`) (AD-05, AD-06).],
    [`identity-svc` exposes a narrow internal REST protocol and a session snapshot, so `commerce-svc` never touches the `sys_user` tables.],

    [`catalog` (U) → `order` (D)],
    [Customer–Supplier],
    [In-process call],
    [`CatalogPurchaseSupport` (`lockStartQuote`, `lockQuotedStart`, `requirePurchasable`, `consumeOneUnit`).],
    [`order` depends on `catalog` to freeze product snapshots, check the `ON_SALE` status and decrement stock in the settlement transaction.],

    [`groupbuy` (U) → `catalog` (D)],
    [Customer–Supplier],
    [In-process call],
    [`GroupDealQueryService` returns groupbuy `OpenDealOffer` records; catalogue's `ProductAppServiceImpl` translates them into `GroupDealOffer` value objects for `DualPricingStrategy`.],
    [Listings show live open-team prices without exposing `GroupDeal` internals to `catalog`.],

    [`groupbuy` ↔ `order` (within `commerce-svc`)],
    [Partnership (shared consistency boundary)],
    [In-process call; in-process event (`BEFORE_COMMIT`)],
    [`GroupDealPurchaseSupport`, `OrderFulfilmentAdapter` and `BEFORE_COMMIT` listeners (`OrderLifecycleSubscriber` at `@Order(10)`).],
    [Both contexts change in the same local ACID transaction: a seat admission and its `Order` succeed or roll back together (AD-02).],

    [`payment` (U) → `order` (D)],
    [Separated Interface (payment owns the port, order implements it; an architectural pattern, not GoF)],
    [In-process call],
    [`payment` declares `PaymentBusinessPort` and `PaymentIntentPort`; `order` implements them (`OrderFulfilmentAdapter` implements `PaymentBusinessPort`; `PurchaseIntentAppService` implements `PaymentIntentPort`).],
    [Keeps `payment` business-agnostic and reusable for future seeds: it drives settlement through its own ports without importing `order` or `groupbuy`.],

    [`groupbuy` (U) → `payment` (D)],
    [Domain event (in-process)],
    [In-process event (`BEFORE_COMMIT`)],
    [`TeamExpired` domain event record, handled `BEFORE_COMMIT` by `TeamExpiryRefundSubscriber` (`@Order(20)`).],
    [Decouples team expiry from refund persistence, while `DealStatus.EXPIRED` and the `biz_refund` rows still commit atomically.],

    [`commerce-svc` (U) → `notification` (D)],
    [Open Host Service and Published Language],
    [Asynchronous event (outbox, SNS, SQS)],
    [`IntegrationEventEnvelope<T>` (`TeamSucceededV1`, `TeamExpiredV1`, `PaymentSucceededV1`) through outbox, message broker and event queue (AD-04).],
    [Versioned JSON contracts without personal data let `notification-svc` consume business facts without coupling to `commerce-svc`. The audit queue is a second subscriber queue with no consumer, not a bounded context.],

    [`common` ↔ all contexts],
    [Shared Kernel],
    [Compile-time library],
    [`com.blocbuy.common` (`ErrorCode`, `BizException`, `Result`, `UuidV7`, `PageQuery`, `RoleCode`).],
    [Kept minimal (technical primitives, no business entities), so its changes do not ripple across domain models.],
  ),
  kind: table,
  caption: [Context-map relationships with their pattern, interaction style, mechanism and rationale. Source: @fig-d4-context-map and the code.],
) <tbl-context-map>

#afig("d4-context-map", [Context map (UML component diagram with DDD context-map labels). Each dashed arrow runs from the downstream (D) context to the upstream (U) one and names the pattern; Partnership and Shared Kernel are undirected. External systems sit behind anti-corruption layers, and payment owns the ports that order implements (Separated Interface).])

==== Aggregate derivation and lifecycles

*How we derived the aggregates.* We did not map one aggregate per table. We derived every aggregate root with a four-step test:
1. *Find the commands and the business invariant.* We listed every command that changes state in the process-level timeline and asked which rule must hold when the transaction commits, even under concurrent requests. For example, a `GroupDeal` must never exceed `targetSize`, must never admit the same buyer twice and must become `SUCCEEDED` in the transaction that fills the last seat.
2. *Draw the smallest consistency boundary.* Objects that must change together to protect that invariant, such as the `PaidMember` (`biz_group_member`) and `ParticipationReceipt` (`biz_group_participation_receipt`) records of a team, stay inside one boundary: `PaidGroupDealRepository` writes them under the team lock, although the `GroupDeal` class holds no collection of them. `ShareInvite` and `ShareCredit` instead have their own repositories and refer to the team by `dealId`; they are not part of `GroupDeal`. Minting an invite takes no team lock, and `ShareCreditSubscriber` writes the sharer's credit `AFTER_COMMIT` in a separate `REQUIRES_NEW` transaction, so a credit failure cannot roll back a paid admission (#src("backend/docs/adr/0003-share-credit-post-commit-subscriber.md")[ADR 0003]).
3. *Refer to other aggregates by id.* No aggregate holds an object reference to another root. `GroupDeal` refers to `Product` by `productId`; `Order` refers to `GroupDeal` by `dealId` and to `CheckoutPayment` by `paymentId`; `RefundWork` refers to `CheckoutPayment` by `paymentId`. By our DDL convention, the PostgreSQL tables have no foreign keys and no `CHECK` constraints; application services and unique indexes keep references consistent (§3.2.3).
4. *Govern the shared settlement transaction.* Strict DDD changes one aggregate per transaction. Hot spot HS-8 showed that splitting the settlement of a paid admission into four transactions would leave buyers paid without a seat, or seated without stock. We therefore recorded a bounded exception (AD-02, #src("backend/docs/adr/0001-payment-business-integration.md")[ADR 0001]): `StripeCheckoutService.settle` and `OrderFulfilmentAdapter.fulfil` change `CheckoutPayment`, `GroupDeal`, `PurchaseIntent`, `Order` and `Product` stock in one local PostgreSQL transaction. The webhook's idempotency claim (`log_payment_webhook_event`) is written first; then the rows are locked with `SELECT … FOR UPDATE` in one global order: team (lock 1, `GroupDeal`), then purchase intent (lock 2, `PurchaseIntent`), then payment (lock 3, `CheckoutPayment`). Only fulfilment then locks two more rows, after lock 3 and without a number: the participation receipt of this payment (`PaidGroupDealRepository.reserveAndLock`) and the product row of the guarded stock update. A START has no team row to lock yet, so it takes lock 2, lock 3, then the receipt. Expiry follows the same order: it takes lock 1, cancels the team's orders with a status-guarded update, then locks the paid payments (lock 3). That update needs no separate `SELECT … FOR UPDATE`, but it row-locks the orders it changes until commit; it takes them while it holds lock 1, as every other write to a team's orders does, so these locks cannot form a deadlock cycle. The purchase-intent expiry also takes lock 1 before lock 2 (`PurchaseIntentExpiryService.expireOne`). This fixed order prevents deadlocks when buyers race for the last seat (HS-1) or the expiry sweep races a webhook (HS-4).

Three further transactions write more than one aggregate for the same reasons: team expiry (ADR 0002), listing terms with their price policy (UC9) and first sign-in (UC2).

@tbl-aggregates derives the 11 aggregate roots of the six bounded contexts; @fig-d7a-aggregates to @fig-d7d-aggregates show their class structure by context. The last row holds the refund records, which are not roots; the review case is a read model inside the `CheckoutPayment` aggregate.

#figure(
  table(
    columns: (1fr, 1.2fr, 1.2fr, 1.6fr, 1.4fr, 1.1fr),
    table.header([Aggregate root (context)], [Entities and value objects], [Commands], [Invariants], [Consistency boundary], [Events emitted]),
    [`User` \ (`identity`)],
    [Value objects: `UserStatus`, `RegisterSource`, `OAuthProvider`, `UserProfile`],
    [`registerViaOAuth`, `touchLogin`, `ensureActive`, `disable`],
    [Email is unique across accounts; a `DISABLED` user cannot open a session or settle a purchase.],
    [One local transaction on the `identity` database (`sys_user`); the first sign-in also writes `OAuthIdentity` in that transaction (UC2).],
    [None],

    [`OAuthIdentity` \ (`identity`)],
    [Refers to `User` by `userId`],
    [Created by `OAuthUserTxService.registerNewUser` at the first sign-in],
    [One identity per provider subject (`uq_sys_user_oauth_provider_subject`); provider tokens never enter the domain model.],
    [`UserOAuthRepository`, `sys_user_oauth`],
    [None],

    [`Product` \ (`catalog`)],
    [Value objects: `ProductTeamTerms`, `ListingDetails`, `ProductStatus`, `PricingMode`, `TeamTermsChange`],
    [`draft`, `revise`, `publish`, `delist`, `requireDeletable`],
    [Only `ON_SALE` products can be browsed and bought; `revise` cannot change published commercial terms (`originalPrice`, currency, stock); only a never-published draft can be deleted; stock ≥ 0 (conditional `UPDATE … WHERE status = 'on_sale' AND stock > 0`).],
    [Own transaction for administrator changes (`biz_product`); in settlement, `CatalogPurchaseSupport.consumeOneUnit` takes one unit through `ProductRepository.consumeOneUnit`.],
    [None],

    [`TeamPricePolicy` \ (`catalog`)],
    [Entity: `TeamPriceQuote`, stored in `biz_team_price_quote` through the same `TeamPriceRepository` \ Value objects: `ListingPrice`, `GroupDealOffer`],
    [`TeamPriceAppService.configure`, `requireQuotable`],
    [The team price is lower than `Product.originalPrice`; policy revisions are append-only within `[validFrom, validUntil]`.],
    [One local transaction on the `commerce` database (`biz_team_price_policy`, `biz_team_price_quote`).],
    [None],

    [`GroupDeal` \ (`groupbuy`)],
    [Records: `PaidMember`, `ParticipationReceipt` (step 2) \ Value objects: `DealStatus`, `PaidParticipation`, `NewDealTerms`, `PaidAdmission`],
    [`PaidGroupDealCreator.initiate` (start), `joinPaid`, `expire`],
    [`targetSize` ≥ 2; 1 ≤ `currentSize` ≤ `targetSize`; the leader is member one and cannot join again; one seat per buyer; admission only while `startTime` ≤ now < `expireTime` on the database clock; filling the last seat sets `SUCCEEDED` in the same transaction.],
    [Lock 1 (`FOR UPDATE` on `biz_group_deal`) in settlement and expiry transactions; member rows (`biz_group_member`) are written under it, and the receipt row (`biz_group_participation_receipt`) is locked after lock 3 in fulfilment.],
    [`MemberJoined` (join only; `PaidGroupDealService.joinConfirmed`), `TeamSucceeded` (`PaidGroupDealService`), `TeamExpired` (`GroupDealExpiryService`)],

    [`ShareInvite` \ (`groupbuy`)],
    [Fields: `ref`, `dealId`, `sharerUserId`],
    [`ShareInvite.mint`, through `ShareInviteAppService`],
    [Unique `ref` (`uq_biz_share_invite_ref`); one invite per sharer per team (`uq_biz_share_invite_deal_sharer`).],
    [`ShareInviteRepository`, `biz_share_invite`; refers to the team by `dealId`; minting takes no team lock (ADR 0003).],
    [None],

    [`ShareCredit` \ (`groupbuy`)],
    [Fields: `dealId`, `paymentId`, `sharerUserId`, `inviteeUserId`, `creditTime`],
    [`ShareCredit.of(MemberJoined)` in `ShareCreditSubscriber.onMemberJoined`],
    [Immutable; at most one credit per paid admission (`uq_biz_share_credit_payment`); a member never credits themself (`MemberJoined` rejects member = sharer).],
    [`ShareCreditRepository`, `biz_share_credit`; written `AFTER_COMMIT` in `REQUIRES_NEW`, so a credit failure cannot roll back the paid seat (ADR 0003).],
    [None],

    [`PurchaseIntent` \ (`order`)],
    [Value objects: `PurchaseOperation` (`START` or `JOIN`), `PurchaseQuoteWindow`],
    [`issue` (`POST /payments/intents`), `fulfilBy`, `requireOwner`, `expireAt`],
    [Owned by one buyer (another buyer gets 404); freezes the server-side price, team size and sharer; consumed by at most one payment (partial unique index on `fulfilledPaymentId`); expires at `min(quoteValidUntil, businessDeadline)`. Its state is derived from `fulfilledPaymentId` and `expiredAt`; there is no state enum (@fig-st-intent).],
    [Own transaction when issued; lock 2 (`FOR UPDATE`) in settlement (`biz_purchase_intent`).],
    [None],

    [`Order` \ (`order`)],
    [Value object: `OrderStatus` (`PENDING`, `CONFIRMED`, `CANCELLED`)],
    [`Order.create`, `confirm`, `cancel`],
    [Created only inside a verified settlement transaction, never directly from the browser; exactly one order per payment (`uq_biz_order_payment_id`); `PENDING` moves to `CONFIRMED` or `CANCELLED` only on the team's outcome.],
    [Written in the settlement transaction and by the `BEFORE_COMMIT` subscriber (`@Order(10)`) on `biz_order`.],
    [None],

    [`CheckoutPayment` \ (`payment`)],
    [Value object: `CheckoutQuote` (`intentId`, `userId`, `operation`, `businessId`, `amount`, deadlines) \ Read model: `PaymentReviewCase` (`biz_payment_review_case`: `reason`, `status`)],
    [`bind` (checkout session), `succeed`, `cancelUnpaid`, `failTerminalUnpaid`; `CheckoutPaymentRepository.review(paymentId, reason, operator)` opens a review case],
    [The amount comes only from the frozen `CheckoutQuote`; at most one pending payment per intent; provider references cannot be rebound after confirmation; a webhook applies only after the signature check and the `log_payment_webhook_event` claim.],
    [Stripe calls run outside the database transaction; lock 3 (`FOR UPDATE`) in settlement and expiry (`biz_payment`); `CheckoutPaymentRepository` also writes `biz_payment_review_case`.],
    [`PaymentSucceeded` (published by `StripeCheckoutService.fulfilOrCompensate`)],

    [`Notification` \ (`notification`)],
    [Value objects: `NotificationType`, `NotificationEvent`],
    [`NotificationEventAppService.handle`],
    [Immutable once created (title ≤ 128 characters, content ≤ 1,024 characters); idempotent per `(event_id, consumer)` in `sys_consumed_message`, so a duplicate delivery never duplicates a notice.],
    [One local transaction per message on the `notification` database (`sys_consumed_message` and `biz_notification`).],
    [None],

    [Refund records \ (`payment`; not a root)],
    [`RefundWork`: claim snapshot of one `biz_refund` row (`paymentId`, `amount`, `currency`, `providerIntentId`, `providerRefundId`, `firstAttemptAt`, `version` as a fencing token).],
    [`TeamExpiryRefundService.recordExpiredTeam`; `RefundService.processNext`, `RefundService.reconcile`],
    [At most one refund obligation per payment (`UNIQUE(payment_id)`); after the 23 h idempotency window no new Stripe refund is created: the worker only searches, and a mismatch becomes a review case for the platform administrator.],
    [The obligation is recorded in the settlement or expiry transaction (`biz_refund`); `RefundWorkerJob` calls the Stripe Refunds API outside any database transaction.],
    [None],
  ),
  kind: table,
  caption: [Aggregate roots derived from their commands, invariants and consistency boundaries, with the refund records, which are not a root. Source: code and the four-step test above.],
) <tbl-aggregates>

#afig("d7a-aggregates", [Aggregate view of identity, catalog and notification (UML class diagram). Each «aggregate root» has its own repository and refers to another root only by id (a dashed dependency labelled "id"); value objects and enumerations belong to their root.])

#afig("d7b-aggregates", [Aggregate view of groupbuy (UML class diagram). `PaidMember` and `ParticipationReceipt` belong to the `GroupDeal` aggregate and are written by its repository under the team lock; `ShareInvite` and `ShareCredit` are separate roots that refer to the team by `dealId`, so sharing never takes the team lock.])

#afig("d7c-aggregates", [Aggregate view of purchase intent and payment (UML class diagram). The purchase intent and the checkout payment are two roots linked only by ids; `RefundWork` is not a root but the refund worker's claim snapshot of one `biz_refund` row.])

#afig("d7d-aggregates", [Aggregate view of order (UML class diagram). The order is a third root: its placement terms refer to the checkout payment of @fig-d7c-aggregates only by `paymentId`.])

Three lifecycles carry the money rules, so we draw them: the team (@fig-st-team), the purchase intent (@fig-st-intent) and the checkout payment (@fig-st-payment). @tbl-lifecycles lists the simpler lifecycles of the order, the listing and the refund obligation. `GroupDeal` checks every transition with `DealStatus.canTransitionTo`: only `OPEN` moves to a final state (`SUCCEEDED` when `currentSize` reaches `targetSize`, `EXPIRED` when the database clock passes `expireTime`), and a final state never changes. The enum also allows `OPEN` to `FAILED` and `OPEN` to `CANCELED` for a future administrative cancellation; no code path uses them in this release. The purchase intent has no state field: its state is derived from `fulfilledPaymentId` and `expiredAt`.

#afig("a-uc05-st-deal", lbl: <fig-st-team>, [Team lifecycle view (UML state machine diagram). Only an open team admits a paid join; the join that fills the last seat makes the team succeed, and a paid join that cannot be admitted is refunded. `FAILED` and `CANCELED` exist in `DealStatus` but have no incoming transition.])

#afig("a-uc03-st-intent", lbl: <fig-st-intent>, [Purchase intent lifecycle view (UML state machine diagram). The states are derived from `fulfilledPaymentId` and `expiredAt`, not stored; a payment that arrives after a deadline is refunded and never fulfils the intent.])

#afig("a-pay-st-payment", lbl: <fig-st-payment>, [Checkout payment lifecycle view (UML state machine diagram). `prepare` creates a payment only from an unexpired checkout quote. Only a paid fact that matches the payment reaches `SUCCEEDED`; on a failed or canceled payment, a matching recovery still succeeds and any other paid fact opens a review case.])

#figure(
  table(
    columns: (1fr, 1fr, 2.6fr),
    table.header([Lifecycle (class and field)], [States], [Transitions]),
    [Order (`Order`, `OrderStatus`)],
    [`PENDING`, `CONFIRMED`, `CANCELLED`],
    [Created `PENDING` when fulfilment is accepted (`Order.create`). `TeamSucceeded` moves the team's orders to `CONFIRMED` (`confirmByDealId`); `TeamExpired` moves them to `CANCELLED` (`cancelByDealId`). Both are final.],

    [Listing (`Product`, `ProductStatus`)],
    [`DRAFT`, `ON_SALE`, `OFF_SALE`],
    [`publish` moves `DRAFT` or `OFF_SALE` to `ON_SALE` when stock and price are above zero; `delist` moves `ON_SALE` to `OFF_SALE`. A draft that never sold can be deleted (soft delete). `revise` and `configureTeamTerms` keep the state; `consumeOneUnit` keeps `ON_SALE` and lowers the stock.],

    [Refund obligation (`biz_refund.status`)],
    [`pending`, `processing`, `succeeded`, `failed`, `review`],
    [Created `pending` at expiry or after a business refusal. `RefundService.processNext` moves a due `pending` or `processing` row to `processing`, `failed`, `review` or `succeeded` by the provider's answer. `RefundService.reconcile` moves a `failed` or `review` row to the same four states. `succeeded` is final.],
  ),
  kind: table,
  caption: [Lifecycles of the order, the listing and the refund obligation. Source: code (`OrderStatus`, `ProductStatus`, `RefundService`, `RefundMapper.xml`).],
) <tbl-lifecycles>

==== Domain events, event enrichment and broker choreography

Our event design (AD-04, #src("backend/docs/adr/0007-cross-service-events-sns-sqs.md")[ADR 0007]) separates in-process domain events from versioned integration events between services. It combines local coordination inside one transaction with broker choreography for fan-out to other services (@fig-d8-domain-events, @fig-d8b-integration-events); @tbl-domain-events lists each event with its publisher, payload and subscribers.

#afig("d8-domain-events", [In-process domain event view of `commerce-svc` (UML component diagram). Every dependency points at the event bus: three application services publish four domain events to it, and five subscribers listen to it before or after the commit, in the order shown. No publisher depends on a subscriber, so a subscriber is added without changing any publisher. The shaded box is the messaging element.])

#afig("d8b-integration-events", [Integration event view from `commerce-svc` to its consumers (UML component diagram). The outbox row commits with the business change in the commerce data store; the relay publishes V1 events to one topic. Each queue depends on the topic it subscribes to, and each consumer on its own queue, so the audit queue was added without changing `commerce-svc`. Shaded boxes are messaging elements.])

#figure(
  table(
    columns: (1.2fr, 0.7fr, 1.3fr, 1.2fr, 2.2fr),
    table.header([Event name], [Scope and version], [Producer aggregate (published by)], [Payload schema], [Subscribers and transaction phase]),
    [`PaymentSucceeded`],
    [Internal],
    [`CheckoutPayment` (`StripeCheckoutService.fulfilOrCompensate`)],
    [`(paymentId, CheckoutQuote, confirmedAt)`],
    [`IntegrationOutboxWriter.onPaymentSucceeded` (`BEFORE_COMMIT`, lowest precedence). `OrderFulfilmentAdapter.fulfil` is not a subscriber: `StripeCheckoutService` calls it through `PaymentBusinessPort` in the same transaction.],

    [`MemberJoined`],
    [Internal],
    [`GroupDeal` (`PaidGroupDealService.joinConfirmed`, join only)],
    [`(dealId, productId, memberUserId, paymentId, sharerUserId, at)`],
    [`ShareCreditSubscriber.onMemberJoined` (`AFTER_COMMIT`, `REQUIRES_NEW` transaction; catches `RuntimeException`, so a credit never rolls back the paid admission; ADR 0003); `OpenDealOfferCacheSubscriber` (`AFTER_COMMIT`, refreshes the open-team cache).],

    [`TeamSucceeded`],
    [Internal],
    [`GroupDeal` (`PaidGroupDealService`)],
    [`(dealId, productId, size, at)`],
    [`OrderLifecycleSubscriber.onTeamSucceeded` (`BEFORE_COMMIT`, `@Order(10)`, confirms the orders); `IntegrationOutboxWriter.onTeamSucceeded` (`BEFORE_COMMIT`, lowest precedence); `OpenDealOfferCacheSubscriber` (`AFTER_COMMIT`, refreshes the open-team cache).],

    [`TeamExpired`],
    [Internal],
    [`GroupDeal` (`GroupDealExpiryService`)],
    [`(dealId, productId, at)`],
    [`OrderLifecycleSubscriber.onTeamExpired` (`BEFORE_COMMIT`, `@Order(10)`, cancels the orders); `TeamExpiryRefundSubscriber` (`BEFORE_COMMIT`, `@Order(20)`, records `biz_refund`); `IntegrationOutboxWriter.onTeamExpired` (lowest precedence); `OpenDealOfferCacheSubscriber` (`AFTER_COMMIT`, refreshes the open-team cache).],

    [`PaymentSucceededV1`],
    [Published, V1],
    [`commerce-svc` (`IntegrationOutboxWriter`)],
    [`(paymentId, intentId, recipientUserId, operation, businessId, confirmedAt)`],
    [Topic, then the `notification` queue, then `notification-svc` (`SqsNotificationEventConsumer`, then `NotificationEventAppService`).],

    [`TeamSucceededV1`],
    [Published, V1],
    [`commerce-svc` (`IntegrationOutboxWriter`)],
    [`(dealId, productId, size, List<UUID> recipientUserIds)`],
    [Topic, then the `notification` queue (`notification-svc`) and the `audit` queue (filter: `eventType` prefix `Team`; no consumer).],

    [`TeamExpiredV1`],
    [Published, V1],
    [`commerce-svc` (`IntegrationOutboxWriter`)],
    [`(dealId, productId, expiredAt, List<UUID> recipientUserIds)`],
    [Topic, then the `notification` queue (`notification-svc`) and the `audit` queue (filter: `eventType` prefix `Team`; no consumer).],
  ),
  kind: table,
  caption: [Internal domain events and published integration events with their publisher, payload and subscribers; V1 is the `version` field of `IntegrationEventEnvelope`. Source: @fig-d8-domain-events, @fig-d8b-integration-events and the code.],
) <tbl-domain-events>

The SNS topic is `blocbuy-swe5001-domain-events`; its queues are `blocbuy-swe5001-notification` and `blocbuy-swe5001-audit`, each with a `-dlq` dead-letter queue (`infra/swe5001/modules/messaging/main.tf`).

Two design principles shape this event pipeline:

1. *Choreography outside the transaction, orchestration inside it.* For the settlement of several aggregates inside `commerce-svc`, we rejected a distributed saga orchestrator (AD-02). Coordination stays inside one local ACID transaction (`OrderFulfilmentAdapter` and the ordered `BEFORE_COMMIT` listeners `@Order(10)` and `@Order(20)`). Once that transaction commits, every reaction in another service follows broker choreography: `commerce-svc` publishes facts to the topic without knowing its consumers, and each consumer subscribes its own queue and DLQ.
2. *Enrich events at the boundary, without personal data.* The internal events `TeamSucceeded` and `TeamExpired` carry only ids such as `dealId` and `productId`, not the members. Publishing them as they are would force `notification-svc` to call `commerce-svc` back for the member list. Instead, `IntegrationOutboxWriter` enriches the event at the context boundary: in the same `BEFORE_COMMIT` step (lowest precedence) it reads `IntegrationRecipientRepository.findActiveMemberIdsByDealId(dealId)` and adds `List<UUID> recipientUserIds` to `TeamSucceededV1` and `TeamExpiredV1`. The payload holds ids and references only: no email, name, payment amount or Stripe token. `notification-svc` therefore handles each event in one local transaction (the `sys_consumed_message` claim and the `biz_notification` insert) with no synchronous call to `commerce-svc` or `identity-svc`. If email or SMS channels are added later, `notification-svc` can resolve contact details through a read port on `identity-svc`, with no change to `commerce-svc`.

==== Platform interaction for producers and consumers, and governance

BlocBuy serves two sides of participants and a growing set of consumer services:

- *Producers.* The platform administrator publishes product listings on sellers' behalf, configures `TeamPricePolicy` and sets `ProductTeamTerms` (UC9). Team leaders and sharers act as demand producers: by paying first to open a team (UC3), sharing an invite link (`ShareInvite`, UC7) and being credited for it (UC12), they bring buyers from outside chat groups to the platform.
- *Consumers.* Members (UC1, UC4, UC8) browse dual-priced listings, join open teams with automatic refund protection and read in-app notifications. On the technical side, `notification-svc` consumes `commerce-svc` facts as events, and the `audit` queue is ready for the next consumer.

Settlement is a reusable module inside `commerce-svc`: `payment` drives it through `PaymentBusinessPort` and `PaymentIntentPort` without importing `order` or `groupbuy` (@tbl-context-map). A new seed reuses it in-process today; extracting it as a service is AISS-02.

*How the platform extends.*
- *A new subscriber needs no producer change.* The `audit` queue (no consumer; demonstrates fan-out, ADR 0007) and its dead-letter queue receive every event whose `eventType` starts with `Team`. They were created with the notification queue on 14 October by two lines of Terraform in `infra/swe5001/modules/messaging/main.tf` (one `consumers` element and one filter line), with no change to `commerce-svc`. A working consumer also needs its checks and its worker; @tbl-seams lists the full edit.
- *Workload-isolated batch path.* Designed but not implemented: heavy batch jobs, such as end-of-day settlement reconciliation, seller payout aggregation or retraining a recommendation model, would read the `audit` queue and run as ECS scheduled tasks or AWS Batch jobs outside `commerce-svc` tasks. They would not compete for its CPU or PostgreSQL connections (AD-07, AD-16; @tbl-workload-compute, §3.2.2).

*How the API contract is governed.*
- *Built.* One OpenAPI 3.1 specification (`contracts/openapi.json`, 31 operations in 27 paths under `/api/v1`) is the contract for all three services. The web client's TypeScript client is generated from it. Tested in CI: a pull request fails when controller mappings drift from the committed specification or expose `/internal/*` (`InternalPathsContractTest`). At run time, the edge routes only public `/api/v1/*` paths, while `/internal/users/*` is reachable only inside the virtual private cloud (VPC) through ECS Service Connect (AD-06, AD-09).
- *Designed for partners.* Designed but not implemented: when third-party merchants or partner apps call BlocBuy directly, an API gateway tier in front of the load balancer will enforce per-partner API keys, usage plans, quotas and rate limits (AISS-04, @tbl-not-built).

==== Ubiquitous language glossary

@tbl-ubiquitous-language defines the terms this report uses, so that requirements, the report and the Java packages share one vocabulary.

#figure(
  table(
    columns: (1.1fr, 0.9fr, 2.1fr, 1.4fr),
    table.header([Term], [Bounded context], [Meaning in BlocBuy], [Code or table]),
    [Seed],
    [Platform],
    [Curated, team-priced product offers published by the platform administrator (§1.3).],
    [`Product`, `TeamPricePolicy`],

    [Producer and consumer],
    [Platform],
    [*Producers:* the platform administrator, team leaders and sharers. *Consumers:* buyers and downstream event subscribers (§1.3).],
    [`ProductController`, `GroupDealController`, `SqsNotificationEventConsumer`],

    [Account and OAuth identity],
    [`identity`],
    [A user account and the Google identity bound to it.],
    [`User`, `OAuthIdentity`, `sys_user`, `sys_user_oauth`],

    [Team terms and team price],
    [`catalog`],
    [The individual price (`originalPrice`) shown beside the team price (`dealPrice`), set by the team size (`targetSize` ≥ 2) and lifetime (`lifetimeSeconds`).],
    [`DualPricingStrategy`, `ProductTeamTerms`, `TeamPricePolicy`],

    [Team price quote versus checkout quote],
    [`catalog` versus `payment`],
    [`TeamPriceQuote` (`catalog`) is an exploratory price read; `CheckoutQuote` (`payment`) is the frozen financial commitment bound to a payment.],
    [`TeamPriceQuote`, `CheckoutQuote`],

    [Purchase intent (`START` or `JOIN`)],
    [`order`],
    [The server-frozen price, product snapshot, deadlines and optional sharer, fixed before the buyer is sent to Stripe.],
    [`PurchaseIntent`, `PurchaseOperation`, `biz_purchase_intent`],

    [Team (`GroupDeal`)],
    [`groupbuy`],
    [A time-boxed group purchase (`OPEN` to `SUCCEEDED` or `EXPIRED`) opened by a paid leader (member one) up to `targetSize`.],
    [`GroupDeal`, `DealStatus`, `biz_group_deal`],

    [Paid member],
    [`groupbuy`],
    [A buyer admitted to a team by a paid join; one seat.],
    [`PaidMember`, `biz_group_member`],

    [Pay-first settlement],
    [`payment`, `order` and `groupbuy`],
    [Turning a verified Stripe webhook into a seat, an `Order` and a stock decrement, or a refund obligation, in one ACID transaction.],
    [`StripeCheckoutService.settle`, `OrderFulfilmentAdapter.fulfil`],

    [Business refusal],
    [`order` and `payment`],
    [A deterministic refusal after payment that commits a refund obligation (`GROUP_REJECTED`, `BUSINESS_EXPIRED`, `BUYER_INACTIVE`, `PRODUCT_UNAVAILABLE`, `INTENT_EXPIRED`, `INTENT_ALREADY_FULFILLED`).],
    [`PaymentBusinessPort.Outcome`, `biz_refund`],

    [Refund records],
    [`payment`],
    [A refund owed to a buyer and its attempts; not an aggregate root.],
    [`biz_refund` (`RefundWork` claim snapshot), `log_refund_attempt`],

    [Review case],
    [`payment`],
    [A payment sent to the platform administrator for review; part of the `CheckoutPayment` aggregate.],
    [`biz_payment_review_case` (`PaymentReviewCase` read model), `CheckoutPaymentRepository.review`],

    [Invite link and share credit],
    [`groupbuy`],
    [A random 128-bit invite link (`ShareInvite`) created by a team member; an attributed paid join records an immutable `ShareCredit` after commit.],
    [`ShareInvite`, `ShareCredit`, `ShareCreditSubscriber`, `biz_share_credit`],

    [Scheduler lease],
    [`blocbuy-bootstrap` (technical)],
    [A lease row in `sys_scheduler_lock` with a `locked_until` time; the row is locked only while a task acquires it. It ensures that only one task runs each of the five scheduled jobs at a time (AD-16).],
    [`LeasedJobRunner`, `sys_scheduler_lock`; jobs `TeamExpirySweepJob`, `PurchaseIntentExpiryJob`, `RefundWorkerJob`, `OutboxRelayJob`, `OutboxRetentionJob`],
  ),
  kind: table,
  caption: [Ubiquitous language: terms, their bounded context and their code. Source: code and the EventStorming study §7.],
) <tbl-ubiquitous-language>
