# SWE5001 Implementation Plan: Backend Code Architecture

Detailed backend work for the [refactoring plan](microservice-refactoring-plan.md), sections 2.3–2.6. Companion plans: [GitHub configuration](impl-github-configuration.md), [AWS infrastructure](impl-aws-infrastructure.md), [verification and observability](impl-verification-observability.md).

Status: **Draft, 2026-09-29.** Facts are taken from `dev` at `c66bb40`.

---

## 1. Goal and invariants

Package the existing code as three services (`identity-svc`, `commerce-svc`, `notification-svc`) without moving business code, while the SWE5006 monolith keeps running unchanged.

| Invariant | Check |
| --- | --- |
| `blocbuy.topology` defaults to `monolith`; the monolith behaves exactly as today | Existing `./mvnw verify` suite (unit tests and PostgreSQL ITs) stays green on every PR |
| No file in the shared COLA modules is moved or renamed | PR review; `git diff --name-status` shows no `R` entries under `backend/blocbuy-*`. One deliberate exception (2026-10-12, #455 A2): `RoleCode` moved from `com.blocbuy.identity.model` to `com.blocbuy.common.model` so commerce controllers stop importing identity's model |
| The public API contract is unchanged | `contract-check` job; `contracts/openapi.json` diff is empty unless a feature adds endpoints |
| `/internal/**` never appears in `contracts/openapi.json` | Test in `blocbuy-bootstrap` asserts no `/internal` path in the exported contract |
| No business write spans two services | ArchUnit rules (section 6) |

---

## 2. Current-state findings that drive the work

| # | Finding (at `c66bb40`) | Consequence |
| --- | --- | --- |
| F1 | `blocbuy-bootstrap` is a repackaged Spring Boot jar (`finalName` `blocbuy-backend`) that also holds `db/migration`, `application*.yml`, Stripe configuration (`config/Stripe*`, `PaymentIntegrationConfiguration`), scheduling (`scheduling/*`) and `TraceIdFilter` | Service modules cannot depend on it as a library until the repackaged jar gets a classifier (WP-B2). **Resolved 2026-10-12 (#455 step A1):** the main artifact is now the plain `blocbuy-backend.jar` and the executable is `blocbuy-backend-exec.jar` |
| F2 | `StpInterfaceImpl` (`blocbuy-infrastructure`, `com.blocbuy.common.config`) reads roles and permissions from `RoleRepository` on every request | Outside `identity-svc` there is no identity database, so roles must come from the shared Sa-Token session (WP-B4) |
| F3 | `MyBatisConfig` uses `@MapperScan(basePackages = "com.blocbuy")` | Every service would register every mapper. Scope it per assembly (WP-B2) |
| F4 | Outside identity, `UserAppService` is called only through `requireActive` (catalog `AdminListingAppServiceImpl`, `TeamPriceAppServiceImpl`; order `PurchaseIntentAppService`, `OrderFulfilmentAdapter`). `currentUser` is called only by identity's `AuthController` | The remote adapter needs one real method; `currentUser` can refuse (WP-B5) |
| F5 | Domain events with payloads: `TeamSucceeded`, `TeamExpired`, `MemberJoined` (groupbuy), `PaymentSucceeded` (payment). Notification has no subscriber today | The outbox publishes `TeamSucceeded`, `TeamExpired`, `PaymentSucceeded`; notification gains consumers (WP-B6, WP-B7) |
| F6 | Public prefixes: identity `/api/v1/auth`, `/api/v1/users`; notification `/api/v1/notifications`; everything else commerce (`/products`, `/admin/products`, `/group-deals`, `/orders`, `/payments`, `/webhooks`) | ALB routing rules in the AWS plan match these prefixes exactly |
| F7 | Redis settings expose host, port and password only; no TLS switch | ElastiCache with in-transit encryption needs `spring.data.redis.ssl.enabled` (WP-B3) |

---

## 3. Target module layout

```
backend/
  pom.xml                       # adds modules below; no existing module changes position
  blocbuy-client/ domain/ application/ adapter/ infrastructure/   # unchanged paths
  blocbuy-bootstrap/            # monolith; now also published as a plain library jar (classifier change)
  blocbuy-platform-starter/     # NEW library: topology switch, outbox, idempotent consumer, internal-call auth,
                                #   session-based StpInterface, remote UserAppService adapter
  services/
    identity-svc/               # NEW: IdentityServiceApplication + application-identity.yml
    commerce-svc/               # NEW: CommerceServiceApplication + application-commerce.yml
    notification-svc/           # NEW: NotificationServiceApplication + application-notification.yml
```

### Entry points

| Service | Component scan | Mapper scan | Imports from `blocbuy-bootstrap` |
| --- | --- | --- | --- |
| `identity-svc` | `com.blocbuy.identity`, `com.blocbuy.common`, `com.blocbuy.platform` | `com.blocbuy.identity` | `TraceIdFilter` |
| `commerce-svc` | `com.blocbuy.catalog`, `.groupbuy`, `.order`, `.payment`, `com.blocbuy.common`, `com.blocbuy.platform` | the four commerce packages | `config/*` (Stripe, payment integration, scheduling), `scheduling/*`, `TraceIdFilter` |
| `notification-svc` | `com.blocbuy.notification`, `com.blocbuy.common`, `com.blocbuy.platform` | `com.blocbuy.notification` | `TraceIdFilter` |

Imports use `@Import` or `scanBasePackages` on `com.blocbuy.bootstrap.config` and `com.blocbuy.bootstrap.scheduling` only. The root package `com.blocbuy.bootstrap` is never scanned, because it holds `BlocBuyApplication`, whose `scanBasePackages = "com.blocbuy"` would pull in everything.

### Configuration layering

- `application.yml` in `blocbuy-bootstrap` stays the shared base on every classpath.
- Each service ships `application-<service>.yml` and activates it with `SPRING_PROFILES_ACTIVE=<service>,aws`.
- `application-aws.yml` (new, in `blocbuy-platform-starter`) holds ECS-only settings: Redis TLS, SNS/SQS endpoints, `blocbuy.topology=distributed`, JSON logs to stdout.

---

## 4. Topology switch

```yaml
blocbuy:
  topology: monolith        # monolith | distributed
```

| Bean | `monolith` (default, `matchIfMissing = true`) | `distributed` |
| --- | --- | --- |
| `UserAppService` | `UserAppServiceImpl` (unchanged, active only where identity is scanned) | `RemoteUserAppService` in `commerce-svc`; `UserAppServiceImpl` in `identity-svc` |
| `StpInterface` | `StpInterfaceImpl` (unchanged DB lookup) | `SessionStpInterface` (roles from `SaSession`) outside `identity-svc` |
| Outbox writer and relay | absent | active in `commerce-svc` |
| SQS listener container | absent | active in `notification-svc` |
| `MyBatisConfig` | unchanged global scan | disabled; each service entry declares its own `@MapperScan` |

Rule: every conditional bean pair is covered by a context test for both values (`ApplicationContextRunner`), so a missing or duplicate bean fails in CI.

---

## 5. Work packages

Each package is one PR to `dev`. WBS numbers refer to the proposal's Table 7.

### WP-B0 Decision records (WBS 1.1)

- `backend/docs/adr/0008-dual-deployment-topology.md` (next free number; 0005 and 0006 were taken by payment decisions): two shapes from one codebase, `blocbuy.topology`, split by assembly.
- [`backend/docs/adr/0007-cross-service-events-sns-sqs.md`](../../backend/docs/adr/0007-cross-service-events-sns-sqs.md) (#443, 2026-10-11): outbox, one SNS topic with filter policies, SQS + DLQ per consumer, at-least-once plus idempotent consumer. Amends the "no message broker" rule for both shapes: `blocbuy.events.transport` selects ElasticMQ (`sqs`) for local and CI, and SNS for ECS; the EC2 `dev` stack keeps the relay and consumer off. Implementation is tracked in #450.
- Amend `.claude/skills/backend-constraints-skill/SKILL.md` and `references/messaging-consistency.md` to point at both ADRs.
- **Done when:** SWE5006 team agrees at stand-up; ADRs merged before WP-B6.
- **2026-10-12 (#455 A2):** [backend ADR 0008](../../backend/docs/adr/0008-dual-deployment-topology.md) written as Proposed and indexed; the backend-constraints skill points at it.

### WP-B1 Architecture guardrails first (WBS 2.1)

- ArchUnit rules in the existing `ArchitectureTest` (section 6).
- Test that `/internal/**` is absent from the exported OpenAPI contract.
- **Done when:** rules pass on current code, so later PRs cannot regress.
- **Done 2026-10-12 (#455 A2):** nine rules added to `ArchitectureTest` (section 6, as amended) and `InternalPathsContractTest`; each rule except the notification-inbound and two persistence-ownership rules was also shown to fail on a deliberate violation.

### WP-B2 Packaging: library bootstrap and service modules (WBS 3.3)

- `blocbuy-bootstrap/pom.xml`: set `spring-boot-maven-plugin` `<classifier>exec</classifier>`; the fat jar becomes `blocbuy-backend-exec.jar`.
- Update `backend/Dockerfile` and `backend/Dockerfile.ci` `COPY` to the `-exec` jar. **This touches the SWE5006 image build: coordinate the merge and confirm the EC2 deploy after merge.**
- **Done 2026-10-12 (#455 step A1, [plan](../agile/sprint-04/exec-jar-classifier-plan-20261012.md)):** classifier set; every consumer (both backend Dockerfiles, `fullstack-localhost/backend.Dockerfile`, `ci-backend.yml`, `swe5001-service-images.yml`) uses the `-exec` jar; `backend/.dockerignore` admits only that jar and `.github/scripts/assert-boot-jar.sh` rejects a non-executable jar before upload. The `dev` deploy check after merge is still owed.
- Make `MyBatisConfig` `@ConditionalOnProperty(name = "blocbuy.topology", havingValue = "monolith", matchIfMissing = true)`.
- Add `services/*-svc` modules with entry classes, `application-<service>.yml`, per-service `@MapperScan`, and a context-load test per service against Testcontainers PostgreSQL and Redis.
- One Dockerfile with `ARG MODULE` for all three services (see the GitHub plan for the build matrix).
- **Done when:** three service jars boot locally with `topology=distributed` stubs, and the monolith image and EC2 deploy are unchanged in behaviour.
- **2026-10-12 (#455 A3, [plan](../agile/sprint-04/service-assemblies-plan-20261012.md)):** `services/identity-svc`, `commerce-svc` and `notification-svc` (package `com.blocbuy.service.<service>`) with per-service scans, mapper XML locations and a Testcontainers context IT each; `MyBatisConfig` is monolith-only through the `Topology` constants in `blocbuy-client`. The commerce and notification ITs mock `RoleRepository` and `UserAppService` until #467 and #468; the shared Dockerfile with `ARG MODULE` moves to C1 (#456).

### WP-B3 Platform library (WBS 2.1)

`blocbuy-platform-starter` contains:

- `TopologyProperties` (`blocbuy.topology`).
- `application-aws.yml`: `spring.data.redis.ssl.enabled: ${REDIS_SSL:false}`, JSON console logging, `management.endpoints.web.exposure.include: health,info,prometheus`.
- Internal-call authentication: `/internal/**` requires header `X-Internal-Token` equal to SSM parameter `/blocbuy/swe5001/shared/INTERNAL_TOKEN`; Sa-Token route excluded for `/internal/**`, custom filter enforces the token. Network isolation (security groups) is the primary control; the token is defence in depth.
- Trace propagation: outbound `RestClient` interceptor copies `X-Trace-Id`; SQS messages carry `traceId` as a message attribute and the consumer restores MDC.
- **Done when:** unit tests cover token accept/reject and trace propagation.
- **2026-10-12 (#455 A3), as rescoped on 2026-10-11:** the library is `blocbuy-platform` (not a starter the monolith loads; only `services/*` depend on it) with `TopologyProperties` (enum binding, fail-fast unless `distributed`), a `RestClientCustomizer` that copies the MDC trace id into `X-Trace-Id`, and `application-aws.yml` (Redis TLS). JSON logs are a `springProfile name="aws"` block in bootstrap's `logback-spring.xml`. No internal-call token. Outbox, SQS trace attributes, session `StpInterface` and the remote adapter stay with D, #467 and #468.

### WP-B4 Session-based authorisation (WBS 3.2)

- At login (`AuthController` Google callback), after `StpUtil.login`, write `roles` and `permissions` into `StpUtil.getSession()`. Runs in both topologies (harmless for the monolith).
- `SessionStpInterface` reads those keys; active when `topology=distributed` and identity is not scanned.
- Role changes take effect at next login. Record this in the dual-topology ADR (0008).
- **Done when:** a test logs in, then resolves roles through `SessionStpInterface` with no identity repository in the context.

- **2026-10-12 (#467, including review fixes):** `LoginAuthorizationService` reads roles and permissions in a read-only REPEATABLE READ transaction; `LoginSessionPublisher` creates an unpublished fresh token, then `RedisLoginSnapshotGateway` atomically fences and persists a dedicated per-account Sa-Token authorization session before publishing the browser cookie/header. The fence lasts 120 s; newer attempts supersede older callbacks, and the snapshot inherits the account session's remaining TTL. Both mandatory lists must be valid or all RBAC is denied. SQL remains authoritative in monolith/identity; distributed services without `RoleRepository` use this shared account snapshot without SQL fallback. Failed callbacks redirect and revoke only the new token, preserving existing logins. Service IT role mocks are removed; the commerce user-service mock remains for #468. Permanent Redis tests cover callback failures, old-token preservation, partial corruption and delayed concurrent callbacks. Design: [ADR 0008](../../backend/docs/adr/0008-dual-deployment-topology.md); verification limits: [day log](../agile/sprint-04/log/2026-10-12.md).

### WP-B5 Remote identity check (WBS 3.2)

- `identity-svc`: `InternalUserController` `GET /internal/users/{id}/active` → `204` when active, `404` when missing, `409` when disabled; delegates to `UserAppServiceImpl.requireActive`.
- `commerce-svc`: `RemoteUserAppService implements UserAppService`:
  - `requireActive` calls `http://identity-svc:8589/internal/users/{id}/active` through Service Connect with connect timeout 500 ms and read timeout 1500 ms, and maps `404`/`409` to the same `IdentityErrorCode` exceptions the local implementation throws;
  - timeouts and `5xx` throw `IdentityUnavailableException` (unchecked, not a `BizException`), so `OrderFulfilmentAdapter.rejects` does not turn them into `BUYER_INACTIVE`: the webhook transaction rolls back, the global handler answers 500 and Stripe redelivers; user requests fail closed with 500;
  - `currentUser` throws `UnsupportedOperationException` (only `AuthController` uses it, and it runs in `identity-svc`).
- **Done when:** contract test runs the same `requireActive` cases against both implementations; WireMock test covers timeout and `5xx`; a webhook test proves a timeout returns 500, writes no `BUYER_INACTIVE` outcome and succeeds on redelivery.
- **Amended 2026-10-13 (#468, [plan](../agile/sprint-04/remote-identity-plan-20261013.md)):** the endpoints use the `Result` envelope, so disabled is 403 `IDENTITY_403_001` (not 409) and the adapter maps refusals by status and identity code together; a route 404 is an outage. `GET /internal/users/{id}/public-nickname` answers 200 with a nullable nickname. The adapter lives in `services/commerce-svc` (`com.blocbuy.service.commerce.identity`), bound to `blocbuy.identity.base-url` = `IDENTITY_BASE_URL`, not in the platform starter. The batch nickname endpoint follows #469's port method.

### WP-B6 Transactional outbox and publisher (WBS 2.2)

- Migration `V<next>__sys_outbox.sql` in the shared stream: `sys_outbox (id uuid v7, event_type varchar(64), aggregate_id uuid, payload jsonb, trace_id varchar(64), created_at, published_at null, attempts int)`, partial index on `published_at is null`. Follow the `pgsql-ddl` skill conventions.
- `OutboxWriter`: `@TransactionalEventListener(phase = BEFORE_COMMIT)` for `TeamSucceeded`, `TeamExpired`, `PaymentSucceeded`; serialises a versioned envelope `{eventId, type, version, occurredAt, traceId, payload}`. Existing subscribers keep their order; the writer uses a lower precedence so it runs last.
- `OutboxRelay`: scheduled every 2 s under the existing lease mechanism (`LeasedJobRunner`, `sys_scheduler_lock`); publishes up to 100 rows per tick to SNS topic `blocbuy-swe5001-<event>`; marks `published_at`; backs off on failure.
- Event contracts live in `blocbuy-client` (`com.blocbuy.<context>.event.contract`) so producer and consumer share one schema.
- **Done when:** IT with LocalStack (SNS/SQS) shows one message per committed event and none for a rolled-back transaction.

### WP-B7 Notification consumers (WBS 3.1)

- Migration `V<next>__sys_consumed_message.sql`: `(message_id uuid primary key, consumer varchar(64), consumed_at)`.
- SQS listener (Spring Cloud AWS `@SqsListener`, or a small polling loop if the Spring Boot 4 line is not yet supported; decide in the PR by checking the official compatibility matrix) on queue `blocbuy-swe5001-notification`.
- Handler inserts `sys_consumed_message` and the `biz_notification` rows in one transaction; duplicate `message_id` is a no-op.
- Recipients: the event payload carries member user IDs, so notification never calls commerce. If a payload lacks them, extend the event record (additive field).
- **Done when:** duplicate delivery test creates one notification; a poison message reaches the DLQ after 5 receives.

### WP-B8 Per-service data access (WBS 3.4)

- All three services run the full `db/migration` stream (plan section 2.6); Flyway runs as the database owner role, never as the runtime role.
- Repeatable migration `R__swe5001_grants.sql` in a location loaded only by service profiles (`classpath:db/grants/<service>`): grants the service's runtime role `SELECT, INSERT, UPDATE, DELETE` on owned tables only.
- Each service connects at runtime as its own role (`blocbuy_identity_app`, `blocbuy_commerce_app`, `blocbuy_notification_app`); Flyway uses the owner credentials (`SPRING_FLYWAY_USER`/`PASSWORD`).
- **Done when:** IT proves `commerce` role gets `permission denied` on `sys_user` and `biz_notification`.
- **Amended 2026-10-12 (#455 A4):** implemented as a Flyway `afterMigrate` callback, `blocbuy-platform/src/main/resources/db/swe5001/afterMigrate__service_grants.sql`, not `R__swe5001_grants.sql`: `db-init`'s default privileges grant every new table to the runtime role, and only a callback re-checks them on every start. One file for all services (placeholders `service` and `approle`); `ServiceGrantsIT` proves the denials (`biz_notification` does not exist yet, so commerce is checked on `sys_user` and `rel_user_role`) and the re-convergence after a later table.

### WP-B9 Demo seed data (WBS 3.5)

- `db/seed/demo/<service>/R__demo_seed.sql`, loaded only with profile `demo`.
- Bulk rows via `generate_series` (10,000 products, 1,000 deals) behind a separate `db/seed/load` location so a plain demo stays small.

---

## 6. ArchUnit rules (WP-B1)

| Rule | Why |
| --- | --- |
| Classes in `catalog`, `groupbuy`, `order`, `payment` must not depend on `com.blocbuy.identity..` except `com.blocbuy.identity.service.UserAppService` | Only the remote-capable port crosses the boundary |
| Commerce classes may call only `UserAppService.requireActive` | `currentUser` has no remote implementation. **Amended 2026-10-12:** implemented as "no class outside identity calls `currentUser`", because `publicNickname` (and the batch lookup of #469) also get a remote implementation in #468 |
| No class outside `com.blocbuy.notification..` depends on `com.blocbuy.notification..` | Notification is reached by events only |
| `com.blocbuy.notification..` depends on no commerce or identity package | Keeps notification independently deployable |
| Classes under `..web.internal..` are annotated with the internal-token guard | `/internal/**` is never public. **Amended 2026-10-12:** the token was cut on 2026-10-11, so internal controllers must instead be `@Hidden` and `@ConditionalOnProperty(blocbuy.topology=distributed)`, and no other controller may map an `/internal` path (ADR 0008) |
| Mappers of one service's tables are only used from that service's packages | Enforces data ownership before the database split. Implemented as three rules on `..persistence..` packages; SQL joins inside mapper XML are out of ArchUnit's reach (#469 records a `grep`) |

---

## 7. Configuration contract per service

Environment variables expected at runtime on ECS (values from SSM; see the AWS plan for parameter paths).

| Variable | identity | commerce | notification |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `identity,aws` | `commerce,aws` | `notification,aws` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | yes | yes | yes |
| `SPRING_FLYWAY_USER`, `SPRING_FLYWAY_PASSWORD` | yes | yes | yes |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL=true` | yes | yes | yes |
| `FRONTEND_URL` | yes | yes | yes |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` | yes | no | no |
| `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET` | no | yes | no |
| `BLOCBUY_SCHEDULING_ENABLED` | false | true | false |
| `BLOCBUY_INTERNAL_TOKEN` | yes | yes | no |
| `IDENTITY_BASE_URL` | no | `http://identity-svc:8589` | no |
| `BLOCBUY_EVENTS_TOPIC_ARN_PREFIX` | no | yes | no |
| `BLOCBUY_NOTIFICATION_QUEUE_URL` | no | no | yes |
| `TYPESAFE_ENABLED` | no | as on `dev` | no |

---

## 8. Sequence and dependencies

```
B0 ADRs ─┬─ B1 guardrails ── B2 packaging ─┬─ B3 platform ─┬─ B4 session RBAC ── B5 remote identity
         │                                 │               └─ B6 outbox ── B7 consumers
         └─────────────────────────────────┴─ B8 data access (after B2)   B9 seed (any time after B2)
```

| Window | Packages |
| --- | --- |
| 30 Sep – 4 Oct | B0, B1, B2 |
| 5 – 11 Oct | B3, B6, B7 |
| 12 – 18 Oct | B4, B5, B8, B9 |

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| Classifier change breaks the SWE5006 image build or EC2 deploy | Separate small PR (B2 part 1); watch the first `dev` deploy; revert is one line |
| Spring Cloud AWS not yet compatible with Spring Boot 4 | Fallback: AWS SDK v2 `SqsClient` polling loop in `blocbuy-platform-starter` |
| Session roles go stale after an admin role change | Accept for demo; documented in the dual-topology ADR (0008); logout forces refresh |
| Outbox relay lag adds notification latency | 2 s poll; measured in the queue-scaling demo |
