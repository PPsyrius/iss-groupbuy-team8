#import "../lib.typ": *

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
    *Trade-off & Consequence:* Business state commits and event publication are atomic; adding the `audit-events` consumer required 2 lines of Terraform and zero changes to `commerce-svc` (verified on AWS on 17 October 2026). The trade-off is eventual consistency with a ~2 s relay polling lag (`AISS-11`, by design) and a calculated single-relay ceiling of ~50 events/s under `LeasedJobRunner`.
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
    [Delivers static SPA assets, routes `/api/v1/auth/*` and `/api/v1/users/*` to `identity-svc`, `/api/v1/notifications/*` to `notification-svc`, and remaining public paths to `commerce-svc`; blocks `/internal/*`.],

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
  caption: [DDD Strategic Context Map (`D4`). Dashed outer boxes show the three deployed services (`identity-svc`, `commerce-svc`, `notification-svc`) and the `audit-events` subscriber; solid inner boxes show the six bounded contexts and the `common` Shared Kernel. Every relationship edge carries its Upstream (`U`) / Downstream (`D`) direction and DDD pattern.],
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
    [Versioned, PII-free JSON contracts allow `notification-svc` and future subscribers (`audit-events`) to consume business facts with zero coupling to `commerce-svc`.],

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
    [*Entities:* `PaidMember`, `ParticipationReceipt` \ *VOs:* `DealStatus`, `PaidParticipation`, `NewDealTerms`, `ShareInvite`, `ShareCredit`],
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
  caption: [Domain event and integration event flow (`D8`, UML component view). Inside `commerce-svc`, `BEFORE_COMMIT` subscribers enforce immediate consistency for orders, refund obligations, and `sys_outbox` writes; across services, `OutboxRelayService` and the SNS/SQS Broker Topology deliver versioned `V1` events to `notification-svc` and `audit-events`.],
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
    [SNS topic → `notification-events` SQS queue → `notification-svc` (`SqsNotificationEventConsumer` → `NotificationEventAppService`).],

    [`TeamSucceededV1`],
    [Published (`v1` in `IntegrationEventEnvelope`)],
    [`commerce-svc` (`IntegrationOutboxWriter`)],
    [`(dealId, productId, size, List<UUID> recipientUserIds)`],
    [SNS topic → `notification-events` SQS queue (`notification-svc`) *and* `audit-events` SQS queue (filtered fan-out).],

    [`TeamExpiredV1`],
    [Published (`v1` in `IntegrationEventEnvelope`)],
    [`commerce-svc` (`IntegrationOutboxWriter`)],
    [`(dealId, productId, expiredAt, List<UUID> recipientUserIds)`],
    [SNS topic → `notification-events` SQS queue (`notification-svc`) *and* `audit-events` SQS queue (filtered fan-out).],
  ),
  caption: [Catalogue of internal domain events and published cross-service integration events (`D8`).],
) <tbl-domain-events>

Two courseware design principles shape this event pipeline (`#741`):

1. *Broker Topology (Choreography) vs Mediator Topology (Orchestration):* For the multi-aggregate financial settlement inside `commerce-svc`, we rejected a distributed Saga Mediator (`AD-02`) and kept orchestration inside a single local ACID transaction (`OrderFulfilmentAdapter` + deterministic `@Order(10, 20)` `BEFORE_COMMIT` listeners). Conversely, once the local transaction commits, all downstream reactions (`notification-svc`, `audit-events`, and future analytics) follow a pure *Broker Topology (Choreography)*: `commerce-svc` publishes domain facts to the SNS topic without knowing which consumers exist, and each consumer independently subscribes its own SQS queue and DLQ.
2. *Boundary Event Enrichment without PII Leakage:* Internal domain events (`TeamSucceeded` and `TeamExpired`) carry only `(dealId, productId, ...)`—they do not list the team's member IDs. If `commerce-svc` published those raw records directly, `notification-svc` would have to make a synchronous callback to `commerce-svc` to query team membership, re-introducing tight runtime coupling. Instead, `IntegrationOutboxWriter` performs *Event Enrichment at the bounded-context boundary*: inside the same `BEFORE_COMMIT` transaction (`@Order(Ordered.LOWEST_PRECEDENCE)`), it queries `IntegrationRecipientRepository.findActiveMemberIdsByDealId(dealId)` and embeds `List<UUID> recipientUserIds` into `TeamSucceededV1` and `TeamExpiredV1`. At the same time, the payload carries *UUIDs and references only*—never emails, names, payment amounts, or Stripe tokens. Because `notification-svc` delivers in-app notifications (`biz_notification` keyed by `user_id`), it processes every event in one local transaction (`sys_consumed_message` claim + `biz_notification` insert) with *zero synchronous callbacks* to `commerce-svc` or `identity-svc`. If external Email or SMS channels are added in a future phase, `notification-svc` can resolve contact details via a dedicated read-through port on `identity-svc` or encrypted notification tokens without altering `commerce-svc`.

==== Platform Interaction for Producers and Consumers, Measured Reuse, and Governance

BlocBuy's platform architecture serves two external participant sides and an expandable set of internal/external service consumers (`Q2`, `Q3`, `#741`):

- *Producers (Supply & Demand Creators):* Sellers and administrators publish product listings, configure `TeamPricePolicy`, and seed `ProductTeamTerms` (`UC9`). Equally important, *team leaders act as demand producers*: by paying first to open a team (`UC3`) and minting a cryptographic `ShareInvite` link (`UC7`), they recruit buyers from external chat groups into the platform, driving its viral network effect.
- *Consumers (Buyers & Event Subscribers):* Joining buyers (`UC1`, `UC4`, `UC8`) browse dual-priced listings, join open teams with automatic refund protection, and read in-app notifications. On the technical plane, downstream services (`notification-svc` and `audit-events`) act as *event consumers* that derive new user-facing or analytical value from `commerce-svc` facts.

*Demonstrated platform extensibility (measured reuse):*
- *Zero-code event subscriber onboarding:* Adding the `audit-events` queue and its dead-letter queue to capture all `TeamSucceeded` and `TeamExpired` facts required *2 lines of Terraform configuration* in `infra/swe5001/modules/messaging/main.tf` (PR `\#487`) and *zero code or configuration changes* in `commerce-svc` (verified on AWS on 17 October 2026; detailed in Section 4.4).
- *Workload-isolated path for future high-compute and batch consumers (`#741`):* Because every team and payment fact is fanned out through SNS to independent SQS queues, future *compute-intensive or long-running batch workloads*—such as end-of-day financial settlement reconciliation, seller payout aggregation, or machine-learning deal-recommendation retraining that would exceed serverless FaaS 15-minute execution limits—can subscribe to `audit-events` and run as isolated *ECS Scheduled Tasks or AWS Batch jobs* (`AD-07`, `AD-16`). This guarantees that heavy analytical or batch compute never contends with `commerce-svc` checkout tasks for CPU or PostgreSQL connection-pool capacity.

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
#owner([Liu Jin], closes: "F6, F8 (physical)", diagrams: "D5, D6")
#prompt[Describe the architecturally significant components (or elements) of the solution by describing the physical design which may include the network, technologies, nodes and machines where the components are deployed using physical detail deployment and other diagram as necessary. This is where you can also include and describe the cloud services that you use if any.]
#todo(
  [Mapping table: logical element (3.1) → AWS product.],
  [Network: public subnets (ALB, NAT), private application subnets (ECS tasks), data subnets (RDS, Valkey) with no route out of the VPC (AD-15). State "configured, tested offline on 4 Nov 2026" until an AWS session proves it.],
)
#diagram-slot("D5", [Physical deployment view (UML deployment). Nodes, artefacts and two availability zones.])
#diagram-slot("D6", [Network and security groups (AWS architecture icons). Public, application and data subnets per zone.])

=== Key Architectural Decisions
#prompt[Provide a summary of the significant decisions made in arriving at the architecture of the solution that are related and/or reflected in the physical view. Documented decisions should focus on trade-off choices and rationale. You should continue the numbering from the previous architectural decisions table.]
The key architectural decisions taken are as follows:
#ad-table(
  ("AD-07", [ECS Fargate on ARM. _Trade-off to write._]),
  ("AD-08", [Fargate Spot above the regular-Fargate minimum (1 : 3). _Trade-off to write._]),
  ("AD-09", [CloudFront → ALB restricted by origin prefix list and a secret header. _Trade-off to write._]),
  ("AD-10", [fck-nat instance, not a NAT Gateway. _Trade-off to write._]),
  ("AD-11", [One RDS server holding three databases. _Trade-off to write._]),
  ("AD-12", [Permanent edge stack, disposable runtime stack. _Trade-off to write._]),
  ("AD-13", [No Kubernetes and no Argo CD. _Trade-off to write._]),
  ("AD-14", [Own member account in an AWS Organization with SCP guardrails. _Trade-off to write._]),
  ("AD-15", [Separate data subnets for RDS and Valkey with no route out of the VPC (F6, \#589). _Trade-off to write._]),
)

=== Technology and Services
#prompt[Describe the architecturally significant technology and cloud services that are used in the solution and describe the trade-offs and rationale.]
#todo([Services, sizes and versions with trade-offs (deck backup B7). Check every figure against evidence.])

=== Persistence Design
#owner([Liu Jin], closes: "F11", diagrams: "D9")
#prompt[State how data is managed in the solution, e.g., in a relational database. Describe any special considerations for transaction management, concurrency, etc. Data persistence — indicate which parts of the data model must persist beyond a transaction or session and the period of persistence. Provide a logical data model for the persistent data storage used in the solution.

Describe the information architecture of the solutions describing the type of data inventory and classification that you have and how the data will be stored, secured and handled.]
#todo(
  [Table ownership per service; every database runs the full Flyway stream, the runtime role only reaches its own tables.],
  [Conventions: UUIDv7, table prefixes, audit columns, soft delete; no foreign keys or CHECK constraints.],
  [Indexes that enforce invariants; aggregate-to-table mapping.],
  [Technical tables: outbox (V26), consumed messages (V28), scheduler lease (V13).],
  [Valkey key model; event envelope schema and SNS attributes; queue and DLQ settings.],
  [Transactions and concurrency: settlement lock order (AGENTS.md claims table).],
)
#diagram-slot("D9", [Logical data model per database (crow's foot). Relationships dashed: kept by the application.])

==== Data classification
#prompt[For each data group: data format; proposed storage technology; security requirements and controls; trade-off consideration.]
#table(
  columns: (auto, 1fr, 1fr, 1fr, 1fr),
  [Data group], [Data format], [Storage technology], [Security requirements and controls], [Trade-off],
  [Identity], [], [], [], [],
  [Commerce and payment], [], [], [], [],
  [Notifications], [], [], [], [],
  [Sessions], [], [], [], [],
  [Events], [], [], [], [],
)

=== Detailed Design

The two use cases below protect the same buyer at different points. When a buyer pays for the last seat, commerce must either admit that buyer and create an order, or record a refund. When the deadline passes with a seat still empty, commerce must close the team and record what it owes the buyers who paid. Both decisions use the team's database row to settle competing actions. The diagrams trace the implementation on the report baseline (5 November 2026, commit `174af508`).

*UC4 — joining by paying.* D11 shows the objects behind the webhook. The inbound adapter passes the raw request and signature to the payment service. Before opening a settlement transaction, the service verifies the signature and retrieves the session from Stripe. In the transaction it claims the event ID, locks the existing team for JOIN, locks the saved purchase intent, and then locks the payment. The business port lets payment request fulfilment while the order module owns the membership and order writes. The group aggregate checks its deadline, remaining seats and existing members. It returns the updated group snapshot to the application service. The repository implements its lock with PostgreSQL through MyBatis. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-application/src/main/java/com/blocbuy/payment/service/StripeCheckoutService.java")[Checkout and settlement] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-application/src/main/java/com/blocbuy/order/service/OrderFulfilmentAdapter.java")[Business port implementation] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-infrastructure/src/main/java/com/blocbuy/groupbuy/persistence/PaidGroupDealRepositoryImpl.java")[Group persistence].

#figure(
  image("../diagrams/d11-paid-participation-classes.svg", width: 95%, height: 14.0cm, fit: "contain"),
  caption: [D11. UML class diagram, paid participation. The payment service depends on the business port. A locked group decides whether to admit a paid buyer.],
) <d11-paid>

*How a paid refusal differs from a retry.* In D12 the business port returns a rejection for a full team. The payment service commits the obligation to refund the losing buyer without creating an order. A technical error instead rolls back the event claim and business writes, so a webhook redelivery can settle the payment. After a successful commit, a repeated event ID does no further work. The fulfilment adapter saves the order, links it to the membership, marks the intent fulfilled and consumes stock in the same transaction. On a full team the order listener runs before commit, after the last buyer's order exists, so both pending orders become confirmed. The concurrency test runs two payments against the remaining capacity. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/PaidGroupDealServiceIT.java")[Concurrency and replay tests] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/docs/adr/0001-payment-business-integration.md")[Settlement decision].

#page(flipped: true, margin: (x: 1.4cm, y: 1.2cm))[
  *UC3 START and UC4 JOIN — two buyers, one team* \
  The leader pays for a two-seat listing and shares the invitation. Opening it leaves one paid member. The joiner's successful payment fills the second seat. Each buyer sees their own order. The group status is shared. The 24 October 2026 recording on deployed dev showed the second buyer's receipt and the same group marked “2 of 2 joined” and “Filled”. #link("https://github.com/PPsyrius/iss-groupbuy-team8/issues/590")[Recorded walkthrough].

  #figure(
    image("../diagrams/d12-two-buyers-sequence.svg", width: 100%, height: 14.6cm, fit: "contain"),
    caption: [D12. UML sequence diagram, buyer and settlement viewpoints. The lower alternative shows a competing payment taking the final seat before the joiner obtains the team lock.],
  ) <d12-paid>
]

#page(flipped: true, margin: (x: 1.4cm, y: 1.2cm))[
*Team expiry and refunds.* D13 shows why expiry and refund execution are separate. Under the scheduler lease, the sweeper calls `expireOne` for each candidate team. The expiry service locks the team and checks the database clock again. If a concurrent join has already filled the team, expiry returns without publishing an event. If expiry wins, it writes the expired state and publishes `TeamExpired` in the transaction. Spring then invokes the order subscriber, the refund subscriber and, when integration events are enabled, the outbox writer. A failure in one listener rolls back that team's expiry, cancellations and refund obligations together. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-application/src/main/java/com/blocbuy/groupbuy/service/GroupDealExpiryService.java")[Expiry transaction] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/docs/adr/0002-team-lifecycle-events.md")[Subscriber order] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/TeamExpirySchedulingIT.java")[Rollback, retry and refund tests].

#figure(
  image("../diagrams/d13-expiry-classes.svg", width: 100%),
  caption: [D13. UML class diagram, expiry responsibilities. The expiry service publishes one event. Subscribers own order cancellation, the refund obligation and the integration record.],
) <d13-expiry>

*Design choices.* The Factory Method in `PaidGroupDealCreator` constructs a new paid team on START. JOIN always acts on the existing team. Observer lets a new listener handle `TeamExpired` without adding another call to the expiry service, but the listeners must finish before commit to preserve consistency. The Transactional Outbox stores the integration event with the business writes. Its relay can publish a duplicate if it succeeds at the broker and then loses its database update. The notification handler implements an Idempotent Receiver. It commits its consumed-message marker with the notification before acknowledging the queue message. The refund worker uses its own provider idempotency key for the separate risk of issuing a refund twice. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-domain/src/main/java/com/blocbuy/groupbuy/model/PaidGroupDealCreator.java")[Factory Method] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-infrastructure/src/main/java/com/blocbuy/integration/outbox/IntegrationOutboxWriter.java")[Outbox writer] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-application/src/main/java/com/blocbuy/notification/service/NotificationEventAppService.java")[Consumer transaction].
]

#page(flipped: true, margin: (x: 1.4cm, y: 1.2cm))[
  *After expiry — paying the buyer back* \
  The worker claims a refund, releases the database transaction, calls Stripe and records the result in another transaction. It reuses the refund ID as the basis of the provider key. If it knows the provider refund ID, it retrieves that refund. Beyond the safe creation window it looks up the refund and sends an uncertain outcome for review. Notification delivery can lag or retry independently of repayment. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-application/src/main/java/com/blocbuy/payment/service/RefundService.java")[Refund worker] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-domain/src/main/java/com/blocbuy/payment/model/RefundWork.java")[Provider key] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/174af508e7bd238b50faea7a9482e84e4d3a8cac/backend/blocbuy-application/src/main/java/com/blocbuy/common/service/OutboxRelayService.java")[Relay].

  #figure(
    image("../diagrams/d14-expiry-refund-sequence.svg", width: 100%, height: 14.4cm, fit: "contain"),
    caption: [D14. UML sequence diagram, transaction and recovery viewpoints. Synchronous listeners complete before commit. Stripe refund execution and event delivery proceed afterwards.],
  ) <d14-expiry>
]

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
#owner[Liu Jin]
#prompt[(Optional but it's important to acknowledge any known limitation of your solution before any stakeholder point them out to you) Include any outstanding issues from a solution architecture and high-level design perspective which need to be resolved. For resolutions that extend beyond existing project plans, there should be a roadmap with a timeline for closing the issue.]
#todo([Expand impact and description; add a roadmap date to every open item (confirm at the report meeting).])
The key outstanding architectural issues in the solution are as follows:
#table(
  columns: (auto, 1.4fr, 1fr, 1.2fr, auto, auto),
  [Identifier], [Issue and impact], [Description], [Resolution], [Owner], [Status],
  [AISS-01], [RDS, Valkey and NAT each in one zone], [], [Multi-AZ RDS, Valkey replica, NAT per zone], [Liu Jin], [Open],
  [AISS-02], [Commerce is large; payment shares the settlement transaction], [`catalog`, `groupbuy`, `order`, and `payment` deploy and scale together in `commerce-svc` (`AD-02`) to keep the 4-step settlement in one local ACID transaction.], [Extract `payment-svc` behind `PaymentIntentPort` / `PaymentBusinessPort` with an outbox-driven two-step settlement and refund saga (Phase 2 roadmap, Q1 2027).], [Eric], [Open],
  [AISS-03], [Checkout stops while identity is down], [], [By design; later cache or circuit breaker], [Yu Qianshuo], [Open],
  [AISS-04], [Internal endpoints rely on network rules only], [], [Service token or mutual TLS], [Pan], [Open],
  [AISS-05], [One shared database server], [], [Move a database to its own server], [Liu Jin], [Open],
  [AISS-06], [Environment build takes about 32 min (create, deploy and smoke, #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244")[run 39008208244], 17 Oct 2026); about 15 min of it is the 900 s SNS filter-policy wait (16 Oct 2026 startup record)], [], [Start early; edge stays permanent], [Pan], [Mitigated],
  [AISS-07], [Database and application shared subnets], [], [Data subnets (AD-15)], [Liu Jin], [Configured; AWS pending],
  [AISS-08], [CloudFront → ALB is plain HTTP], [], [HTTPS listener with ACM], [Pan], [Open],
  [AISS-09], [Valkey token and origin secret in Terraform state], [], [Rotate per session; write-only when possible], [Pan], [Accepted],
  [AISS-10], [Shared session store], [All three services read login sessions and RBAC snapshots from one Valkey node (`AD-05`); node loss signs out active users, and role updates outside `identity-svc` take effect at next login.], [Issue short-lived signed JWT access tokens verified at the edge with Valkey-backed refresh tokens and `UserDisabled` event revocation (Phase 2 roadmap, Q1 2027).], [Eric], [Open],
  [AISS-11], [Events eventually consistent (about 2 s)], [`OutboxRelayService` polls `sys_outbox` every ~2 s under `LeasedJobRunner` (`AD-04`), so in-app notifications lag the triggering checkout or expiry commit by ~2 s.], [Accepted by design; the SPA polls and displays an updating state while REST APIs on `commerce-svc` remain the immediate source of truth.], [Eric], [By design],
  [AISS-12], [Linear scaling not proven beyond three tasks], [], [Distributed load generator; rerun V3], [Pan], [Open],
  [AISS-13], [V4, V5, V7 and identity outage run locally only], [], [Run the drills on AWS], [Yu Qianshuo], [Open],
  [AISS-14], [Bootstrap retry can restore scaling suspension; SCP denied a metric read], [], [Retry regression and fix], [Liu Jin], [Open],
  [AISS-15], [`CloudWatchFullAccessV2` not fenced to SWE5001], [], [Narrow to SWE5001 names], [Pan], [Accepted],
)
