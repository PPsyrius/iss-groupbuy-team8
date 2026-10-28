<!-- report
rubric: 3.1
owner: Liu Jin
plan: #677
sources: figures/logical-layers.svg (portrait copy of ../presentation/src/diagrams/logical-layers.svg), figures/dev-deployment.svg; backend/docs/adr/; frontend/docs/adr/
owns: R33, R43, R52
status: draft
-->

# 3 System Design

## 3.1 Software Architecture

The SWE5006 system (section 1.1) is BlocBuy's `dev` environment, a modular monolith. One Spring Boot application, split into the four COLA layers and, inside each layer, one package per bounded context, serves a React single-page application (SPA). On `dev` the backend runs as one Docker container on one EC2 host behind CloudFront, with PostgreSQL 18 and Redis 8 on the same host. Stripe (test mode) and Google sign-in are the only external systems. The same code can also be assembled as three services for the SWE5001 course, and nothing in the design sections depends on that (SWE5001 topology, section 1.4).

#### Logical view: layers and bounded contexts

The backend follows the COLA style of Domain-Driven Design layering, decided on 30 August before the first sprint (Table B.3). Each layer is one Maven module:

Table 3.1: Backend Maven modules of the monolith, their layer and their allowed dependencies.

| Module | Layer | Holds | May depend on |
| --- | --- | --- | --- |
| `blocbuy-adapter` | Adapter (presentation) | REST controllers, request and response DTOs, the `Result<T>` envelope, `GlobalExceptionHandler` | application, client |
| `blocbuy-application` | Application | Use-case services that own the transaction boundary (`@Transactional`), commands, queries, assemblers, event subscribers | domain, client |
| `blocbuy-domain` | Domain | Aggregates, value objects, domain events, repository and gateway ports (interfaces), error codes | JDK and Lombok only |
| `blocbuy-infrastructure` | Infrastructure | MyBatis mappers, data objects, repository implementations, hand-written converters, Redis, Stripe, Google and AWS SDK adapters, Sa-Token configuration | domain, client |
| `blocbuy-client` | Shared contract | Constants (`ApiConstants`, `SessionRbac`, `Topology`) and versioned integration-event contracts (`IntegrationEventEnvelope`, `TeamSucceededV1`) | none |
| `blocbuy-bootstrap` | Assembly | `BlocBuyApplication`, configuration, scheduled jobs (`LeasedJobRunner`, `TeamExpirySweepJob`, `RefundWorkerJob`), Flyway migrations | all five modules above |

The reactor also contains `blocbuy-platform` (topology guard, trace propagation) and `services/identity-svc`, `services/commerce-svc` and `services/notification-svc`. They assemble the SWE5001 services from the same modules; the monolith never depends on them and they are not deployed on `dev`.

Dependencies point inward: adapter → application → domain, and infrastructure → domain. Infrastructure realises the domain's ports, so the domain never sees a framework type. This is the "dependency inversion" form of layering; the reasons and the effect on every analysis object are in section 3.2.1.

Every layer is cut by the same seven packages: six bounded contexts and a shared kernel. `catalog` holds products and team terms; `groupbuy` holds teams and their members, including share invites and referral credits; `order` holds purchase intents and orders; `payment` holds Stripe Checkout payments, refunds and the admin review queue; `identity` holds users, Google sign-in and role-based access control (RBAC); `notification` holds in-app notifications; `common` holds the error-code system, paging and the scheduler lease. Infrastructure adds one more package, `integration`, for the transactional outbox. Inside the domain module every bounded context has `model/`, `repository/`, `event/` and `exception/`. Only catalog, groupbuy, identity, payment and the shared kernel have `gateway/`, and a few add their own: `pricing/` in catalog, `query/` in groupbuy and `util/` in common. Figure 3.1 shows the result as a UML package diagram, with the frontend on top.

![Figure 3.1: Logical view of the SWE5006 system as a UML package diagram, frontend and the five backend modules by bounded context. Dependencies point down toward the domain, and infrastructure realises the domain's ports.](../figures/logical-layers.svg)

**Ports and adapters.** The domain declares 20 repository ports, one per aggregate root or read model (for example `GroupDealRepository` and `RefundRepository`), and 12 gateway ports for everything a context needs from outside itself. Figure 3.2 shows four of them, who calls them and who realises them. Three ports are realised in the application layer, because they connect two of our own contexts and no external system: the payment context asks `PaymentIntentPort` for the server-owned price and `PaymentBusinessPort` to fulfil a paid purchase, and the order context answers through `PurchaseIntentAppService` and `OrderFulfilmentAdapter`; the groupbuy context reads product titles and cover images through `ListingDisplayGateway`, which catalog answers with `GroupbuyListingDisplayAdapter`. Payment therefore never imports a groupbuy or order class, and groupbuy never imports catalog.

```mermaid
classDiagram
    class StripeCheckoutService {
        <<control>>
    }
    class RefundService {
        <<control>>
    }
    class OAuthLoginAppServiceImpl {
        <<control>>
    }
    class CheckoutGateway {
        <<interface>>
    }
    class PaymentBusinessPort {
        <<interface>>
    }
    class RefundGateway {
        <<interface>>
    }
    class OAuthIdentityGateway {
        <<interface>>
    }
    class StripeCheckoutGateway
    class OrderFulfilmentAdapter
    class StripeRefundGateway
    class GoogleOAuthGateway
    %% at StripeCheckoutService 0,0
    %% at RefundService 0,2
    %% at OAuthLoginAppServiceImpl 0,3
    %% at CheckoutGateway 1,0
    %% at PaymentBusinessPort 1,1
    %% at RefundGateway 1,2
    %% at OAuthIdentityGateway 1,3
    %% at StripeCheckoutGateway 2,0
    %% at OrderFulfilmentAdapter 2,1
    %% at StripeRefundGateway 2,2
    %% at GoogleOAuthGateway 2,3
    StripeCheckoutService --> CheckoutGateway
    StripeCheckoutService --> PaymentBusinessPort
    RefundService --> RefundGateway
    OAuthLoginAppServiceImpl --> OAuthIdentityGateway
    StripeCheckoutGateway ..|> CheckoutGateway
    OrderFulfilmentAdapter ..|> PaymentBusinessPort
    StripeRefundGateway ..|> RefundGateway
    GoogleOAuthGateway ..|> OAuthIdentityGateway
```

Figure 3.2: Ports and adapters, UML class diagram (left column application services, middle column domain ports, right column realisations). Every external call goes through a domain interface; `OrderFulfilmentAdapter` is the one realisation drawn here that lives in the application layer. The figure shows 4 of the 12 gateway ports; the settlement seams between payment, order, groupbuy and catalog are Figure 3.12.

The figure leaves out eight ports that follow the same pattern: the two cross-context ports named above, the outbox event transport with its SNS and SQS realisations (off on dev, section 1.4), four ports realised on Redis (rate limits, the open-team cache, sign-in state and login snapshots, the last by `RedisLoginSnapshotGateway`) and the listing search reranker, realised by `TypeSafeJevListingSearchReranker`.

**Enforcement.** `ArchitectureTest` in `blocbuy-bootstrap` checks these rules in every build with 17 ArchUnit rules:

<!-- table: split -->
Table 3.2: The 17 ArchUnit rules in `ArchitectureTest`, in five groups: layering, coding rules, context boundaries, data ownership and internal API.

| Rule | What it stops |
| --- | --- |
| `domain_is_framework_free` | Layering: Spring, MyBatis or Redis types in the domain |
| `domain_has_no_outward_dependencies` | Layering: the domain depending on application, adapter or infrastructure |
| `application_uses_ports_not_tech` | Layering: an application service calling a MyBatis mapper or `RedisTemplate` |
| `controllers_never_touch_persistence` | Layering: a controller reaching persistence |
| `persistence_is_internal` | Layering: a mapper, data object or converter leaving infrastructure |
| `no_field_injection` | Coding: field injection instead of constructor injection |
| `controllers_are_validated` | Coding: a controller without class-level `@Validated` |
| `numeric_request_params_have_range_constraints` | Coding: a numeric query parameter without `@Min`/`@Max` |
| `commerce_reaches_identity_only_through_user_port` | Boundary: catalog, groupbuy, order or payment using identity other than through `UserAppService` |
| `only_identity_reads_current_user` | Boundary: another context calling `UserAppService.currentUser`, which the remote identity client refuses |
| `nothing_depends_on_notification` | Boundary: any code depending on notification, which is reached by events only |
| `notification_depends_on_no_other_service` | Boundary: notification depending on commerce or identity code |
| `identity_persistence_is_owned` | Data: another context touching identity persistence |
| `commerce_persistence_is_owned` | Data: identity or notification touching commerce persistence |
| `notification_persistence_is_owned` | Data: another context touching notification persistence |
| `internal_controllers_are_hidden_and_distributed_only` | Internal API: a service-to-service controller active in the monolith or published in the contract |
| `internal_paths_live_in_internal_controllers` | Internal API: an `/internal/**` path outside the internal controllers |

The last three groups keep the SWE5001 split possible. In the monolith they keep the contexts loosely coupled.

**API surface.** The adapter layer has 14 `@RestController` classes and two exception advice classes (`GlobalExceptionHandler`, `StripeRequestExceptionHandler`). Thirteen controllers serve the monolith; `InternalUserController` is active only in the distributed topology. The contract is OpenAPI 3.1, generated by springdoc and committed as `contracts/openapi.json`: 27 paths and 31 operations under `/api/v1`. CI fails a pull request whose committed contract differs from the code. Every response, success or error, is one `Result<T>` envelope with a stable code; success is `COMMON_200_000` (`CommonErrorCode`). This replaced, on 11 September, an earlier choice of bare DTOs for success and ProblemDetail for errors (Table B.3), and the frontend handles it through a single `unwrapResult` function (frontend ADR 0009).

**Frontend.** The SPA is rendered only in the browser (frontend ADR 0001). It has three layers of its own: `src/routes` (TanStack Router file routes, which own URL state; signed-in pages sit under `_authenticated`), `src/features/<area>` (`catalog`, `session`, `payment`, `share`, `blocs`, `order`, `admin`; components and TanStack Query hooks) and `src/api` (the generated client, `client.ts` with a 401 interceptor and `envelope.ts`). The client is generated from the committed contract at build time and never committed (frontend ADR 0003). The SPA calls the API on its own origin under `/api` with an HttpOnly session cookie, so no token reaches JavaScript (frontend ADR 0002).

#### Component and deployment view of the SWE5006 system

On `dev` the backend is one deployable unit, so its component boundaries are physical, and Figure 3.3 is both the component and the deployment view. It is a UML deployment diagram drawn from the AWS account, read-only, on 24 October; the resource identifiers are in Table E.5.

![Figure 3.3: Deployment view of the SWE5006 dev system, UML deployment diagram with «device», «node» and «executionEnvironment». One CloudFront distribution splits the SPA from the API, and the whole backend with its database and cache runs on one EC2 host.](../figures/dev-deployment.svg)

The buyer's browser reaches one CloudFront distribution over HTTPS. CloudFront serves `/*` from a private S3 bucket through origin access control, and forwards `/api/*` to an Application Load Balancer (ALB). The ALB accepts traffic only from CloudFront's address range and only when CloudFront's secret origin header is present. It forwards to port 8589 on the EC2 host (t3.large, Ubuntu 24.04), whose security group admits that port only from the load balancer. There the backend container runs read-only, without Linux capabilities, on the host network, and reaches PostgreSQL 18 and Redis 8 on the loopback interface. PostgreSQL holds every table and is the source of truth for teams, orders and payments; Redis holds only short-lived data: Sa-Token sessions, sign-in state, rate-limit counters and a cache of open teams. The backend calls Stripe and Google over HTTPS, and Stripe calls the backend back with signed webhooks through the same CloudFront path. Container logs go to CloudWatch Logs.

Delivery uses the same resources. CI on GitHub-hosted runners authenticates to AWS with one OpenID Connect (OIDC) role per job, pushes the backend image to ECR with an immutable tag, and asks Systems Manager Run Command to pull it on the host by digest; the frontend bundle is synchronised to S3 and CloudFront is invalidated. Section 4.3 describes the pipeline and its gates.

The system has one host and no auto-scaling group (single host, section 4.3). We configured PostgreSQL, Redis, the host and the ALB by hand; the pipeline does not manage them. The host's other gaps are open issues (open issues in Table 2.6). No message broker runs on `dev`, so in-app notifications do not run there (off on dev, section 1.4).

#### Target implementation platform

Table 3.3 lists the platform. Versions are those of `backend/pom.xml`, `frontend/package.json` and the Docker files at the freeze commit (section 1.1).

Table 3.3: Target implementation platform of the SWE5006 system.

| Layer | Technology | Version | Notes |
| --- | --- | --- | --- |
| Backend language and runtime | Java (Eclipse Temurin JRE) | 21 | Container base `eclipse-temurin:21-jre-noble`, pinned by digest |
| Backend framework | Spring Boot | 4.1.1 | Parent BOM; Tomcat overridden to 11.0.25 for CVE fixes |
| Persistence | MyBatis Spring Boot starter, XML mappers | 4.1.0 | Hand-written SQL; no JPA |
| Schema migration | Flyway | 13.5.0 | Overrides the Boot BOM, whose Flyway does not support PostgreSQL 18 (migrations, section 3.6) |
| Authentication and RBAC | Sa-Token with `sa-token-redis-jackson` | 1.46.0 | Server session in Redis; roles and permissions from SQL tables |
| Payment provider SDK | stripe-java | 34.0.0 | Infrastructure module only; Stripe test mode |
| Sign-in provider SDK | google-api-client | 2.9.1 | Google OpenID Connect `id_token` verification |
| AWS SDK | AWS SDK for Java | 2.55.16 | SNS and SQS transports (off on dev, section 1.4) |
| API documentation | springdoc-openapi | 3.1.1 | Exports `contracts/openapi.json` (OpenAPI 3.1) |
| Architecture tests | ArchUnit | 1.5.1 | 17 rules (Table 3.2) |
| Integration tests | Testcontainers | Spring Boot BOM | `postgres:18` and `redis:8.10` images |
| Database | PostgreSQL | 18 | One database, schema `domain`; native `uuidv7()` |
| Cache and session store | Redis | 8 | Sessions, OAuth state, rate limits and invite lookup cache |
| Frontend language | TypeScript | 7.0.2 | Node 24, pnpm 12.4.2 |
| UI library | React | 19.3.1 | React Compiler through the Babel preset (frontend ADR 0010) |
| Routing, server state, forms | TanStack Router, Query, Form | 1.170.44, 5.104.3, 1.33.5 | |
| Components and styling | HeroUI, Tailwind CSS | 3.2.6, 4.3.3 | |
| Validation | Valibot | 1.5.0 | Schemas generated with the client |
| API client generator | hey-api `openapi-ts` | pre-release build `0.0.0-next-20260824173136` | Output gitignored (frontend ADR 0003) |
| Build tool | Vite | 8.3.4 | |
| Frontend tests | Vitest, Playwright | 5.0.1, 1.64.1 | MSW for API mocks |
| Cloud (SWE5006 `dev`) | AWS CloudFront, S3, ALB, EC2, ECR, Systems Manager, CloudWatch Logs | n/a | Region ap-southeast-1 |
| External services | Stripe Checkout (test mode), Google OAuth 2.0 | n/a | |

Sa-Token and MyBatis differ from the proposal (changes from the proposal: Table 2.5).

#### Architectural decisions

Each architectural decision is recorded either as a numbered ADR or as one of the decisions locked on 30 August, before the first sprint (Table B.3). Table 3.4 lists the decisions that shape the architecture above; Appendix B indexes all 24 ADRs (8 backend, 16 frontend) and lists the decisions that have no ADR.

Table 3.4: Architectural decisions behind section 3.1 and where each is recorded.

| Decision | Record | Date |
| --- | --- | --- |
| Modular monolith in COLA layers, one package per bounded context | Locked decision (Table B.3) | 30 Aug 2026 (payment context added 31 Aug) |
| Framework-free domain (JDK and Lombok only), tested by ArchUnit | No ADR (Table B.4) | 30 Aug 2026 |
| PostgreSQL and Redis only; no message broker or search engine | Locked decision (Table B.3); amended by backend ADR 0007 for SNS and SQS | 30 Aug 2026; 11 Oct 2026 |
| Sa-Token session in Redis, RBAC from SQL tables | Locked decision (Table B.3); frontend ADR 0002 for the cookie | 30 Aug 2026; 17 Sep 2026 |
| Pure client-side SPA on S3 and CloudFront | Frontend ADRs 0001, 0005 | 17 Sep 2026 |
| Same-origin `/api` with an HttpOnly cookie session | Frontend ADR 0002 | 17 Sep 2026 |
| Committed OpenAPI contract, generated client not committed | Locked decision (Table B.3); frontend ADR 0003 | 30 Aug 2026; 17 Sep 2026 |
| One `Result<T>` envelope for every response | Supersedes a locked decision (Table B.3); frontend ADR 0009 for the client | 11 Sep 2026; 16 Sep 2026 |
| Purchase intent in order, team terms on the product | Backend ADR 0001 | 22 Sep 2026 |
| A team's consequences run before the commit | Backend ADR 0002 | 22 Sep 2026 |
| Referral credit after the commit | Backend ADR 0003 | 25 Sep 2026 |
| Stripe Hosted Checkout with a server-owned intent | Frontend ADR 0014; backend ADR 0005 for the feature flag | 23 Sep 2026; 30 Sep 2026 |
| Transactional outbox to SNS and SQS for cross-service events | Backend ADR 0007 (status still "Proposed") | 11 Oct 2026 |
| One codebase, two deployment topologies | Backend ADR 0008 | 12 Oct 2026 |
