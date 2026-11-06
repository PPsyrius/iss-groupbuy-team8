#import "../lib.typ": *
#import "../diagrams/d5-physical-deployment.typ": d5
#import "../diagrams/d6-network.typ": d6
#import "../diagrams/d9-data-models.typ": d9-identity, d9-notification, d9-commerce

// AD and AISS numbers come from ../registers.md. Replace each short description with
// context, options, choice, trade-off and consequence (AGENTS.md writing rule 3).
#let ad-table(..rows) = table(
  columns: (auto, 1fr),
  [Identifier], [Description],
  ..rows.pos().map(((id, d)) => ([#id], [#d])).flatten(),
)

= Solution Overview

== Logical Architecture & Design

BlocBuy's logical architecture decomposes a two-sided community group-buying platform into *three deployable services*—two reusable platform services (`identity-svc` and `notification-svc`) and one transactional business application (`commerce-svc`)—connected across four logical tiers by *one synchronous inter-service interface* and an *asynchronous publish–subscribe event bus*. Every logical boundary follows a single governing rule: _split services only where no ACID database transaction crosses the line_ (`AD-02`), while keeping six Domain-Driven Design (DDD) bounded contexts strictly isolated in code and database permissions so internal modules can be extracted mechanically as transaction volume grows (`AD-01`, `AD-03`, `AD-18`).

All logical elements, interfaces, and interactions in Section 3.1 are technology-neutral roles; their concrete AWS product mappings and network placement are defined in Section 3.2.

=== Key Architectural Decisions

Six architectural decisions (`AD-01` to `AD-06` in the canonical register) govern the logical decomposition, data ownership, session propagation, and synchronous versus asynchronous communication between services. Each entry below states the context, the options evaluated (including rejected alternatives), the chosen design, and the resulting trade-offs and limitations.

#ad-table(
  ("AD-01", [
    *Strangler migration from the SWE5006 modular monolith to three deployable services, retaining the monolith as a dual-topology fallback* (#link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008]). \
    *Context:* The same codebase serves two concurrent modules with different grading criteria: SWE5006 requires a stable single-host environment (`dev`) through late October 2026, whereas SWE5001 requires an independently scalable cloud-native microservice platform (`prod` on AWS ECS) within a ~50 person-day team budget. \
    *Options considered:* (a) _Keep a single monolithic deployment for both courses_—rejected because it cannot isolate scaling signals or fault domains between authentication, core checkout, and asynchronous notifications; (b) _Rewrite into six separate source repositories and pipelines_—rejected because duplicating build pipelines and cross-repo contract changes would consume the entire schedule and destabilise `dev`; (c) _Single Maven reactor with assembly-level packaging (`blocbuy.topology=monolith|distributed`)_. \
    *Choice:* Option (c). Business code remains in shared COLA Maven modules (`blocbuy-client` to `blocbuy-infrastructure`), while three thin assembly modules under `backend/services/` (`identity-svc`, `commerce-svc`, `notification-svc`) each package their own entry class (`IdentityServiceApplication`, `CommerceServiceApplication`, `NotificationServiceApplication`), restricted `@ComponentScan`, and `@MapperScan`. \
    *Trade-off & Consequence:* Verified on AWS on 17 October 2026 (promotion #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244")[run 39008208244]): all three services build and deploy independently from one codebase while `dev` stays healthy (`UP`). The cost is a slightly larger container image footprint (shared JAR dependencies) and strict CI discipline (`ArchitectureTest`) to prevent accidental cross-context imports.
  ]),
  ("AD-02", [
    *Split services along ACID transaction boundaries rather than by domain nouns: `identity-svc`, `commerce-svc`, and `notification-svc`* (#link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/backend/docs/adr/0001-payment-business-integration.md")[ADR 0001], #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/backend/docs/adr/0002-team-lifecycle-events.md")[ADR 0002], #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008]). \
    *Context:* EventStorming identified six bounded contexts (`identity`, `catalog`, `groupbuy`, `order`, `payment`, `notification`). However, settling a paid group-buy participation (`StripeCheckoutService.settle` → `OrderFulfilmentAdapter.fulfil`) must atomically verify payment status, lock and admit the buyer into `GroupDeal`, create the `Order`, mark `PurchaseIntent` fulfilled, decrement `Product` stock, and—when the last seat fills—confirm all team orders via `BEFORE_COMMIT` listeners. \
    *Options considered:* (a) _Five or six microservices (one per bounded context) coordinated by a distributed Saga Mediator/Orchestrator_—rejected after estimating 50–55 person-days (100% of team capacity) to build and verify compensating transactions (e.g., automated stock release and Stripe refund rollback on partial failure) across network boundaries; (b) _Split `groupbuy` and `order` into separate services_—rejected because an order's lifecycle (`PENDING → CONFIRMED | CANCELLED`) is wholly governed by the team's state transition in the same transaction; (c) _Co-locate the four transaction-sharing contexts (`catalog`, `groupbuy`, `order`, `payment`) in `commerce-svc`, and extract `identity-svc` and `notification-svc` where no transaction crosses the boundary_. \
    *Choice:* Option (c). \
    *Trade-off & Consequence:* Financial invariants (zero double-charging, zero unrefunded rejections, deterministic last-seat arbitration) are guaranteed by a single local PostgreSQL ACID transaction (`StripeLocalIntegrationTest`, 20 concurrent buyers for 1 seat). The trade-off is a coarser scaling and deployment unit for `commerce-svc`, recorded as `AISS-02` with a planned post-course extraction path for `payment`.
  ]),
  ("AD-03", [
    *Database-per-service logical isolation via dedicated PostgreSQL databases, per-service runtime roles, Flyway grant callbacks, and ArchUnit mapper guards* (#link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008]). \
    *Context:* Microservice autonomy requires that no service read or mutate another service's persistent tables directly, yet SWE5006 and SWE5001 share a single Flyway migration stream (`V1`–`V28`) during active development. \
    *Options considered:* (a) _One shared database schema accessed by all three services with the same credentials_—rejected because SQL joins across contexts (such as the legacy `PaymentAttentionMapper.xml` join on `sys_user`, removed in `#469`) silently couple service deployments; (b) _Three separate physical RDS instances with diverged Flyway streams_—rejected due to 3× database cost and migration drift before SWE5006 completion; (c) _Three logical databases (`identity`, `commerce`, `notification`) on one RDS instance, where each service's runtime role (`*_app`) is granted `SELECT, INSERT, UPDATE, DELETE` only on its owned tables via `afterMigrate__service_grants.sql`_. \
    *Choice:* Option (c), reinforced at build time by `ArchitectureTest.persistenceOwnedBy` and `IdentityTablesStayInIdentityMappersTest`. \
    *Trade-off & Consequence:* Any cross-service SQL query fails immediately in CI and is denied by PostgreSQL privileges at runtime. Empty foreign tables remain present in each database until migration streams are baselined per service after SWE5006, and physical server capacity is shared (`AISS-05`).
  ]),
  ("AD-04", [
    *Asynchronous cross-service integration via Transactional Outbox, Broker Choreography (SNS topic fan-out + per-consumer SQS queues with DLQs), and idempotent inbox consumers* (#link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/backend/docs/adr/0007-cross-service-events-sns-sqs.md")[ADR 0007]). \
    *Context:* When a team succeeds, expires, or settles a payment in `commerce-svc`, downstream platform services (`notification-svc` and future audit/analytics consumers) must react reliably without coupling the checkout transaction to downstream availability or losing events on process crashes. \
    *Options considered:* (a) _Synchronous REST callbacks from `commerce-svc` to `notification-svc` or `AFTER_COMMIT` broker publish without an outbox_—rejected because a crash between database commit and network send drops events (dual-write hazard), and synchronous calls propagate notification latency/outages to checkout; (b) _Central Saga Mediator / Step Functions or always-on Kafka (MSK) / RabbitMQ (Amazon MQ)_—rejected because post-commit notifications require fan-out choreography rather than multi-step orchestration, and always-on brokers violate our ephemeral cost model; (c) _Point-to-point SQS without SNS_—rejected because adding a second consumer would require modifying `commerce-svc`; (d) _`BEFORE_COMMIT` writes to `sys_outbox` + leased `OutboxRelayService` → SNS standard topic → per-consumer SQS queues with `maxReceiveCount = 5` DLQs + `sys_consumed_message` deduplication_. \
    *Choice:* Option (d). \
    *Trade-off & Consequence:* Business state commits and event publication are atomic; adding the `audit` consumer required 2 lines of Terraform and zero changes to `commerce-svc` (verified on AWS on 17 October 2026). The trade-off is eventual consistency with a ~2 s relay polling lag (`AISS-11`, by design) and a calculated single-relay ceiling of ~50 events/s under `LeasedJobRunner`.
  ]),
  ("AD-05", [
    *Shared authentication and immutable RBAC authorization snapshot in the distributed Session Store* (#link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008], `#467`). \
    *Context:* Every authenticated request to `commerce-svc` or `notification-svc` requires token validation and role/permission checks (`buyer`, `admin`). In the original monolith, `StpInterfaceImpl` queried `identity` tables (`sys_role`, `sys_permission`) on every request. \
    *Options considered:* (a) _Synchronous RPC from `commerce-svc` and `notification-svc` to `identity-svc` on every HTTP request_—rejected because it adds network latency to every read/write call and makes `identity-svc` a hard availability bottleneck for browsing and notifications; (b) _Stateless JWTs verified at an external API Gateway or AWS Cognito_—rejected because immediate session revocation on logout and atomic OAuth state handling were already built on Sa-Token; (c) _Shared Valkey session store holding both the Sa-Token login session and a dedicated per-account RBAC snapshot (`${tokenName}:rbac:${loginType}:{${userId}}`) written atomically via Redis Lua with a 120 s refresh fence at login_. \
    *Choice:* Option (c). Distributed assemblies use `SessionRbacAuthorizationService` to memoize one validated snapshot per request without SQL or remote calls. \
    *Trade-off & Consequence:* Zero inter-service RPCs for authentication or RBAC checks across all endpoints. Two trade-offs are accepted: role changes take effect on the user's next login outside `identity-svc`, and a total Session Store outage invalidates active sessions (`AISS-10`).
  ]),
  ("AD-06", [
    *Single synchronous inter-service call (`commerce-svc` → `identity-svc` via `UserAppService`), failing closed on the money path* (#link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008], `#468`, `#469`). \
    *Context:* While browsing and session checks need no synchronous call to `identity-svc`, purchase intent creation, payment settlement (`OrderFulfilmentAdapter.fulfil`), and operator payment-attention views must verify that a buyer account is currently `ACTIVE` (`UserAppService.requireActive`) or resolve public nicknames (`UserAppService.publicNicknames`). \
    *Options considered:* (a) _Replicate `sys_user` asynchronously into `commerce-svc` via events_—rejected for the conduct window because a stale replica could admit a disabled user into a paid team; (b) _Fail open when `identity-svc` is unreachable_—rejected because admitting an unverified or disabled buyer violates payment compliance invariants; (c) _Synchronous HTTP adapter (`RemoteUserAppService`) over internal service discovery (`/internal/users/{id}/require-active` and `/internal/users/public-nicknames`) with strict 500 ms connect / 1,500 ms read timeouts, no retry, and fail-closed semantics_. \
    *Choice:* Option (c). Only HTTP 404 (`IDENTITY_404_001`) and HTTP 403 (`IDENTITY_403_001`) map to business rejections (which trigger an automatic refund obligation); any timeout, 5xx, or route error throws `IdentityUnavailableException` (HTTP 500), rolling back the settlement transaction so Stripe safely retries later. \
    *Trade-off & Consequence:* Keeps user PII strictly inside `identity-svc` and prevents false business refunds during transient network faults (verified locally in the 15 October 2026 identity-outage drill; `AISS-13`). The accepted limitation is that new checkouts and webhook settlements pause while `identity-svc` is down (`AISS-03`).
  ]),
)

=== Tiers and Layers

BlocBuy organises its logical elements along two orthogonal dimensions: * four horizontal system tiers* that separate client delivery, business processing, asynchronous messaging, and data storage across the platform, and *four internal Clean Object-Oriented and Layered Architecture (COLA) layers* inside every service that keep the domain model free of framework and infrastructure dependencies (`AD-18`).

==== System Tiers

#figure(
  table(
    columns: (auto, 1.3fr, 2.2fr, 1.4fr),
    [Tier], [Logical Elements], [Architectural Responsibility], [Communication Style],
    [1. Presentation & Edge Tier],
    [Web SPA (`React 19`), CDN & Edge Gateway],
    [Serves static client assets, terminates public TLS, enforces single-origin routing (`/api/v1/*`), and blocks external access to internal service endpoints (`/internal/*`).],
    [Synchronous HTTPS from browsers and external webhook senders.],

    [2. Application Tier],
    [`identity-svc`, `commerce-svc`, `notification-svc`],
    [Executes stateless application use cases, enforces domain invariants, manages local ACID transactions, and exposes OpenAPI 3.1 REST endpoints.],
    [Synchronous REST from Edge; one internal REST call (`commerce-svc` → `identity-svc`, `AD-06`).],

    [3. Integration Tier],
    [Transactional Outbox Relay, Message Broker Topic, Event Queues (`notification`, `audit`) + DLQs],
    [Decouples post-commit domain facts from downstream consumers via Broker Choreography, providing at-least-once delivery, event-type filtering, and dead-letter isolation.],
    [Asynchronous batch publish (`PublishBatch`) and long-poll queue consumption (`AD-04`).],

    [4. Data Tier],
    [Identity DB, Commerce DB, Notification DB, Shared Session Store],
    [Persists per-service relational aggregates, outbox/inbox technical tables, distributed scheduler leases, and shared Sa-Token authentication/RBAC snapshots.],
    [TCP connection pools (JDBC / Redis protocol) scoped by per-service credentials (`AD-03`).],
  ),
  caption: [Horizontal system tiers and their responsibilities in the logical architecture.],
)

==== Internal Service Layering (Alibaba COLA) and Automated Guardrails

Inside the backend reactor, every bounded context (`identity`, `catalog`, `groupbuy`, `order`, `payment`, `notification`) follows the *Alibaba COLA* package structure across five shared Maven modules plus the deployment assemblies:

1. *Adapter Layer (`blocbuy-adapter`, `..web..` and `..web.internal..`):* Contains `@Validated @RestController` classes (`ProductController`, `GroupDealController`, `StripeWebhookController`, `NotificationController`, `InternalUserController`). Adapters perform HTTP protocol binding, parameter validation, and Sa-Token permission checks, then delegate to application services. They never import persistence classes or MyBatis mappers.
2. *Application Layer (`blocbuy-application`, `..command..`, `..query..`, `..service..`, `..assembler..`):* Orchestrates use-case workflows, manages `@Transactional` boundaries, dispatches and listens to in-process events (`OrderLifecycleSubscriber`, `TeamExpiryRefundSubscriber`, `ShareCreditSubscriber`), and translates domain objects into DTOs/views. It depends only on `blocbuy-domain` ports and `blocbuy-client` contracts.
3. *Domain Layer (`blocbuy-domain`, `..model..`, `..pricing..`, `..event..`, `..repository..`, `..gateway..`, `..exception..`):* Holds aggregate roots, entities, value objects, domain services (`DualPricingStrategy`), domain event records, and repository/gateway port interfaces. By rule, `blocbuy-domain` is *100% framework-free* (pure JDK 21 and Lombok only—no Spring, MyBatis, Jackson, Jakarta Validation, or Sa-Token imports).
4. *Infrastructure Layer (`blocbuy-infrastructure`, `..persistence..`, `..messaging..`, `..outbox..`):* Implements domain repository and gateway ports using MyBatis-Plus data objects (`*DO`), XML/annotation mappers (`*Mapper`), converter classes (`*Converter`), Redis/Valkey templates, Stripe SDK adapters, and AWS SDK v2 SNS/SQS transports (`SnsOutboxEventTransport`, `SqsNotificationEventConsumer`).
5. *Contract & Assembly Modules (`blocbuy-client`, `blocbuy-bootstrap`, `services/*`):* `blocbuy-client` holds versioned cross-service integration event contracts (`IntegrationEventEnvelope`, `TeamSucceededV1`, `TeamExpiredV1`, `PaymentSucceededV1`). `blocbuy-bootstrap` and the three `services/*` modules wire Spring Boot configurations and scheduled jobs (`LeasedJobRunner`).

To prevent architectural erosion as five developers worked in parallel, these layer and service boundaries are enforced on every pull request by *18 automated architecture checks* (13 `@ArchTest` rules in `ArchitectureTest.java` plus 5 contract, SQL-ownership, and assembly-isolation test suites):

#figure(
  table(
    columns: (auto, 1.5fr, 2.3fr),
    [Category], [Enforced Rule / Test Class], [Architectural Boundary Protected],
    [COLA Layer Isolation \ (6 rules)],
    [`domain_is_framework_free` \ `domain_has_no_outward_dependencies` \ `application_uses_ports_not_tech` \ `controllers_never_touch_persistence` \ `persistence_is_internal` \ `no_field_injection`],
    [Forbids Spring/MyBatis/Jackson/Sa-Token in `blocbuy-domain`; forbids outward dependencies from domain to application/adapter/infrastructure; blocks MyBatis/Redis in application services; blocks persistence access from controllers; seals `*DO`/`*Mapper`/`*RepositoryImpl` inside `..persistence..`; mandates constructor injection.],

    [Service Boundary Isolation \ (4 rules)],
    [`commerce_reaches_identity_only_through_user_port` \ `only_identity_reads_current_user` \ `nothing_depends_on_notification` \ `notification_depends_on_no_other_service`],
    [Restricts `commerce` → `identity` calls strictly to the remote-capable `UserAppService` port; forbids `currentUser` outside `identity`; ensures `notification-svc` has zero incoming or outgoing code dependencies on `commerce` or `identity`.],

    [Database & SQL Ownership \ (4 rules)],
    [`identity_persistence_is_owned` \ `commerce_persistence_is_owned` \ `notification_persistence_is_owned` \ `IdentityTablesStayInIdentityMappersTest`],
    [Verifies in bytecode and MyBatis XML files that no service references another service's persistence package or SQL table names (`sys_user*`, `biz_notification`, etc.).],

    [API & Perimeter Guards \ (4 rules)],
    [`controllers_are_validated` \ `numeric_request_params_have_range_constraints` \ `internal_controllers_are_hidden_and_distributed_only` \ `internal_paths_live_in_internal_controllers` + `InternalPathsContractTest`],
    [Requires `@Validated` and numeric bounds on all controllers; requires `@Hidden` and `@ConditionalOnProperty(blocbuy.topology=distributed)` on `..web.internal..` controllers; fails if `/internal/*` leaks into `contracts/openapi.json`.],
  ),
  caption: [Automated architecture rules enforcing COLA layers and microservice boundaries in CI (`./mvnw verify`).],
)

=== Nodes and Subsystems

BlocBuy's logical decomposition into subsystems, components, provided/required interfaces, and data stores is shown in @fig-d3-logical-view (`D3`), redrawn in technology-neutral UML 2.5 component notation to close assessor remark `F12` and `F8 (logical)`.

#figure(
  image("../diagrams/d3-logical-view.svg", width: 100%),
  caption: [Logical view (UML 2.5 component diagram, `D3`). Elements are arranged by logical tier with technology-neutral roles; `commerce-svc` makes only one synchronous inter-service call (`UserStatusPort` in red) and publishes all downstream state transitions asynchronously through the Message Broker.],
) <fig-d3-logical-view>

==== Subsystem and Solution Element Catalogue

Every architecturally significant element in @fig-d3-logical-view is catalogued in @tbl-logical-elements with its stereotype, tier, provider allocation (custom-built vs COTS cloud infrastructure vs external SaaS), and interface contracts.

#figure(
  table(
    columns: (1.2fr, auto, 1fr, 1.3fr, 1.8fr),
    [Logical Element], [Stereotype & Tier], [Provider], [Provided / Required Interfaces], [Responsibility in the Solution],
    [Web SPA],
    [`«component»` \ Presentation],
    [Custom (Team 8, React 19 + Orval client)],
    [*Req:* `AuthAPI`, `CommerceAPI`, `NotificationAPI` (HTTPS)],
    [Single-page browser client for buyers, team leaders, and administrators; generated from `contracts/openapi.json`.],

    [CDN & Edge Gateway],
    [`«component»` \ Presentation / Edge],
    [COTS (Managed Cloud Edge + L7 Router)],
    [*Prov:* Public HTTPS Origin \ *Req:* `AuthAPI`, `CommerceAPI`, `NotificationAPI`, `PaymentWebhookAPI`],
    [Delivers static SPA assets, routes `/api/v1/auth/*` and `/api/v1/users/*` to `identity-svc`, `/api/v1/notifications*` to `notification-svc`, and remaining public paths to `commerce-svc`; blocks `/internal/*`.],

    [`identity-svc`],
    [`«component»` \ Application (`«subsystem»` Platform)],
    [Custom (Team 8, Spring Boot 4 / Java 21)],
    [*Prov:* `AuthAPI` (Public), `UserStatusPort` (Internal) \ *Req:* OAuth 2.0 ACL, `Identity DB`, `Session Store`],
    [Reusable platform service managing Google OAuth 2.0 login, user lifecycle (`ACTIVE`/`DISABLED`), RBAC role snapshots in `Session Store`, and internal user-status checks.],

    [`notification-svc`],
    [`«component»` \ Application (`«subsystem»` Platform)],
    [Custom (Team 8, Spring Boot 4 / Java 21)],
    [*Prov:* `NotificationAPI` (Public) \ *Req:* `EventsV1` (Async Poll), `Notification DB`, `Session Store`],
    [Reusable platform service consuming versioned integration events idempotently (`sys_consumed_message`) to persist and serve in-app buyer notifications.],

    [`commerce-svc`],
    [`«component»` \ Application (`«subsystem»` Business)],
    [Custom (Team 8, Spring Boot 4 / Java 21)],
    [*Prov:* `CommerceAPI`, `PaymentWebhookAPI` \ *Req:* `UserStatusPort` (Sync), `EventsV1` (Async Publish), Payment ACL, `Commerce DB`, `Session Store`],
    [Core business application co-locating `catalog`, `groupbuy`, `order`, and `payment` modules plus `IntegrationOutboxWriter` and leased `OutboxRelayService` (`AD-02`, `AD-04`).],

    [Message Broker & Event Queues (`notification`, `audit`) + DLQs],
    [`«component»` \ Integration],
    [COTS (Managed Cloud Pub/Sub & Queues)],
    [*Prov:* `EventsV1` Topic & Queue Endpoints],
    [Fans out published `IntegrationEventEnvelope<T>` messages by `eventType` filter policy to independent subscriber queues with 5-attempt dead-letter queues.],

    [Identity DB, Commerce DB, Notification DB],
    [`«database»` \ Data],
    [COTS (Managed Relational DB)],
    [*Prov:* SQL / JDBC per-service role access],
    [Durable ACID storage for each service's owned aggregates, outbox (`sys_outbox`), inbox (`sys_consumed_message`), and scheduler lease (`sys_scheduler_lock`).],

    [Session Store],
    [`«database»` \ Data],
    [COTS (Managed In-Memory KV Store)],
    [*Prov:* Redis protocol],
    [Holds single-use OAuth state tokens, Sa-Token login sessions, and per-account RBAC role/permission snapshots (`AD-05`).],

    [OAuth Identity Provider],
    [`«external system»` \ External],
    [External SaaS (Google OAuth 2.0)],
    [*Prov:* OIDC / OAuth 2.0 endpoints],
    [Authenticates buyer and administrator identities during sign-in (`UC2`); isolated behind `OAuthIdentityGateway`.],

    [Payment Gateway],
    [`«external system»` \ External],
    [External SaaS (Stripe Sandbox)],
    [*Prov:* Checkout & Refunds API \ *Req:* `PaymentWebhookAPI`],
    [Processes hosted card checkout sessions, emits signed payment webhooks (`UC3`/`UC4`), and executes asynchronous refunds (`UC6`).],
  ),
  caption: [Catalogue of logical architecture elements, providers, interfaces, and responsibilities.],
) <tbl-logical-elements>

=== Platform Design

BlocBuy is architected not as a single-purpose store, but as a *reusable two-sided group-buying platform* shaped by Domain-Driven Design (`F3`, `F4`, `F9`, `#587`, `#595`, `#741`). Domain modelling proceeded in four traceable stages: (1) a simulated EventStorming study across all use cases to expose domain events and concurrency hot spots; (2) strategic classification of subdomains and bounded contexts linked by explicit context-mapping patterns; (3) transaction-boundary analysis to map six bounded contexts onto three deployable services; and (4) tactical derivation of aggregate roots, state machines, and enriched integration events that allow new producers and consumers to plug in without modifying existing core code.

==== EventStorming Method and Decision Traceability

Because the team operated on an intensive part-time development schedule across two concurrent modules, *we did not conduct a live in-person sticky-note workshop*. Instead, on 25 September 2026 we executed a *structured simulated EventStorming study* (#link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/architecture-options-study/06-eventstorming-ddd.md")[`06-eventstorming-ddd.md`]) following Alberto Brandolini's three-level progression (*Big Picture → Process Level → Software Design*). To ensure every modelled element reflected real system behaviour rather than retrospective invention, the study used six repository artefacts as its inputs: the business proposal (`UC1`–`UC9`), `blocbuy-domain` models and events, `blocbuy-application` services and subscribers, backend `ADRs 0001–0002`, Flyway migrations `V1`–`V17` (later extended to `V28`), and the 23 HTTP operations in `contracts/openapi.json`.

1. *Level 1 — Big Picture & Hot Spots:* We placed 34 chronological domain events across six lifecycle phases (*Listing → Access & Discovery → Pay-First Team Formation → Team Resolution → Refund → Viral Growth*) and identified *four pivotal events* that divide the business lifecycle into distinct acts: `Product Published` (supply exists), `PaymentSucceeded` (buyer money is committed), `Team Started` (an open team window begins), and `TeamSucceeded` / `TeamExpired` (the team's collective fate is sealed). Crucially, annotating the timeline surfaced *10 concurrency and consistency hot spots (`HS-1` to `HS-10`)*—such as `HS-1` (20 buyers paying for the last seat simultaneously), `HS-2` (duplicate or out-of-order Stripe webhooks), `HS-4` (the expiry sweeper racing a settling webhook on the same team), and `HS-8` (order confirmation and refund recording running `BEFORE_COMMIT` inside the team transition transaction).
2. *Level 2 — Process Level (`UC3`–`UC6` Timeline):* For each core flow we traced the exact causal chain *Read Model → Actor/Time Trigger → Command → Aggregate → Domain Event → Policy (`BEFORE_COMMIT` / `AFTER_COMMIT` / Outbox) → Next Command*, illustrated in @fig-d4a-timeline.
3. *Level 3 — Software Design & Grouping Test:* We clustered aggregates and policies around the hot spots and tested whether `groupbuy` and `order` could be separated into distinct services—proving from `HS-1` and `HS-8` that they share a single consistency boundary.

#figure(
  image("../diagrams/d4a-eventstorming-timeline.svg", width: 100%),
  caption: [EventStorming process-level timeline for `UC3`–`UC6` (`d4a-eventstorming-timeline.svg`). Pivotal Java event records (`PaymentSucceeded`, `MemberJoined`, `TeamSucceeded`, `TeamExpired`) and hot spots (`HS-1`, `HS-2`, `HS-4`, `HS-8`) directly determined the transaction boundaries and outbox choreography.],
) <fig-d4a-timeline>

@tbl-es-traceability traces how each major EventStorming finding drove a concrete architectural decision and code artefact (`#595`).

#figure(
  table(
    columns: (1.1fr, 1.6fr, 1.6fr, 1.2fr),
    [EventStorming Finding], [Architectural Problem Exposed], [Design Decision Driven], [Recorded Artefact & Code],
    [Pivotal Event `PaymentSucceeded` + `HS-8` (`BEFORE_COMMIT` consequences)],
    [Confirming payment, admitting the member, creating the order, and taking stock must either all commit or all roll back.],
    [Keep `catalog`, `groupbuy`, `order`, and `payment` in one deployable (`commerce-svc`, `AD-02`) instead of a 5-service Saga.],
    [ADR 0001, ADR 0002, ADR 0008; `OrderFulfilmentAdapter.fulfil`],

    [`HS-1` (Last-slot race) & `HS-4` (Sweeper vs webhook race)],
    [Concurrent webhook threads and the 60 s expiry job contend on the same `GroupDeal` row.],
    [Enforce a global pessimistic lock order (`GroupDeal` → `PurchaseIntent` → `CheckoutPayment`) and status-guarded updates.],
    [ADR 0001; `OrderFulfilmentAdapter.lockForSettlement`, `StripeCheckoutService.settle`],

    [`HS-2` (Duplicate / replayed Stripe webhooks)],
    [At-least-once webhook delivery can replay a settled event across multiple threads.],
    [Idempotent event claim (`log_payment_webhook_event`), `ParticipationReceipt`, and unique index `uq_biz_order_payment_id`.],
    [ADR 0001; `StripeLocalIntegrationTest` (8-thread replay suite)],

    [Pivotal Events `TeamSucceeded` / `TeamExpired` → external consumers],
    [Downstream notification and analytics services need team outcomes without coupling to the checkout transaction.],
    [Insert enriched `V1` events into `sys_outbox` at `BEFORE_COMMIT` (`Ordered.LOWEST_PRECEDENCE`) and relay via SNS → SQS (`AD-04`).],
    [ADR 0007; `IntegrationOutboxWriter`, `OutboxRelayService`],

    [`MemberJoined` → `UC7` Share Attribution],
    [Crediting a referrer is non-financial; a credit failure must never roll back a paid buyer's seat.],
    [Publish `MemberJoined` in-process and handle credit insertion in an `AFTER_COMMIT` (`REQUIRES_NEW`) subscriber that catches all exceptions.],
    [ADR 0003; `ShareCreditSubscriber`, `ShareInvite`, `ShareCredit`],

    [`HS-6` (Stripe 24 h idempotency expiry on refunds)],
    [Retrying a failed refund with a new key after 24 hours risks double-refunding the buyer.],
    [Execute refunds asynchronously outside DB transactions (`RefundWorkerJob`); after 23 h, search only and route to operator `review`.],
    [ADR 0002; `RefundService.processNext`, `PaymentReviewCase`],
  ),
  caption: [Traceability from EventStorming findings to architectural decisions and implementation artefacts (`#595`).],
) <tbl-es-traceability>

==== Subdomain Classification and Build-versus-Buy Strategy

Using the strategic DDD lens, we partitioned BlocBuy's business capabilities into *Core*, *Supporting*, and *Generic* subdomains (@tbl-subdomains) to focus custom engineering effort on the platform's competitive differentiators while buying commodity capabilities via external SaaS or managed cloud services.

#figure(
  table(
    columns: (1.3fr, auto, auto, 1.5fr, 1.5fr),
    [Subdomain], [Classification], [Build vs Buy], [Strategic Rationale], [Implementation Package / Provider],
    [Team Formation & Resolution],
    [*Core*],
    [Custom Build],
    [Primary platform differentiator (the _seed_): time-boxed quota enforcement, last-seat fairness, and collective team fate.],
    [`com.blocbuy.groupbuy` (`commerce-svc`)],

    [Team Purchase Settlement],
    [*Core*],
    [Custom Build],
    [Pay-first trust guarantee: every verified payment deterministically yields either one seat + one order, or a durable refund obligation.],
    [`com.blocbuy.order` + `groupbuy` (`commerce-svc`)],

    [Share Attribution & Viral Growth (`UC7`)],
    [*Core*],
    [Custom Build],
    [Drives the platform's buyer-led network effect by minting unguessable 128-bit invite links (`ShareInvite`) and attributing joins (`ShareCredit`).],
    [`com.blocbuy.groupbuy` (`ShareInviteAppService`, `ShareCreditSubscriber`)],

    [Catalog & Dual Pricing],
    [*Supporting*],
    [Custom Build + SaaS ACL],
    [Supports the core seed with individual-vs-team `DualPricingStrategy` and `ProductTeamTerms`; semantic search reranking is delegated to an optional external service.],
    [`com.blocbuy.catalog` (`commerce-svc`) + TypeSafe Reranker (`ListingSearchReranker` ACL)],

    [Payment & Refund Orchestration],
    [*Supporting*],
    [Hybrid (Custom Rules + SaaS Stripe)],
    [Escrow-like business rules (frozen `CheckoutQuote`, expiry refund obligations, 23 h review guard) are custom; PCI card processing and money movement are bought.],
    [`com.blocbuy.payment` (`commerce-svc`) + Stripe Checkout & Refunds API (`CheckoutGateway`, `RefundGateway`)],

    [Identity & Access],
    [*Generic*],
    [Hybrid (Custom RBAC + SaaS Google)],
    [Standard authentication and role-based access control; credential verification is delegated to Google OAuth 2.0 while session/RBAC snapshots use Sa-Token.],
    [`com.blocbuy.identity` (`identity-svc`) + Google OAuth 2.0 (`OAuthIdentityGateway`)],

    [Notification],
    [*Generic*],
    [Custom Build on Managed Queues],
    [Standard event-driven inbox delivery; built as an independent platform service on managed SNS/SQS to prove decoupled consumer scaling.],
    [`com.blocbuy.notification` (`notification-svc`)],
  ),
  caption: [DDD subdomain classification and build-versus-buy strategy.],
) <tbl-subdomains>

==== Context Mapping (`D4`) and Service Decomposition

*How context mapping was conducted (`F3`):* We identified bounded context boundaries by testing where *ubiquitous language terms changed meaning*, where *data ownership could be isolated without foreign keys*, and where *external models needed translation*:
1. *Linguistic boundary test:* A "quote" in `catalog` (`TeamPriceQuote`) is an exploratory price calculation for a browsing buyer that holds no stock and expires without side effects; in `payment` (`CheckoutQuote`), a "quote" is an immutable financial commitment bound to a `PurchaseIntent` and a Stripe session. Similarly, `GroupDeal` in `groupbuy` models collective quota and window rules, whereas `Order` in `order` models an individual buyer's commercial receipt and product snapshot.
2. *Upstream/downstream dependency analysis:* For every pair of collaborating contexts and external providers, we determined which side dictates the contract (*Upstream, `U`*) and which side adapts (*Downstream, `D`*), assigning the appropriate DDD strategic pattern in @fig-d4-context-map (`D4`) and @tbl-context-map.

#figure(
  image("../diagrams/d4-context-map.svg", width: 100%),
  caption: [DDD Strategic Context Map (`D4`). Dashed outer boxes show the three deployed services (`identity-svc`, `commerce-svc`, `notification-svc`) and the `audit` subscriber; solid inner boxes show the six bounded contexts and the `common` Shared Kernel. Every relationship edge carries its Upstream (`U`) / Downstream (`D`) direction and DDD pattern.],
) <fig-d4-context-map>

#figure(
  table(
    columns: (1.3fr, 1.1fr, 1.4fr, 1.8fr),
    [Upstream (`U`) → Downstream (`D`)], [DDD Context Map Pattern], [Code / Runtime Mechanism], [Rationale for Pattern Choice],
    [Google OAuth 2.0 (`U`) → Identity (`D`)],
    [*Anti-Corruption Layer (ACL)*],
    [`OAuthIdentityGateway` port implemented by Google HTTP adapter in `identity` infrastructure.],
    [Prevents external Google token schemas and OAuth protocol details from leaking into the `User` aggregate.],

    [Stripe (`U`) → Payment (`D`)],
    [*Anti-Corruption Layer (ACL)*],
    [`CheckoutGateway`, `PaymentGateway`, `RefundGateway`; webhook signatures verified and translated into domain `CheckoutGateway.Session`.],
    [Isolates Stripe SDK objects and webhook payloads inside `payment` infrastructure; domain logic sees only `CheckoutPayment` and `RefundWork`.],

    [TypeSafe Reranker (`U`) → Catalog (`D`)],
    [*Anti-Corruption Layer (ACL, fail-open)*],
    [`ListingSearchReranker` port with fallback to PostgreSQL deterministic ordering.],
    [Treats external semantic search as an optional enhancement that never blocks product browsing on failure.],

    [Identity (`U`) → Ordering & Payment in `commerce-svc` (`D`)],
    [*Customer–Supplier* + *Open Host Service (OHS)*],
    [`UserAppService` (`requireActive`, `publicNicknames`) served over `/internal/users/*` (`RemoteUserAppService`) + shared Valkey RBAC session (`AD-05`, `AD-06`).],
    [`identity-svc` exposes a narrow, standardized internal REST protocol and session snapshot so `commerce-svc` never touches `sys_user` tables.],

    [Catalog (`U`) → Ordering (`D`)],
    [*Customer–Supplier*],
    [`CatalogPurchaseSupport` (`priceStart`, `requirePurchasable`, `consumeOneUnit`).],
    [`order` depends on `catalog` to freeze product snapshots, validate `ON_SALE` status, and decrement stock atomically during settlement.],

    [GroupBuy (`U`) → Catalog (`D`)],
    [*Customer–Supplier*],
    [`GroupDealQueryService` translating active teams into `GroupDealOffer` value objects for `DualPricingStrategy`.],
    [Allows catalog listings to display live open-team prices without exposing `GroupDeal` internals to `catalog`.],

    [GroupBuy ↔ Ordering (within `commerce-svc`)],
    [*Partnership* (Shared Consistency Boundary)],
    [`GroupDealPurchaseSupport`, `OrderFulfilmentAdapter`, and `BEFORE_COMMIT` listeners (`OrderLifecycleSubscriber` at `@Order(10)`).],
    [Both contexts co-evolve inside the same local ACID transaction: a seat admission and its `Order` must succeed or roll back as one unit (`AD-02`).],

    [Payment (`U`) → Ordering (`D`)],
    [*Conformist* (Port Inversion)],
    [Payment defines `PaymentIntentPort` and `PaymentBusinessPort`; `OrderFulfilmentAdapter` conforms by implementing them.],
    [Keeps `payment` business-agnostic (reusable for future platform seeds): `payment` drives settlement through abstract ports without importing `order` or `groupbuy`.],

    [GroupBuy (`U`) → Payment (`D`)],
    [*Published Language* (In-Process)],
    [`TeamExpired` domain event record consumed `BEFORE_COMMIT` by `TeamExpiryRefundSubscriber` (`@Order(20)`).],
    [Decouples team expiry logic from refund persistence while guaranteeing that `DealStatus.EXPIRED` and `biz_refund` rows commit atomically.],

    [Commerce (`U`) → Notification & Audit (`D`)],
    [*Published Language (PL)* over Event Bus],
    [`IntegrationEventEnvelope<T>` (`TeamSucceededV1`, `TeamExpiredV1`, `PaymentSucceededV1`) via `sys_outbox` → SNS → SQS (`AD-04`).],
    [Versioned, PII-free JSON contracts allow `notification-svc` and future subscribers (such as the `audit` queue) to consume business facts with zero coupling to `commerce-svc`.],

    [`common` ↔ All Contexts],
    [*Shared Kernel*],
    [`com.blocbuy.common` (`ErrorCode`, `BizException`, `UuidV7`, `PageQuery`, `RoleCode`, `LeasedJobRunner`).],
    [Kept deliberately minimal (cross-cutting technical primitives only; zero business entities) so changes do not ripple across domain models.],
  ),
  caption: [DDD Context Map relationships, patterns, mechanisms, and rationale across all bounded contexts (`F3`).],
) <tbl-context-map>

==== Tactical Design: Aggregate Derivation (`F4`) and Lifecycles (`D7`)

*How aggregates were derived (`F4`):* Rather than mapping one aggregate per database table, we derived every aggregate root by applying a four-step tactical DDD test:
1. *Identify the Commands and the True Business Invariant:* We listed every state-mutating command from EventStorming Level 2 and asked: *"What business rule must hold true immediately when the database transaction commits, even under concurrent requests?"* (For example: a `GroupDeal` must never exceed `targetSize`, must never admit the same buyer twice, and must transition to `SUCCEEDED` on the exact transaction that fills the final seat.)
2. *Draw the Minimal Consistency Boundary:* Objects that must change together to protect that invariant—such as `PaidMember` (`biz_group_member`) and `ParticipationReceipt` (`biz_group_participation_receipt`) inside `GroupDeal`, or `OAuthIdentity` (`sys_user_oauth`) inside `User`—are encapsulated as internal entities or value objects owned by the aggregate root.
3. *Enforce Reference-by-ID Across Aggregates:* No aggregate holds an object reference to another aggregate root. `GroupDeal` references `Product` by `UUID productId`; `Order` references `GroupDeal` by `UUID dealId` and `CheckoutPayment` by `UUID paymentId`; `RefundWork` references `CheckoutPayment` by `UUID paymentId`. Furthermore, by team DDL convention, PostgreSQL tables use *no foreign keys or `CHECK` constraints*; referential integrity is enforced by application services and targeted unique indexes (Section 3.2.3).
4. *Explicitly Govern the Shared Settlement Transaction:* In strict single-aggregate DDD, one transaction modifies only one aggregate root. In BlocBuy, EventStorming hot spot `HS-8` proved that splitting paid participation settlement across four separate transactions would expose buyers to money-safety hazards (paid but no seat, or seat granted without stock). We therefore recorded a deliberate, bounded architectural exception (`AD-02`, #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/backend/docs/adr/0001-payment-business-integration.md")[ADR 0001]): `StripeCheckoutService.settle` and `OrderFulfilmentAdapter.fulfil` coordinate `CheckoutPayment`, `GroupDeal`, `PurchaseIntent`, `Order`, and `Product` stock inside *one local PostgreSQL transaction*, acquiring pessimistic `SELECT ... FOR UPDATE` row locks in a strict global order—*`GroupDeal` (team) → `PurchaseIntent` → `CheckoutPayment` (payment)*—before executing any writes. This deterministic lock hierarchy prevents deadlocks when multiple buyers race for the last seat (`HS-1`) or when the expiry sweeper races a webhook (`HS-4`).

@tbl-aggregates presents the complete derivation of all nine aggregate roots across the six bounded contexts (`F4`), and @fig-d7-aggregates (`D7`) illustrates their tactical class structures and lifecycle state machines (`F9`).

#figure(
  table(
    columns: (0.9fr, 1.1fr, 1.5fr, 1.2fr, 1.1fr),
    [Aggregate Root \ (Context)], [Internal Entities & Value Objects], [Commands Handled], [Protected Business Invariants], [Consistency Boundary & Emitted Events],
    [`User` \ (`identity`)],
    [*Entity:* `OAuthIdentity` \ *VOs:* `UserStatus`, `RegisterSource`, `OAuthProvider`, `UserProfile`],
    [`registerViaOAuth`, `touchLogin`, `ensureActive`, `disable`],
    [Email is unique across accounts; a `DISABLED` user cannot open a session or settle a purchase; OAuth provider tokens never enter the domain model.],
    [1 local txn on `identity` DB (`sys_user`, `sys_user_oauth`).],

    [`Product` \ (`catalog`)],
    [*VOs:* `ProductTeamTerms`, `ListingDetails`, `ProductStatus`, `PricingMode`, `TeamTermsChange`],
    [`draft`, `revise`, `publish`, `delist`, `requireDeletable`, `consumeOneUnit`],
    [Only `ON_SALE` products are browsable/purchasable; published commercial terms (`originalPrice`, currency, stock) cannot be edited via `revise`; only a never-published draft is deletable; stock ≥ 0 (optimistic `version`).],
    [Own txn for admin CRUD (`biz_product`); participates in settlement txn for atomic `consumeOneUnit`.],

    [`TeamPricePolicy` \ (`catalog`)],
    [*VOs:* `TeamPriceQuote`, `ListingPrice`, `GroupDealOffer`],
    [`configurePolicy`, `requireQuotable`],
    [Team price must be strictly lower than `Product.originalPrice`; policy revisions are append-only within `[validFrom, validUntil]`.],
    [1 local txn on `commerce` DB (`biz_team_price_policy`, `biz_team_price_quote`).],

    [`GroupDeal` \ (`groupbuy`)],
    [*Entities:* `PaidMember`, `ParticipationReceipt` \ *VOs:* `DealStatus`, `PaidParticipation`, `NewDealTerms`, `ShareInvite`, `ShareCredit` \ #pending[Eric to confirm: in the code they have their own repositories (3.2.3); also D4, D7.]],
    [`PaidGroupDealCreator.initiate` (startPaid), `joinPaid`, `expire`, `ShareInvite.mint`],
    [`targetSize ≥ 2`; `1 ≤ currentSize ≤ targetSize`; leader is member 1 and cannot re-join; 1 seat per buyer; admission only while `startTime ≤ now < expireTime` on DB clock; filling last seat sets `SUCCEEDED` atomically; self-referral forbidden.],
    [Locked first (`FOR UPDATE`) in settlement and expiry txns (`biz_group_deal`, `biz_group_member`, `biz_group_participation_receipt`). \ *Events:* `MemberJoined`, `TeamSucceeded`, `TeamExpired`.],

    [`PurchaseIntent` \ (`order`)],
    [*VOs:* `PurchaseOperation` (`START` | `JOIN`), `PurchaseQuoteWindow`],
    [`issue` (`POST /payments/intents`), `fulfilBy`, `requireOwner`, `expiredAt`],
    [Owned by a single buyer (mismatch returns 404); freezes server-side price, team size, and sharer; consumed by at most one payment (`fulfilledPaymentId` partial unique index); expires at `min(quoteValidUntil, businessDeadline)`.],
    [Own txn at quote creation; locked second (`FOR UPDATE`) during settlement (`biz_purchase_intent`).],

    [`Order` \ (`order`)],
    [*VO:* `OrderStatus` (`PENDING` | `CONFIRMED` | `CANCELLED`)],
    [`Order.create`, `confirm`, `cancel`],
    [Created only inside a verified settlement transaction (never directly from browser); exactly 1 order per payment (`uq_biz_order_payment_id`); lifecycle transitions (`PENDING → CONFIRMED | CANCELLED`) driven strictly by team outcome.],
    [Written in settlement txn and `BEFORE_COMMIT` subscriber (`@Order(10)`) on `biz_order`.],

    [`CheckoutPayment` \ (`payment`)],
    [*VO:* `CheckoutQuote` (`intentId`, `userId`, `operation`, `businessId`, `amount`, deadlines)],
    [`bind` (checkout session), `succeed`, `cancelUnpaid`, `failTerminalUnpaid`],
    [Payment amount comes exclusively from frozen `CheckoutQuote`; at most 1 pending payment per intent; provider references cannot be rebound after confirmation; webhook applied only after signature check and `log_payment_webhook_event` claim.],
    [Stripe I/O runs outside DB txn; locked third (`FOR UPDATE`) during settlement (`biz_payment`). \ *Event:* `PaymentSucceeded`.],

    [`RefundWork` & `PaymentReviewCase` \ (`payment`)],
    [*Fields:* `paymentId`, `amount`, `status`, `version` (fencing token), `reason`],
    [`recordExpiredTeam`, `processNext`, `reconcile`, `openReviewCase`],
    [At most 1 refund obligation per payment (`UNIQUE(payment_id)`); no new Stripe refund created after 23 h idempotency window (switches to search-only + operator `review`).],
    [Obligation recorded in settlement/expiry txn (`biz_refund`); Stripe Refund API called outside DB txn by `RefundWorkerJob`.],

    [`Notification` \ (`notification`)],
    [*VOs:* `NotificationType`, `NotificationEvent`],
    [`NotificationEventAppService.handle`],
    [Immutable once created (title ≤ 128 chars, content ≤ 1,024 chars); idempotent per `(event_id, consumer)` in `sys_consumed_message` so duplicate SQS deliveries never duplicate buyer notices.],
    [1 local ACID txn per SQS message on `notification` DB (`sys_consumed_message` + `biz_notification`).],
  ),
  caption: [Derivation of all nine aggregates from commands, protected invariants, and consistency boundaries (`F4`).],
) <tbl-aggregates>

#figure(
  image("../diagrams/d7-aggregates.svg", width: 100%),
  caption: [Tactical aggregate boundaries and lifecycle state machines (`D7`). Left: aggregate roots, internal entities, value objects, and ID-only cross-aggregate references. Right: state machines for `GroupDeal` (`DealStatus`), `PurchaseIntent`, and `Order` (`OrderStatus`).],
) <fig-d7-aggregates>

As shown in @fig-d7-aggregates (b), `GroupDeal` enforces a single-entry state machine (`DealStatus.canTransitionTo`): only `OPEN` can transition to a terminal state (`SUCCEEDED` when `currentSize == targetSize`, or `EXPIRED` when the database clock passes `expireTime`), and terminal states never re-enter. While the `DealStatus` enum matrix also permits `OPEN → FAILED` and `OPEN → CANCELED` for future administrative cancellation, no code path triggers those two states in the current release.

==== Domain Events, Boundary Event Enrichment, and Broker Choreography (`D8`)

BlocBuy's event architecture (`AD-04`, #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/backend/docs/adr/0007-cross-service-events-sns-sqs.md")[ADR 0007], `#741`) explicitly separates *in-process domain events* from *versioned cross-service integration events*, and combines *in-transaction local coordination* with *Broker Topology (Choreography)* for cross-service fan-out (@fig-d8-domain-events, `D8`).

#figure(
  image("../diagrams/d8-domain-events.svg", width: 100%),
  caption: [Domain event and integration event flow (`D8`, UML component view). Inside `commerce-svc`, `BEFORE_COMMIT` subscribers enforce immediate consistency for orders, refund obligations, and `sys_outbox` writes; across services, `OutboxRelayService` and the SNS/SQS Broker Topology deliver versioned `V1` events to `notification-svc` and `audit`.],
) <fig-d8-domain-events>

#figure(
  table(
    columns: (1.1fr, auto, 0.9fr, 1.4fr, 1.5fr),
    [Event Name], [Scope & Version], [Producer Aggregate], [Payload Schema], [Subscribers & Transaction Phase],
    [`PaymentSucceeded`],
    [Internal record],
    [`CheckoutPayment` (`payment`)],
    [`(paymentId, CheckoutQuote, confirmedAt)`],
    [`OrderFulfilmentAdapter.fulfil` (same ACID txn); `IntegrationOutboxWriter.onPaymentSucceeded` (`BEFORE_COMMIT`, `@Order(LOWEST)`).],

    [`MemberJoined`],
    [Internal record],
    [`GroupDeal` (`groupbuy`)],
    [`(dealId, productId, memberUserId, paymentId, sharerUserId, at)`],
    [`ShareCreditSubscriber.onMemberJoined` (`AFTER_COMMIT`, `REQUIRES_NEW` txn, catches all errors so credit never rolls back paid admission; ADR 0003).],

    [`TeamSucceeded`],
    [Internal record],
    [`GroupDeal` (`groupbuy`)],
    [`(dealId, productId, size, at)`],
    [`OrderLifecycleSubscriber.onTeamSucceeded` (`BEFORE_COMMIT`, `@Order(10)` → confirms orders); `IntegrationOutboxWriter.onTeamSucceeded` (`BEFORE_COMMIT`, `@Order(LOWEST)`).],

    [`TeamExpired`],
    [Internal record],
    [`GroupDeal` (`groupbuy`)],
    [`(dealId, productId, at)`],
    [`OrderLifecycleSubscriber.onTeamExpired` (`BEFORE_COMMIT`, `@Order(10)` → cancels orders); `TeamExpiryRefundSubscriber` (`BEFORE_COMMIT`, `@Order(20)` → records `biz_refund`); `IntegrationOutboxWriter.onTeamExpired` (`@Order(LOWEST)`).],

    [`PaymentSucceededV1`],
    [Published (`v1` in `IntegrationEventEnvelope`)],
    [`commerce-svc` (`IntegrationOutboxWriter`)],
    [`(paymentId, intentId, recipientUserId, operation, businessId, confirmedAt)`],
    [SNS topic → `notification` SQS queue → `notification-svc` (`SqsNotificationEventConsumer` → `NotificationEventAppService`).],

    [`TeamSucceededV1`],
    [Published (`v1` in `IntegrationEventEnvelope`)],
    [`commerce-svc` (`IntegrationOutboxWriter`)],
    [`(dealId, productId, size, List<UUID> recipientUserIds)`],
    [SNS topic → `notification` SQS queue (`notification-svc`) *and* `audit` SQS queue (filtered fan-out).],

    [`TeamExpiredV1`],
    [Published (`v1` in `IntegrationEventEnvelope`)],
    [`commerce-svc` (`IntegrationOutboxWriter`)],
    [`(dealId, productId, expiredAt, List<UUID> recipientUserIds)`],
    [SNS topic → `notification` SQS queue (`notification-svc`) *and* `audit` SQS queue (filtered fan-out).],
  ),
  caption: [Catalogue of internal domain events and published cross-service integration events (`D8`).],
) <tbl-domain-events>

Two courseware design principles shape this event pipeline (`#741`):

1. *Broker Topology (Choreography) vs Mediator Topology (Orchestration):* For the multi-aggregate financial settlement inside `commerce-svc`, we rejected a distributed Saga Mediator (`AD-02`) and kept orchestration inside a single local ACID transaction (`OrderFulfilmentAdapter` + deterministic `@Order(10, 20)` `BEFORE_COMMIT` listeners). Conversely, once the local transaction commits, all downstream reactions (`notification-svc`, the `audit` queue, and future analytics) follow a pure *Broker Topology (Choreography)*: `commerce-svc` publishes domain facts to the SNS topic without knowing which consumers exist, and each consumer independently subscribes its own SQS queue and DLQ.
2. *Boundary Event Enrichment without PII Leakage:* Internal domain events (`TeamSucceeded` and `TeamExpired`) carry only `(dealId, productId, ...)`—they do not list the team's member IDs. If `commerce-svc` published those raw records directly, `notification-svc` would have to make a synchronous callback to `commerce-svc` to query team membership, re-introducing tight runtime coupling. Instead, `IntegrationOutboxWriter` performs *Event Enrichment at the bounded-context boundary*: inside the same `BEFORE_COMMIT` transaction (`@Order(Ordered.LOWEST_PRECEDENCE)`), it queries `IntegrationRecipientRepository.findActiveMemberIdsByDealId(dealId)` and embeds `List<UUID> recipientUserIds` into `TeamSucceededV1` and `TeamExpiredV1`. At the same time, the payload carries *UUIDs and references only*—never emails, names, payment amounts, or Stripe tokens. Because `notification-svc` delivers in-app notifications (`biz_notification` keyed by `user_id`), it processes every event in one local transaction (`sys_consumed_message` claim + `biz_notification` insert) with *zero synchronous callbacks* to `commerce-svc` or `identity-svc`. If external Email or SMS channels are added in a future phase, `notification-svc` can resolve contact details via a dedicated read-through port on `identity-svc` or encrypted notification tokens without altering `commerce-svc`.

==== Platform Interaction for Producers and Consumers, Measured Reuse, and Governance

BlocBuy's platform architecture serves two external participant sides and an expandable set of internal/external service consumers (`Q2`, `Q3`, `#741`):

- *Producers (Supply & Demand Creators):* Sellers and administrators publish product listings, configure `TeamPricePolicy`, and seed `ProductTeamTerms` (`UC9`). Equally important, *team leaders act as demand producers*: by paying first to open a team (`UC3`) and minting a cryptographic `ShareInvite` link (`UC7`), they recruit buyers from external chat groups into the platform, driving its viral network effect.
- *Consumers (Buyers & Event Subscribers):* Joining buyers (`UC1`, `UC4`, `UC8`) browse dual-priced listings, join open teams with automatic refund protection, and read in-app notifications. On the technical plane, downstream services (`notification-svc` and the `audit` queue's reader) act as *event consumers* that derive new user-facing or analytical value from `commerce-svc` facts.

*Demonstrated platform extensibility (measured reuse):*
- *Zero-code event subscriber onboarding:* Adding the `audit` queue and its dead-letter queue to capture all `TeamSucceeded` and `TeamExpired` facts required *2 lines of Terraform configuration* in `infra/swe5001/modules/messaging/main.tf` (PR `\#487`) and *zero code or configuration changes* in `commerce-svc` (verified on AWS on 17 October 2026; detailed in Section 4.4).
- *Workload-isolated path for future high-compute and batch consumers (`#741`):* Because every team and payment fact is fanned out through SNS to independent SQS queues, future *compute-intensive or long-running batch workloads*—such as end-of-day financial settlement reconciliation, seller payout aggregation, or machine-learning deal-recommendation retraining that would exceed serverless FaaS 15-minute execution limits—can read the `audit` queue and run as isolated *ECS Scheduled Tasks or AWS Batch jobs* (`AD-07`, `AD-16`). This guarantees that heavy analytical or batch compute never contends with `commerce-svc` checkout tasks for CPU or PostgreSQL connection-pool capacity.

*API contract and perimeter governance boundary:*
- *Implemented and verified in CI/AWS today:* A single OpenAPI 3.1 specification (`contracts/openapi.json`, 23 public operations under `/api/v1`) serves as the contract for all three services. The React SPA's TypeScript client is generated directly from `contracts/openapi.json` via Orval, and CI fails any pull request whose controller mappings drift from the committed spec or expose `/internal/*` (`InternalPathsContractTest`). At runtime, CloudFront and the ALB route only public `/api/v1/*` paths while `/internal/users/*` is reachable solely over internal ECS Service Connect within the VPC (`AD-06`, `AD-09`).
- *Designed for external partner onboarding (not built in current scope):* When third-party merchants or partner apps integrate directly with BlocBuy APIs in a future phase, a dedicated *API Gateway* tier will be placed in front of the ALB to enforce per-partner API keys, usage plans, quota tiers, and per-tenant rate limiting (`AISS-04`, Section 1.4).

==== Ubiquitous Language Glossary

To maintain a single shared vocabulary across product requirements, Typst documentation, and Java packages (`AGENTS.md` writing rule 7), @tbl-ubiquitous-language defines the canonical terms used throughout this report.

#figure(
  table(
    columns: (1.1fr, 0.9fr, 2.1fr, 1.3fr),
    [Ubiquitous Term], [Bounded Context], [Domain Meaning in BlocBuy], [Code / Table Artefact],
    [Seed],
    [Platform],
    [The core two-sided interaction: a time-boxed, pay-first group purchase at a discounted team price.],
    [`GroupDeal` + `PurchaseIntent`],

    [Producer / Consumer],
    [Platform],
    [*Producers:* Sellers/admins posting listings and team leaders recruiting members. *Consumers:* Joining buyers and downstream event subscribers.],
    [`ProductController`, `GroupDealController`, `SqsNotificationEventConsumer`],

    [Dual Pricing & Team Terms],
    [`catalog`],
    [Displaying individual price (`originalPrice`) alongside the team price (`dealPrice`), governed by `targetSize` (≥ 2) and window (`lifetimeSeconds`).],
    [`DualPricingStrategy`, `ProductTeamTerms`, `TeamPricePolicy`],

    [Team Price Quote vs Checkout Quote],
    [`catalog` vs `payment`],
    [`TeamPriceQuote` (`catalog`) is an exploratory price read; `CheckoutQuote` (`payment`) is the frozen financial commitment bound to a payment.],
    [`TeamPriceQuote` vs `CheckoutQuote`],

    [Purchase Intent (`START` / `JOIN`)],
    [`order`],
    [Server-frozen commitment of price, product snapshot, deadlines, and optional sharer before redirecting the buyer to Stripe.],
    [`PurchaseIntent`, `PurchaseOperation`, `biz_purchase_intent`],

    [Team (Group Deal)],
    [`groupbuy`],
    [A time-boxed group purchase (`OPEN → SUCCEEDED | EXPIRED`) opened by a paid leader (`member 1`) up to `targetSize`.],
    [`GroupDeal`, `DealStatus`, `biz_group_deal`, `biz_group_member`],

    [Pay-First Settlement],
    [`payment` + `order` + `groupbuy`],
    [Turning a verified Stripe webhook into a seat, an `Order`, and a stock decrement—or a `RefundWork` obligation—in one ACID transaction.],
    [`StripeCheckoutService.settle`, `OrderFulfilmentAdapter.fulfil`],

    [Business Rejection],
    [`order` + `payment`],
    [A deterministic refusal after payment (`GROUP_REJECTED`, `BUSINESS_EXPIRED`, `BUYER_INACTIVE`, `PRODUCT_UNAVAILABLE`) that commits a refund obligation.],
    [`PaymentBusinessPort.Outcome`, `biz_refund`],

    [Share Invite & Share Credit],
    [`groupbuy`],
    [A 128-bit base64url invite token (`ShareInvite`) minted by a team member; an attributed paid join records an immutable `ShareCredit` post-commit.],
    [`ShareInvite`, `ShareCredit`, `ShareCreditSubscriber`, `biz_share_credit`],

    [Scheduler Lease],
    [`common`],
    [A database row lock with TTL renewal ensuring only one container instance executes a recurring job (`Sweeper`, `RefundWorker`, `OutboxRelay`) at a time.],
    [`LeasedJobRunner`, `sys_scheduler_lock`],
  ),
  caption: [Ubiquitous language glossary mapping domain terms to bounded contexts and code artefacts.],
) <tbl-ubiquitous-language>

== Physical Architecture & Design

BlocBuy runs on AWS in Singapore (`ap-southeast-1`) as three containers on ECS Fargate behind CloudFront and one Application Load Balancer (ALB), with one PostgreSQL server, one Valkey node and one SNS topic with SQS queues. Only the ALB and the NAT instance sit in public subnets. The services run in private application subnets, and the data stores sit in separate data subnets that have no route out of the virtual private cloud (VPC). Everything except the edge (CloudFront and the website bucket) is created from nothing for each session or release and destroyed afterwards (AD-12).

@tbl-logical-physical maps each logical element of @tbl-logical-elements (3.1.3) to the product that implements it. The last three rows have no logical counterpart: they exist only because of where the system runs.

#figure(
  table(
    columns: (1fr, 0.9fr, 1.6fr),
    [Logical element (3.1.3)], [Product], [Configuration],
    [Web SPA], [Amazon S3], [Private bucket readable only by CloudFront (origin access control)],
    [CDN & Edge Gateway (edge)], [Amazon CloudFront], [HTTPS for viewers on the default `*.cloudfront.net` certificate; `/api/*` forwarded uncached to the ALB, everything else served from S3 and cached],
    [CDN & Edge Gateway (routing)], [Application Load Balancer], [Listener on HTTP port 80, reachable only from CloudFront's origin-facing addresses and only with the `X-Origin-Secret` header; path rules choose the service],
    [`identity-svc`], [ECS Fargate service], [ARM64, 0.5 vCPU, 1 GiB; one task],
    [`commerce-svc`], [ECS Fargate service], [ARM64, 0.5 vCPU, 1 GiB; 2 to 6 tasks (1 to 6 in baseline sessions), scaled on ALB requests per target],
    [`notification-svc`], [ECS Fargate service], [ARM64, 0.5 vCPU, 1 GiB; 1 to 4 tasks, scaled on queue backlog],
    [`UserStatusPort` (the one synchronous call)], [ECS Service Connect], [Commerce reaches identity by service name inside the VPC],
    [Identity DB, Commerce DB, Notification DB], [Amazon RDS for PostgreSQL 18], [`db.t4g.micro`, 20 GiB gp3, one zone; three databases (`identity`, `commerce`, `notification`) on one server],
    [Session Store], [Amazon ElastiCache for Valkey 8.0], [`cache.t4g.micro`, one node, TLS and an AUTH token],
    [Message Broker & Event Queues + DLQs], [Amazon SNS and SQS], [One topic `blocbuy-swe5001-domain-events`; queues `blocbuy-swe5001-notification` and `blocbuy-swe5001-audit`, each with a dead-letter queue (DLQ)],
    [OAuth Identity Provider, Payment Gateway], [Google OAuth 2.0, Stripe (external)], [Called from the tasks through the outbound gateway; Stripe's webhook arrives through CloudFront],
    [— (outbound gateway)], [fck-nat on EC2 `t4g.nano`], [One instance with a static network interface and an Elastic IP],
    [— (configuration and secrets)], [SSM Parameter Store], [Under `/blocbuy/swe5001/`; read by the task execution role at start-up],
    [— (monitoring)], [Amazon CloudWatch], [Logs under `/blocbuy/swe5001/*` (7 days), one dashboard, DLQ and queue-age alarms],
  ),
  caption: [Logical elements (3.1.3) and the AWS products that implement them. Configured values from `infra/swe5001` on 4 November 2026.],
) <tbl-logical-physical>

#heading(level: 3, numbering: none, outlined: false)[Deployment view]

#figure(kind: image, scale(72%, reflow: true, d5), caption: [Physical deployment view (UML deployment diagram). Only CloudFront reaches the load balancer, and the only synchronous call between services is commerce → identity (red).]) <fig-d5-deployment>

@fig-d5-deployment shows where each artefact runs and how the nodes talk:

- *Browser traffic* reaches CloudFront over HTTPS. CloudFront serves the single-page application from S3 and forwards `/api/*` to the ALB. Stripe's webhook enters the same way.
- *The ALB* picks the service by path. Identity takes `/api/v1/auth/*` and `/api/v1/users/*`, notification takes `/api/v1/notifications*`, and commerce takes the rest of `/api/*`. A high-priority rule (priority 5; the ALB evaluates lower numbers first) returns 404 for `/internal/*` and `/api/internal/*` before any routing rule matches, so internal endpoints are only reachable inside the VPC.
- *Services* talk to each other only once, commerce → identity through Service Connect (AD-06). Everything else between services goes through events: commerce writes them to its outbox, a relay publishes them to SNS, and SNS delivers copies to the SQS queues that the notification service and the audit consumer read (AD-04).
- *Every task* reads its database and session settings from SSM Parameter Store when it starts. Database passwords never pass through Terraform state (3.2.2).
- *All three services* read the shared login session from Valkey over TLS with an AUTH token (AD-05).
- *Outbound calls* leave through fck-nat: identity exchanges the Google OAuth code, and commerce calls the Stripe API. The same path carries the tasks' calls to AWS APIs; S3 traffic uses the gateway endpoint instead.

#heading(level: 3, numbering: none, outlined: false)[Network and security groups]

#figure(kind: image, block(breakable: false, align(left, d6)), caption: [Network view (AWS architecture style: VPC, zones, subnets and security groups). Three subnet tiers per zone; the data tier's route table has no route out of the VPC.]) <fig-d6-network>

The VPC (`10.50.0.0/16`) spans two availability zones and has three subnet tiers in each zone (@fig-d6-network):

#figure(
  table(
    columns: (auto, auto, auto, 1fr),
    [Tier], [Zone a], [Zone b], [What runs there and how it reaches out],
    [Public], [`10.50.0.0/24`], [`10.50.1.0/24`], [ALB nodes and the fck-nat instance. Default route to the internet gateway],
    [Private application], [`10.50.10.0/24`], [`10.50.11.0/24`], [ECS tasks and the one-off `db-init` task. Default route to the fck-nat network interface; free S3 gateway endpoint for image layers],
    [Data], [`10.50.20.0/24`], [`10.50.21.0/24`], [RDS and ElastiCache subnet groups only. Local VPC route only: no NAT, no internet gateway, no endpoint],
  ),
  caption: [Subnet tiers. Configured in `infra/swe5001/modules/network`; the data tier was added on 4 November 2026 (AD-15).],
) <tbl-subnets>

*Separate data subnets (assessor remark F6).* Until 4 November the database, the cache and the services shared the private application subnets, separated only by security groups. The data stores now have their own subnets and route table (AD-15). This gives two independent barriers:

+ *Routing.* A data subnet has no route out of the VPC, so a compromised or misconfigured data store cannot reach the internet, even through the NAT instance.
+ *Security groups.* RDS accepts TCP 5432 only from the service group and the `db-init` group. Valkey accepts TCP 6379 only from the service group. Neither group allows outbound traffic.

Configured and tested offline, not yet on AWS: module validation rejects data CIDRs that reuse an application or public CIDR, and the `terraform test` run `data_tier_has_its_own_subnets` checks that the two sets are disjoint (19 of 19 runs passed, 4 November 2026). The runtime is rebuilt from nothing each session, so the next session creates the new layout without migrating anything. Until that session records the actual placement, this stays open as AISS-07 and NFR-70.

*Single points in the network.* Each zone has its own private route table, but both point at the single fck-nat instance in zone a. An Auto Scaling group of one replaces a failed instance, but only in zone a, because the static network interface lives there. If zone a fails, the services in zone b lose outbound access (for example to Stripe and Google) until the zone recovers. This is a cost decision (AD-10) and is listed as AISS-01.

=== Key Architectural Decisions

The key architectural decisions taken are as follows. Prices are on-demand list prices for `ap-southeast-1`, from the team's cost estimate of 25 September 2026 (`04-aws-cost-estimate.md`), unless the row says otherwise.

#figure(
  ad-table(
    ("AD-07", [
      *ECS Fargate on ARM64 (Graviton), not EKS, App Runner or Lambda.* \
      *Context:* three long-running Spring Boot services that need per-service scaling and private networking. \
      *Options considered:* EKS (Kubernetes), App Runner, Lambda, ECS on Fargate. \
      *Choice:* ECS on Fargate, ARM64 images. \
      *Trade-off:* no cluster to run or patch, and ARM64 is about 20% cheaper per vCPU-hour than x86 (estimated from the list prices); in exchange the team does not practise Kubernetes and is tied to ECS task definitions. \
      *Consequence:* the image pipeline builds `linux/arm64` only (5.2). Lambda was rejected because the services keep connection pools and run scheduled jobs.
    ]),
    ("AD-08", [
      *Fargate Spot above a regular-Fargate base.* \
      *Context:* commerce needs a guaranteed floor for availability but extra capacity only during load. \
      *Options considered:* all on-demand, all Spot, a mixed strategy. \
      *Choice:* capacity provider strategy `FARGATE` with base equal to the commerce minimum (2 in demo sessions) and weight 1, plus `FARGATE_SPOT` with weight 3. \
      *Trade-off:* Spot capacity is cheaper but can be reclaimed with two minutes' notice; the base keeps two tasks on regular Fargate, so a reclaim never takes commerce below its floor. \
      *Consequence:* configured; the 16 and 19 October AWS records show the strategy on the service, but not which provider each task ran on. The cost study of 25 September noted Spot as documented for x86 only, so ARM64 Spot placement is not shown by our evidence.
    ]),
    ("AD-09", [
      *CloudFront in front of an ALB restricted by prefix list and a secret header.* \
      *Context:* one HTTPS entry point for the website and the API, with the load balancer not usable directly. \
      *Options considered:* public ALB with its own certificate; API Gateway with a VPC link; CloudFront → ALB. \
      *Choice:* CloudFront → ALB on HTTP port 80; the ALB security group admits only CloudFront's origin-facing prefix list, and an ALB rule rejects requests without the `X-Origin-Secret` header. \
      *Trade-off:* one domain and no certificate to manage for the ALB; but the hop from CloudFront to the ALB is not encrypted (AISS-08) and the secret sits in Terraform state (AISS-09). \
      *Consequence:* requests that bypass CloudFront are refused; no per-client rate limiting (no WAF).
    ]),
    ("AD-10", [
      *One fck-nat instance, not a NAT Gateway.* \
      *Context:* private tasks need outbound access to Stripe, Google and AWS APIs. \
      *Options considered:* NAT Gateway per zone, one NAT Gateway, VPC interface endpoints, a NAT instance. \
      *Choice:* fck-nat on one `t4g.nano` with a static network interface; an Auto Scaling group of one replaces a failed instance. \
      *Trade-off:* estimated about US\$4 a month instead of US\$43.07 for one NAT Gateway (730 hours at US\$0.059); in exchange, egress depends on one instance in one zone (AISS-01). \
      *Consequence:* a free S3 gateway endpoint keeps image-layer pulls off the instance.
    ]),
    ("AD-11", [
      *One RDS server holding three databases.* \
      *Context:* each service must own its data (AD-03), but three servers would triple the largest fixed cost. \
      *Options considered:* one server per service; one shared schema; one server with a database and two roles per service. \
      *Choice:* one `db.t4g.micro` instance with databases `identity`, `commerce` and `notification`, each with an owner role for migrations and an app role for runtime. \
      *Trade-off:* isolation by grants and connection strings at the cost of one server; the services share capacity and a failure takes all three down (AISS-05). \
      *Consequence:* moving a database to its own server changes only the connection settings, not the code.
    ]),
    ("AD-12", [
      *Permanent edge, disposable runtime.* \
      *Context:* an always-on runtime would cost about US\$125 a month (estimated in the cost study) for a system used a few hours a week. \
      *Options considered:* always on; scale to zero; destroy and rebuild. \
      *Choice:* the `edge` stack (CloudFront, S3) is permanent; the `runtime` stack is created for each session or release and destroyed afterwards. \
      *Trade-off:* estimated US\$0.12 to US\$0.20 an hour while up; but a rebuild takes about 32 minutes (17 October run, AISS-06) and no data survives a session. \
      *Consequence:* every release is also a rebuild-from-nothing test; backups are deliberately off.
    ]),
    ("AD-13", [
      *No Kubernetes and no Argo CD.* \
      *Context:* GitOps was one option for continuous delivery. \
      *Options considered:* EKS + Argo CD; ECS with Terraform and GitHub Actions. \
      *Choice:* the latter. \
      *Trade-off:* the EKS control plane alone was estimated at about US\$73 a month, more than all our compute, and set-up at 8 to 12 person-days before any feature work (`03-k8s-argocd-assessment.md`); we give up the GitOps reconciliation loop. \
      *Consequence:* deployment is a pipeline run (5.3), not a controller.
    ]),
    ("AD-14", [
      *An own member account in an AWS Organization.* \
      *Context:* the SWE5006 environment and the SWE5001 environment must not be able to damage each other. \
      *Options considered:* share the SWE5006 account; IAM users in a new account; an Organization member account with Identity Center. \
      *Choice:* the last, with service control policies (SCPs) as guardrails the account administrator cannot lift. \
      *Trade-off:* separate bill and blast radius and short-lived logins; but an SCP can also block legitimate calls, as a CloudFront metric read was on 16 October (AISS-14). \
      *Consequence:* access details are in 4.3.
    ]),
    ("AD-15", [
      *Separate data subnets for RDS and Valkey.* \
      *Context:* assessor remark F6 (21 October): the database should be in its own subnet. \
      *Options considered:* keep the shared private subnets and rely on security groups; add data subnets with their own route table. \
      *Choice:* two data subnets, one per zone, with a route table that holds only the local VPC route. \
      *Trade-off:* no running cost and no code change; two more subnets and a route table to maintain, and a rebuild is needed to adopt it (free here, AD-12). \
      *Consequence:* configured and tested offline on 4 November 2026; not yet applied on AWS (AISS-07).
    ]),
  ),
  caption: [Physical architectural decisions (AD-07 to AD-15; numbering continues from 3.1.1 and is fixed in `registers.md`).],
) <tbl-ad-physical>

=== Technology and Services

The solution uses managed AWS services wherever one exists, so the team runs no servers of its own apart from one NAT instance. Every choice favours the lowest cost that still shows the architecture, because the runtime exists only for a few hours a week (AD-12). @tbl-tech lists the versions and sizes; the rationale for the main choices is in the AD rows of 3.2.1 and is not repeated here.

#figure(
  table(
    columns: (0.8fr, 1.4fr, 2.2fr),
    [Layer], [Technology and version], [Why this one, and what it costs us],
    [Application runtime], [Java 21, Spring Boot 4.1.1, MyBatis; image `eclipse-temurin:21-jre-noble`, built for `linux/arm64`], [The SWE5006 code base, reused unchanged by the strangler migration (AD-01). One base image for all three services keeps patching in one place],
    [Container platform], [ECS on Fargate, ARM64; 0.5 vCPU and 1 GiB per task], [No hosts to manage (AD-07). The task size was kept small on purpose: one 0.5 vCPU commerce task saturated at about 280 req/s on AWS on 19 Oct 2026 (evidence `20261019-v3-autoscaling`, 4.1), so the service scales out instead of up],
    [Image registry], [Amazon ECR, one repository per service; immutable tags, scan on push, lifecycle policy], [Immutable tags make a release digest refer to exactly one image; Trivy in the pipeline is the blocking scan (5.2) and ECR's scan is a second record],
    [Edge], [Amazon CloudFront (`PriceClass_200`, HTTP/2) and a private S3 bucket], [HTTPS without a custom domain or certificate; the website and the API share one origin, so the browser needs no cross-origin settings (AD-09). The price class excludes the most expensive edge locations],
    [Load balancing], [Application Load Balancer, HTTP :80, path rules], [Path-based routing to three services without a separate API gateway product; rejected API Gateway because it adds a hop and a per-request charge],
    [Service discovery], [ECS Service Connect], [Commerce calls identity by name inside the VPC with no load balancer or DNS records to manage],
    [Relational database], [Amazon RDS for PostgreSQL 18, `db.t4g.micro`, 20 GiB gp3], [PostgreSQL 18 generates UUIDv7 keys natively (3.2.3). The smallest Graviton class is enough for demo load; the single database server is the next scaling limit (AISS-05)],
    [Session store], [Amazon ElastiCache for Valkey 8.0, `cache.t4g.micro`, one node], [Redis-compatible, so Sa-Token and Spring Data Redis work unchanged; Valkey costs less than Redis OSS on ElastiCache. One node is a cost choice (AISS-01, AISS-10)],
    [Messaging], [Amazon SNS (one topic) and SQS (standard queues with DLQs)], [Fan-out to several consumers with no broker to run (AD-04); standard queues give at-least-once delivery, which the consumers handle with `sys_consumed_message`],
    [Outbound network], [fck-nat on `t4g.nano`], [About a tenth of a NAT Gateway's cost (AD-10)],
    [Configuration and secrets], [SSM Parameter Store, SecureString with the AWS-managed key], [Free at this volume; tasks read values at start-up through their execution role. Secrets Manager was not needed, because rotation happens by rebuilding the runtime],
    [Monitoring], [CloudWatch Logs (7-day retention), one dashboard, alarms to an SNS alert topic], [Managed and cheap at demo volume; logs are kept only as long as a sprint review needs them],
    [Infrastructure as code], [Terraform 1.16, AWS provider 5.100; three stacks: `foundation`, `edge` and `runtime`], [The SWE5006 environment already used Terraform; `terraform test` runs offline in CI (5.2). Stack split by lifetime: `foundation` and `edge` are permanent, `runtime` is disposable],
  ),
  caption: [Technology and services, with versions and sizes from `backend/pom.xml`, `backend/Dockerfile` and `infra/swe5001` (checked on 5 November 2026).],
) <tbl-tech>

*Encryption and secrets.* Data is encrypted at rest everywhere it is stored: RDS storage, the Valkey node, SNS (AWS-managed key), SQS (SQS-managed keys), ECR and the Terraform state bucket. In transit, browsers use HTTPS to CloudFront, the services use TLS to RDS (`rds.force_ssl = 1`, `sslmode=require`) and to Valkey (with an AUTH token). The CloudFront → ALB hop is plain HTTP (AISS-08). Database passwords are generated as ephemeral values and written with write-only arguments, so they never reach Terraform state; the Valkey AUTH token and the origin secret do reach it (AISS-09). Each service connects with its own app role, and only `db-init` uses the master password.

*Cost.* Calculated from the AD rows: while it is up, the runtime costs about US\$0.12 to US\$0.20 an hour (AD-12), and nothing for the rest of the week except the permanent edge. A measured figure from the AWS bill is not cited here, because the repository holds no record of it yet; #pending[chapter 6 reports the measured cost once that record is added (check with chapter 6 at the reconcile)].

=== Persistence Design

All persistent business data lives in PostgreSQL. Each service has its own database on the shared server and can read and write only the tables it owns. Sessions live in Valkey and are lost if the node restarts. Events live in the commerce outbox table until they are published, then in SQS until a consumer has applied them. Nothing is backed up, because every session starts from seed data (AD-12).

==== Data stores and table ownership

Every service database runs the same Flyway migration stream (`V1` to `V28`), so each database contains all 29 tables. Ownership is enforced by grants, not by which tables exist: after each migration, `afterMigrate__service_grants.sql` gives the service's app role `SELECT, INSERT, UPDATE, DELETE` on the tables it owns and revokes everything on the others. The owner role runs migrations; the app role used at runtime cannot change the schema.

#figure(
  table(
    columns: (auto, auto, 1fr),
    [Database], [Owned tables], [Tables],
    [`identity`], [6], [`sys_user`, `sys_user_oauth`, `sys_role`, `sys_permission`, `rel_user_role`, `rel_role_permission`],
    [`notification`], [2], [`biz_notification`, `sys_consumed_message`],
    [`commerce`], [21], [Catalogue: `biz_product`, `biz_team_price_policy`, `biz_team_price_quote`, `log_product_team_terms`. Team purchase: `biz_group_deal`, `biz_group_member`, `biz_group_participation_receipt`, `biz_share_invite`, `biz_share_credit`. Order and payment: `biz_purchase_intent`, `biz_payment`, `biz_order`, `biz_refund`, `log_refund_attempt`, `biz_payment_review_case`, `log_payment_review`, `log_payment_webhook_event`. Technical: `sys_outbox`, `sys_scheduler_lock`, `sys_scheduled_item_retry`, `sys_checkout_expiry_cursor`],
  ),
  caption: [Table ownership per database, from `afterMigrate__service_grants.sql`. Commerce owns every table not listed for identity or notification.],
) <tbl-ownership>

The trade-off: one migration stream is simple to build and test, and the grants stop a service from using another's tables, which architecture tests also check. The cost is that unused, empty tables exist in every database, and a migration for one service runs in all three.

==== Conventions

The schema follows the team's PostgreSQL conventions (`pgsql-ddl` skill):

- *Keys.* Native `uuid` primary keys generated as UUIDv7 (`uuidv7()`, PostgreSQL 18). UUIDv7 is time-ordered, so new rows append to the index instead of splitting pages, and the application can create an identifier before the insert, which the outbox uses as the event ID.
- *Prefixes.* `sys_` for platform and technical tables, `biz_` for business entities, `rel_` for many-to-many links, `log_` for append-only records.
- *Audit and soft delete.* Business tables carry `create_time`, `create_by`, `update_time`, `update_by` and `deleted`; mutable aggregates also carry `version` for optimistic locking. Uniqueness rules use partial indexes `WHERE deleted = false`, so a soft-deleted row does not block a new one.
- *No foreign keys or CHECK constraints.* Relationships are logical, kept by the application inside its transactions. This lets a table move to another service's database without unpicking constraints, and keeps validation in one place (the domain model). The cost is that the database does not catch an orphaned row; the unique indexes below catch the errors that matter most.

==== Logical data models

#figure(kind: image, d9-identity, caption: [Identity database, logical data model (UML class notation for tables). Users, their Google identities and role-based permissions.]) <fig-d9-identity>

#figure(kind: image, d9-notification, caption: [Notification database, logical data model (UML class notation for tables). A notification and the consumed-message record are written in one transaction, keyed by the same event ID.]) <fig-d9-notification>

#figure(kind: image, scale(90%, reflow: true, d9-commerce), caption: [Commerce database, logical data model (UML class notation for tables; key columns only). Dashed lines are logical relationships with multiplicities; there are no foreign keys. A START intent reserves its `deal_id` before the team row exists, so the team end is 0..1.]) <fig-d9-commerce>

==== Indexes that protect invariants

Several business rules are guaranteed by unique indexes, so that a race between two transactions ends in a constraint violation instead of a wrong state:

#figure(
  table(
    columns: (auto, 1fr),
    [Index], [Rule it protects],
    [`uq_biz_group_member_deal_user` (`deal_id, user_id`)], [A buyer joins a team at most once],
    [`uq_biz_group_member_payment_id`], [One payment admits at most one member],
    [`uq_biz_purchase_intent_fulfilled_payment`], [One payment fulfils at most one purchase intent, and so produces at most one order (ADR 0001)],
    [`uq_biz_payment_pending_intent` (`WHERE status = 'created'`)], [At most one open payment per purchase intent],
    [`uq_biz_payment_request_key` (`user_id, request_key`)], [A retried checkout request does not create a second payment],
    [`uq_biz_order_payment_id`, `uq_biz_order_deal_user`], [One order per payment, and one order per buyer per team],
    [`biz_refund.payment_id` (unique)], [A payment is refunded at most once],
    [`uq_log_payment_webhook_event_event_id`], [A Stripe webhook event is processed at most once],
    [`uq_sys_scheduler_lock_key`], [A scheduled job has at most one lease holder],
    [`pk_sys_consumed_message` (`event_id, consumer`)], [A consumer applies an event at most once],
  ),
  caption: [Unique indexes that enforce invariants (Flyway migrations V2–V28).],
) <tbl-invariant-indexes>

==== Aggregates and their tables

@tbl-aggregate-tables maps each aggregate root of @tbl-aggregates (3.1.4) to its tables, in the same order. Technical tables are in @tbl-technical; the role and permission tables of `identity` are reference data read through `RbacMapper`, not part of an aggregate.

#figure(
  table(
    columns: (auto, auto, 1fr),
    [Aggregate root (3.1.4)], [Database], [Tables],
    [`User`], [`identity`], [`sys_user`, `sys_user_oauth`],
    [`Product`], [`commerce`], [`biz_product`, `log_product_team_terms`],
    [`TeamPricePolicy`], [`commerce`], [`biz_team_price_policy`, `biz_team_price_quote`],
    [`GroupDeal`], [`commerce`], [`biz_group_deal`, `biz_group_member` (`PaidMember`), `biz_group_participation_receipt` (`ParticipationReceipt`); `biz_share_invite` (`ShareInvite`) and `biz_share_credit` (`ShareCredit`) through their own repositories],
    [`PurchaseIntent`], [`commerce`], [`biz_purchase_intent`],
    [`Order`], [`commerce`], [`biz_order`],
    [`CheckoutPayment`], [`commerce`], [`biz_payment`, `log_payment_webhook_event`],
    [`RefundWork` & `PaymentReviewCase`], [`commerce`], [`biz_refund`, `log_refund_attempt`; `biz_payment_review_case`, `log_payment_review`],
    [`Notification`], [`notification`], [`biz_notification`],
  ),
  caption: [Aggregate-to-table mapping, row for row with @tbl-aggregates. Class names are in `blocbuy-domain`.],
) <tbl-aggregate-tables>

==== Transactions and concurrency

One business transaction never spans two databases; this is why payment, team and order stay in one service (AD-02). Within commerce, the riskiest transaction is settlement of a Stripe webhook for a team purchase. It locks rows in a fixed order (team, then purchase intent, then payment) and then writes the membership, order and payment updates in one transaction (`OrderFulfilmentAdapter.lockForSettlement`, `StripeCheckoutService.settle`; ADR 0001). The fixed order prevents deadlocks between concurrent webhooks; the team row lock serialises the race for the last seat. The detailed sequence is in 3.2.4. Mutable aggregates also use their `version` column for optimistic locking on ordinary updates.

==== Technical tables

#figure(
  table(
    columns: (auto, auto, 1fr),
    [Table], [Migration], [Purpose and pattern],
    [`sys_outbox`], [V26], [*Transactional outbox.* `IntegrationOutboxWriter` inserts the event in the same transaction as the business change (`BEFORE_COMMIT`), so an event exists if and only if the change committed. `OutboxRelayJob` runs `OutboxRelayService` under the scheduler lease; it publishes unpublished rows to SNS in batches and sets `published_at`. The row ID (UUIDv7) is the event ID],
    [`sys_consumed_message`], [V28], [*Idempotent receiver.* The notification consumer inserts `(event_id, consumer)` in the same transaction as the notification; a duplicate delivery inserts nothing and is acknowledged],
    [`sys_scheduler_lock`], [V13], [*Lease.* `LeasedJobRunner` gives each scheduled job (expiry, refunds, outbox relay) one holder across all tasks; PostgreSQL is the lock of record],
    [`sys_scheduled_item_retry`, `sys_checkout_expiry_cursor`], [V17, V25], [Per-item retry back-off and a paging cursor, so one failing item cannot block a job],
  ),
  caption: [Technical tables and the patterns they support.],
) <tbl-technical>

Calculated: the relay publishes about 50 events per second, and only one copy runs because it holds the scheduler lease (see 4.1 for the formula).

==== Non-relational stores and messages

*Session and cache store (Valkey).* Valkey holds only data that can be lost: login sessions, the role snapshot that lets any service authorise a request, one-time sign-in state, two read caches and rate-limit counters. Every key has a time to live (TTL), so nothing grows without bound. @tbl-valkey lists the key model, taken from the code and configuration (Sa-Token 1.46.0, `application.yml`, `RedisKeys`); it has not been read from a running node.

Sa-Token is configured with the token name `blocbuy-token`, a random UUID token sent as an HTTP-only cookie, a 24-hour timeout (`timeout: 86400`) and several concurrent sessions per user (`is-concurrent: true`, `is-share: false`). The application's own keys follow `blocbuy:{profile}:{domain}:{type}:{id}`, where `{profile}` is the active Spring profile list; on ECS that is `ec2,<service>,aws`, so each service's cache keys are separate from the others'.

#figure(
  table(
    columns: (1.6fr, 0.7fr, 0.9fr, 1.8fr),
    [Key pattern], [Written by], [TTL], [Content and what is lost if the node fails],
    [`blocbuy-token:login:token:<token>`], [identity], [24 h], [Maps a token to the user ID. Lost: the user is signed out],
    [`blocbuy-token:login:session:<userId>`], [identity], [24 h], [The user's account session and the list of that user's tokens. Lost: all of the user's devices are signed out],
    [`blocbuy-token:rbac:login:{<userId>}`], [identity], [Same as the session], [`RedisLoginSnapshotGateway`. The user's roles and permissions; every service reads it to authorise a request without calling identity (AD-05). Lost: requests are refused until the user signs in again],
    [`…:rbac:login:{<userId>}:refresh`], [identity], [120 s], [A fence so that only the latest role refresh is published. Lost: harmless],
    [`blocbuy:{profile}:identity:oauth-state:<state>`], [identity], [Short (configured, default 5 min); deleted on first read], [`RedisOAuthStateStore`. One-time Google sign-in state that binds the callback to the browser that started it. Lost: a sign-in in progress must restart],
    [`blocbuy:{profile}:groupbuy:best-open-deal:<productId>`], [commerce], [15–25 s (5–10 s for "none"), with jitter], [`RedisOpenDealOfferCache`. Cached best open team for a product page. Lost: the next read goes to PostgreSQL],
    [`blocbuy:{profile}:groupbuy:invite-ref:<ref>`], [commerce], [20–30 s with jitter, never past the team's expiry], [`RedisCachedShareInviteRepository`. Cached share-invite lookup. Lost: the next read goes to PostgreSQL],
    [`blocbuy:{profile}:common:rate-limit:<scope>:<digest>`], [commerce], [One rate-limit window], [`RedisRateLimitGateway`. Request counter per scope and hashed subject. If Valkey is unreachable the limiter fails open and counts the event in a metric],
  ),
  caption: [Valkey key model (from code and configuration, 5 November 2026). PostgreSQL remains the system of record; no business fact lives only in Valkey.],
) <tbl-valkey>

The design rule is that Valkey is never the lock of record or the only copy of a business fact: the scheduler lease is in PostgreSQL (`sys_scheduler_lock`), and caches are read-through with short, jittered TTLs so that a lost node costs latency, not correctness. The cost of a lost node is that every user is signed out (AISS-10).

*Event envelope.* Every integration event is published to SNS as JSON with this envelope (`IntegrationEventEnvelope`):

#figure(
  table(
    columns: (auto, auto, 1fr),
    [Field], [Type], [Meaning],
    [`eventId`], [UUID], [Outbox row ID; consumers de-duplicate on it],
    [`eventType`], [string], [`TeamSucceeded`, `TeamExpired` or `PaymentSucceeded`],
    [`version`], [integer], [Payload schema version (currently 1, for example `TeamSucceededV1`)],
    [`occurredAt`], [timestamp], [When the business change committed],
    [`producer`], [string], [`blocbuy-commerce`],
    [`traceId`], [string], [Correlates logs across services],
    [`aggregateId`], [UUID], [The team or payment the event is about],
    [`payload`], [object], [Event-specific fields, for example the team, product, size and recipient user IDs],
  ),
  caption: [Integration event envelope. The SNS message attributes repeat `eventType`, `version` and `traceId`, so subscriptions can filter without parsing the body.],
) <tbl-envelope>

*Queues.* SNS delivers raw messages to SQS, and each subscription filters on the `eventType` attribute. The notification queue receives `TeamSucceeded`, `TeamExpired` and `PaymentSucceeded`; the audit queue receives every event whose type starts with `Team`. Each queue has a 60-second visibility timeout, 20-second long polling and 4-day retention; after 5 failed receives a message moves to the queue's DLQ, which keeps it for 14 days. Topic and queues are encrypted (AWS-managed SNS key, SQS-managed encryption).

==== Data classification

#figure(
  table(
    columns: (auto, 1fr, 1fr, 1.2fr, 1.2fr),
    [Data group], [Data format], [Storage technology], [Security requirements and controls], [Trade-off],
    [Identity], [Relational rows; Google profile as JSON (`raw_profile`)], [PostgreSQL, `identity` database], [Personal data (email, name). Only identity's app role can read it; storage encrypted; TLS required; other services see only public nicknames through an internal endpoint], [Synchronous lookup from commerce makes identity a dependency of checkout (AD-06, AISS-03)],
    [Commerce and payment], [Relational rows; money as `numeric` with currency], [PostgreSQL, `commerce` database], [No card data stored: Stripe holds it and we keep only Stripe IDs. Unique indexes stop double charges and double refunds; webhook events stored for audit], [One database for team, order and payment keeps settlement atomic, at the cost of one large service (AISS-02)],
    [Notifications], [Relational rows, immutable], [PostgreSQL, `notification` database], [Readable only by the recipient through the API; only notification's app role can write], [Eventually consistent, a few seconds behind the action (AISS-11)],
    [Sessions], [Key–value, Sa-Token session objects], [ElastiCache Valkey], [HTTP-only cookie; TLS and an AUTH token on the connection; 24-hour expiry; reachable only from the service group], [Fast and shared by all services, but one node: a restart signs everyone out (AISS-10)],
    [Events], [JSON envelope with string message attributes], [`sys_outbox` table, then SNS and SQS], [Encrypted topic and queues; IAM policies limit publish and receive to the producing and consuming task roles; payloads carry IDs, not personal data], [At-least-once delivery, so consumers must be idempotent (`sys_consumed_message`)],
  ),
  caption: [Data classification per data group (template format).],
) <tbl-classification>

=== Detailed Design
#owner([Yu Qianshuo], closes: "F5, F7, F10", diagrams: "D11–D14")
#prompt[Describe the detailed design (of classes, objects, functions, etc.) of a few architecturally representative use cases or user stories.]
#todo(
  [UC4 join a team by paying: two users (leader pays, shares, member joins and pays, team succeeds) and the race for the last seat.],
  [Team expiry with refund: scheduled job, outbox, broker, notification.],
  [Real class and method names only (check with `rg`); lock order per the AGENTS.md claims table.],
  [Design patterns present in the code: Factory Method, Observer, Transactional Outbox, Ports and Adapters, Idempotent Receiver.],
)
#diagram-slot("D11", [UC4 class diagram (UML class). Adapter → application → domain → infrastructure.])
#diagram-slot("D12", [UC4 sequence, two users and the last seat (UML sequence). Transaction boundary marked.])
#diagram-slot("D13", [Team expiry and refund class diagram (UML class).])
#diagram-slot("D14", [Team expiry and refund sequence (UML sequence).])

== Other Architectural Decisions
#owner[Lim Jiechao]
#prompt[Provide a summary of the other significant decisions made in arriving at the architecture of the solution that has not been mentioned. If you explain the rationale of the decisions in other section (such as the quality attribute sections), you can make a reference to those sections.]
The key architectural decisions taken are as follows:
#ad-table(
  ("AD-16", [Scheduled jobs stay in the owning service under a lease (ADR 0002). _Trade-off to write._]),
  ("AD-17", [GitHub OIDC roles and Parameter Store; no stored cloud keys. _See 4.3._]),
  ("AD-18", [COLA layers and ArchUnit rules keep boundaries (ADR 0008). _See 4.4._]),
  ("AD-19", [Release only by a dev → prod promotion that builds, deploys, tests, releases and destroys. _See 5.3._]),
)

== Architectural Limitation

The solution has fifteen known limitations. Most are deliberate cost choices for a system that runs a few hours a week. Three need evidence that only an AWS session can give (phase R0), six must be closed before real users (phase R1), three wait until load requires them (phase R2), and three are accepted by design.

*Roadmap.* Each open item is assigned to one phase. #pending[The dates below are proposed and *pending team review*;] they are derived from the project calendar and the open GitHub issues, as follows:

- The course ends on 16 November 2026 with the final `terraform destroy` (Sprint 4 plan, calendar). No AWS session has run since 19 October 2026; the November runs of `swe5001-env` were nightly `down` runs only. R0 is therefore one evidence session between the section-draft deadline (11 November) and the destroy, which also needs a `dev` → `prod` promotion first, because the data subnets (AD-15) exist only on `dev`.
- The drills behind AISS-13 are scoped in \#459 (estimate 4 h) and \#448 (poison message and backlog scaling); both were due on 20 October and are still open. #pending[AISS-12 is moved out of R0: it needs a distributed load generator that \#458 does not provide, so one session cannot close it.]
- There is a precedent for a dated acceptance: \#227 accepted the origin-TLS risk for `dev` with an end date of 16 November 2026. #pending[R1 and R2 apply only if the platform continues after the course; their dates assume a go-live decision in December 2026.]

#figure(
  table(
    columns: (1fr, 1fr, 2.6fr, 0.8fr),
    [Phase], [Target #pending[(pending review)]], [Content], [Issues],
    [R0 — final evidence session], [#pending[Promote by Thu 12 Nov 2026; session on Fri 13 Nov 2026]], [Show on AWS what is configured or tested only locally: data-subnet placement (AISS-07), self-healing, poison-message and identity-outage drills (AISS-13). Fix the scaling-restore defect first, so the session does not need manual repair (AISS-14)], [\#589, \#459, \#448],
    [R1 — production readiness], [#pending[31 Mar 2027]], [Remove single points of failure and close security gaps before real users: multi-zone data tier and NAT (AISS-01), internal service token (AISS-04), HTTPS to the origin (AISS-08), secrets out of state (AISS-09), narrower monitoring permissions (AISS-15); prove linear scaling with a distributed load generator (AISS-12)], [\#227 (NFR-37), \#458],
    [R2 — scale-out], [#pending[30 Jun 2027, or earlier if a trigger fires]], [Split payment out of commerce (AISS-02), give a database its own server (AISS-05), replace the shared session store (AISS-10). Trigger: commerce needs more than three tasks for a sustained hour, or database CPU stays above 70% at peak], [—],
    [Accepted by design], [#pending[Review with R1, 31 Mar 2027]], [Kept unless the business need changes (AISS-03, AISS-06, AISS-11)], [—],
  ),
  caption: [Roadmap phases for open limitations. #pending[Dates proposed on 5 November 2026 and pending team review.]],
) <tbl-roadmap>

The key outstanding architectural issues in the solution are as follows:

#figure(
  table(
    columns: (0.7fr, 1.2fr, 1.4fr, 1.3fr, 0.7fr, 0.8fr),
    [Identifier], [Issue and impact], [Description], [Resolution], [Owner], [Status],
    [AISS-01], [RDS, Valkey and the NAT instance each run in one zone. A zone failure stops writes, sign-ins or outbound calls], [RDS is single-AZ with no backups; Valkey has one node; one fck-nat instance in zone a serves both zones (AD-10, AD-11)], [Multi-AZ RDS, a Valkey replica with automatic failover, a NAT per zone or VPC interface endpoints. #pending[*R1, by 31 Mar 2027* (pending review)]], [Liu Jin], [Open (cost choice)],
    [AISS-02], [The commerce service is large. Payment scales and deploys with team purchase], [Team, order and payment share the settlement transaction, so they stay in one service (AD-02)], [Extract payment with a saga for refunds once its load differs from commerce's. #pending[*R2, by 30 Jun 2027* (pending review)]], [Eric], [Open],
    [AISS-03], [Checkout stops while identity is down], [Commerce checks the buyer with identity synchronously and fails closed (AD-06); the webhook returns 500 and Stripe retries], [Accepted: no wrong refunds or admissions. Later, a short-lived cache or circuit breaker. #pending[*review 31 Mar 2027* (pending review)]], [Yu Qianshuo], [Accepted by design],
    [AISS-04], [Internal endpoints rely on network rules only. A compromised task in the VPC could call them], [`/internal/**` is blocked at the ALB and skipped by session checks, but carries no service credential], [A service token or mutual TLS on internal calls. #pending[*R1, by 31 Mar 2027* (pending review)]], [Pan], [Open],
    [AISS-05], [One shared database server. The services share capacity and one failure stops all three], [Three databases on one `db.t4g.micro` (AD-11)], [Move a database to its own server; only connection settings change. #pending[*R2, by 30 Jun 2027* (pending review)]], [Liu Jin], [Open],
    [AISS-06], [A rebuild takes about 32 min, so a session must start early], [On AWS on 17 Oct 2026, create, deploy and smoke took about 32 min in promotion #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244")[run 39008208244]; about 15 min of that is the deliberate 900 s wait for SNS filter policies to apply (on AWS on 16 Oct 2026, evidence `20261016-http-baseline-dashboard`)], [Start sessions early; the edge stays permanent (AD-12). #pending[*review 31 Mar 2027* (pending review)]], [Pan], [Accepted (mitigated)],
    [AISS-07], [Data subnets are not yet proven on AWS], [Database and services shared subnets until 4 Nov 2026; separate data subnets are configured and tested offline (AD-15)], [Record RDS and Valkey subnet placement and the data route table in the next session. #pending[*R0, 13 Nov 2026* (pending review)]], [Liu Jin], [In progress],
    [AISS-08], [CloudFront → ALB is plain HTTP. Origin traffic is unencrypted inside AWS], [The ALB listens on port 80; access is limited by prefix list and header (AD-09)], [An HTTPS listener with an ACM certificate and HTTPS-only origin policy. #pending[*R1, by 31 Mar 2027* (pending review)]], [Pan], [Open],
    [AISS-09], [The Valkey AUTH token and the origin secret are in Terraform state. Anyone who can read state can read them], [Database passwords are write-only, but these two have no write-only path in the provider], [Rotate each session (already true: runtime is rebuilt); move to write-only arguments when available. #pending[*R1, by 31 Mar 2027* (pending review)]], [Pan], [Open (accepted)],
    [AISS-10], [All sessions are in one Valkey node. A restart signs everyone out], [Services read the shared Sa-Token session instead of calling identity (AD-05)], [Short-lived signed tokens with refresh at the edge, or a replicated session store. #pending[*R2, by 30 Jun 2027* (pending review)]], [Eric], [Open],
    [AISS-11], [Events are eventually consistent. A notification can lag its action by seconds], [Outbox → SNS → SQS adds relay and polling delay (AD-04)], [Accepted: the UI shows progress and the API is the source of truth. #pending[*review 31 Mar 2027* (pending review)]], [Eric], [Accepted by design],
    [AISS-12], [Linear scaling beyond three tasks is not proven], [On AWS on 19 Oct 2026, commerce scaled 1 → 3 tasks at about 455 req/s, the load generator's limit (evidence `20261019-v3-autoscaling`, 4.1)], [A distributed load generator; rerun V3 to four or more tasks. #pending[*R1, by 31 Mar 2027* (pending review)]], [Pan], [Open],
    [AISS-13], [Burst, self-healing, poison-message and identity-outage drills ran locally only], [Tested locally, not on AWS: V4, V5, V7 and the identity outage (4.2)], [Run the drills on AWS. #pending[*R0, 13 Nov 2026* (pending review)]], [Yu Qianshuo], [Open],
    [AISS-14], [An interrupted start-up can leave scaling suspended; an SCP denied a CloudFront metric read], [Recorded on AWS on 16 Oct 2026 and repaired by hand (evidence `20261016-http-baseline-dashboard`)], [A retry regression test and fix; settle metric access with the Organization policy owner. #pending[*R0, before the 13 Nov 2026 session* (pending review)]], [Liu Jin], [Open],
    [AISS-15], [`CloudWatchFullAccessV2` on the apply role is not limited to SWE5001], [The role could change alarms or dashboards outside this project], [Restrict to `blocbuy-swe5001-*` names. #pending[*R1, by 31 Mar 2027* (pending review)]], [Pan], [Open (accepted)],
  ),
  caption: [Architectural limitations (AISS register; numbering fixed in `registers.md`). #pending[Target dates are pending team review.]],
) <tbl-aiss>
