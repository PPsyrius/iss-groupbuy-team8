# #455 PR 2: service assemblies and per-service grants (steps A3 + A4) — implementation plan

Date: 2026-10-12 · Branch: `feature/microservice-a3a4-20261012` from `dev` at `c8f529ac` (#470 merged) · Issue: #455 (Sprint 4 container #465, task P2) · Owner: @l0sgAi · Related: steps A3 and A4 of the [microservice split plan](microservice-split-plan-20261012.md), [backend ADR 0008](../../../backend/docs/adr/0008-dual-deployment-topology.md), [SWE5001 backend plan](../../swe5001/impl-backend-architecture.md) WP-B2, WP-B3 and WP-B8, [A1 plan](exec-jar-classifier-plan-20261012.md)

> **Status (2026-10-12): reviewed (all §9 defaults accepted) and implemented on `feature/microservice-a3a4-20261012`. Results: [day log](log/2026-10-12.md).**

## 1. Goal and acceptance criteria

Package the existing code as three runnable Spring Boot assemblies, `identity-svc`, `commerce-svc` and `notification-svc`, each with its own component scan, mapper scan, configuration and database grants, while the SWE5006 monolith on `dev` keeps its image, configuration and behaviour. No business code moves.

| # | Criterion | Delivered by |
| --- | --- | --- |
| AC1 | `./mvnw -B verify` builds `services/<svc>/target/<svc>.jar` (Spring Boot layout, `assert-boot-jar.sh` passes) for all three services | §4 S1–S4, V2 |
| AC2 | One context-load test per service starts the application with `blocbuy.topology=distributed` against Testcontainers PostgreSQL and loads only that service's controllers and mappers | §4 S2–S4, V3 |
| AC3 | `identity-svc` boots from its JAR against local PostgreSQL and Redis and answers `/api/actuator/health` `UP`; `commerce-svc` and `notification-svc` do so once B1 (#467) and B3 (#468) supply their cross-service beans (§6) | V5 |
| AC4 | ITs prove the commerce runtime role gets `permission denied` on `sys_user`, the notification runtime role on a commerce table, and identity on a commerce table; grants re-converge after a later migration adds a table | §4 G1–G2, V4 |
| AC5 | The monolith never loads the grants and its executable JAR gains no class except one constants class; `dev` health `UP` after merge | §4 G3, V6–V8 |
| AC6 | Sonar sees coverage for the new modules; new-code coverage ≥ 80% | §4 P7, V9 |

Mapped to #455: AC1 + AC2 + AC3 cover "three service jars boot locally" (fully ticked only after B1/B3, §6); AC4 covers the grant ITs; ADR 0008 is flipped to Accepted here (merged in #470).

Non-goals: images and the service matrix (C1), the `distributed` runtime map, ALB rules and Service Connect (C2), demo data per database (C3), session roles (B1 #467), the nickname port (B2 #469), the remote identity adapter (B3 #468), the outbox (D).

## 2. Baseline (read on 2026-10-12 from `c8f529ac`)

| Piece | State | Consequence |
| --- | --- | --- |
| Reactor | Six COLA modules; `blocbuy-bootstrap` publishes plain `blocbuy-backend.jar` and executable `blocbuy-backend-exec.jar` (A1) | Service modules can depend on bootstrap as a library |
| `BlocBuyApplication` | `@SpringBootApplication(scanBasePackages = "com.blocbuy")` | Service entry classes must never scan `com.blocbuy.bootstrap` itself |
| `MyBatisConfig` (infrastructure) | `@MapperScan(basePackages = "com.blocbuy")` | Every service would register every mapper (F3) |
| `com.blocbuy.common` | Infrastructure `common.config` (`SaTokenConfig`, `StpInterfaceImpl`, `RedisConfig`, `MyBatisConfig`), `common.persistence` (`UuidTypeHandler`, `SysSchedulerLockMapper`, `SchedulerLeaseRepositoryImpl`), adapter `common.web`, Redis rate limit | Scanned by every service; `StpInterfaceImpl` needs identity's `RoleRepository` |
| Cross-context beans | Identity depends on no other context. Commerce needs `UserAppService` (seven callers, plus `PaymentIntegrationConfiguration`); every context needs a `RoleRepository` through `StpInterfaceImpl` | Outside identity both beans are missing until B1 and B3 |
| Bootstrap beans | `bootstrap.config`: `TraceIdFilter` (`@Component`), Stripe and payment configuration, `SchedulingConfiguration` (`blocbuy.scheduling.enabled`); `bootstrap.scheduling`: leased jobs | Commerce scans both packages; identity and notification import `TraceIdFilter` only |
| Mapper XML | `mapper/<context>/*.xml` per context plus `mapper/common` | `mybatis.mapper-locations` can be scoped per service |
| Configuration | `application.yml` sets `spring.profiles.active: local`; ECS sets `SPRING_PROFILES_ACTIVE`, `DB_*`, `SPRING_FLYWAY_USER/PASSWORD`, `REDIS_*`, `SPRING_DATA_REDIS_SSL_ENABLED` (`runtime/ecs.tf`) | Services add `application-<svc>.yml`; ECS activates `ec2,<svc>,aws` (ec2 maps the task variables, as for the ECS monolith) |
| Database bootstrap | `infra/swe5001/db-init/init.sh` creates `blocbuy_<db>_owner` and `blocbuy_<db>_app` and sets **default privileges: DML on every table the owner creates** | Without revokes every service role can read every table, and each new SWE5006 table is granted to all services automatically |
| Tables | 26 tables in `db/migration` V1–V25; no `biz_notification` or `sys_consumed_message` yet (#215, D) | Grants must tolerate tables that do not exist yet |
| Logging | Custom `logback-spring.xml` with a text pattern | Boot's `logging.structured.*` is ignored; JSON needs a `springProfile` block |
| Coverage | JaCoCo `report-aggregate` in bootstrap; Sonar reads only that file (`backend/pom.xml`), CI uploads only that file | Modules that bootstrap does not depend on (platform, services) would report 0% |
| Partial builds | `fullstack-localhost/backend.Dockerfile` copies only the six modules and runs `mvn -pl blocbuy-bootstrap -am` | A reactor with missing module directories fails, so the Dockerfile must copy the new modules |

## 3. Design decisions

### 3.1 Module layout

```
backend/
  blocbuy-platform/              NEW library, com.blocbuy.platform; depends on blocbuy-client and Spring Boot only
  services/identity-svc/         NEW assembly, com.blocbuy.service.identity
  services/commerce-svc/         NEW assembly, com.blocbuy.service.commerce
  services/notification-svc/     NEW assembly, com.blocbuy.service.notification
```

Each service depends on `blocbuy-bootstrap` (plain JAR, which brings the COLA modules, actuator, Flyway and the migrations) and on `blocbuy-platform`. **`blocbuy-bootstrap` never depends on `blocbuy-platform`**, so the monolith's classpath and the `dev` image are unchanged by construction. Per the split plan, B1–B3 put their topology-selected beans in the existing modules; the platform module stays thin.

### 3.2 Entry classes and scans

| Service | `scanBasePackages` | `@MapperScan` (`annotationClass = Mapper.class`) | `mybatis.mapper-locations` | Extra |
| --- | --- | --- | --- | --- |
| `identity-svc` | `com.blocbuy.identity`, `com.blocbuy.common` | `com.blocbuy.identity.persistence`, `com.blocbuy.common.persistence` | `mapper/identity`, `mapper/common` | `@Import(TraceIdFilter.class)` |
| `commerce-svc` | `com.blocbuy.catalog`, `.groupbuy`, `.order`, `.payment`, `com.blocbuy.common`, `com.blocbuy.bootstrap.config`, `com.blocbuy.bootstrap.scheduling` | the four `*.persistence` packages, `com.blocbuy.common.persistence` | the four context folders, `mapper/common` | — |
| `notification-svc` | `com.blocbuy.notification`, `com.blocbuy.common` | `com.blocbuy.notification.persistence`, `com.blocbuy.common.persistence` | `mapper/notification`, `mapper/common` | `@Import(TraceIdFilter.class)` |

`application-<svc>.yml` sets `blocbuy.topology: distributed`, `spring.application.name`, the mapper locations, `spring.flyway.locations: classpath:db/migration,classpath:db/swe5001` and the `service` placeholder (§3.5); commerce adds nothing for scheduling (still `BLOCBUY_SCHEDULING_ENABLED`, C2 sets it). No service ships an `application.yml`: bootstrap's stays the shared base.

### 3.3 Topology switch

- `blocbuy-client`: `com.blocbuy.common.constant.Topology` with compile-time constants `PROPERTY = "blocbuy.topology"`, `MONOLITH`, `DISTRIBUTED`, usable in annotations from every module (B1, B3 and the ArchUnit rule can share them).
- `MyBatisConfig`: `@ConditionalOnProperty(name = Topology.PROPERTY, havingValue = Topology.MONOLITH, matchIfMissing = true)`. Unchanged for the monolith.
- `blocbuy-platform`: `TopologyProperties` binds `blocbuy.topology` to an enum (a typo fails at startup), and `PlatformAutoConfiguration` fails fast when a service assembly starts with anything but `distributed`, because a service running as `monolith` would scan every mapper and keep `StpInterfaceImpl`.

### 3.4 Thin platform pieces (WP-B3 as rescoped)

| Piece | Content |
| --- | --- |
| `TopologyProperties`, `PlatformAutoConfiguration` | §3.3; registered through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| Trace propagation | A `RestClientCustomizer` adds an interceptor that copies the MDC `traceId` into `X-Trace-Id` (`ApiConstants`) on outbound calls; B3's `RestClient.Builder` picks it up without a compile dependency. The inbound side stays `TraceIdFilter` |
| `application-aws.yml` | `spring.data.redis.ssl.enabled: ${REDIS_SSL:false}` (health probes left at their defaults: `/api/actuator/health` already reports liveness and readiness groups); ECS-only, activated as `ec2,<svc>,aws` |
| JSON logs | `logback-spring.xml` (bootstrap) gains `<springProfile name="aws">` using Boot's `StructuredLogEncoder` (`ecs` format, MDC `traceId` included); the default text pattern stays for every other profile, so `dev` (`ec2`) logs are unchanged |
| Internal-call token | Not built (cut 2026-10-11) |

### 3.5 Per-service grants (A4, WP-B8)

**Mechanism: a Flyway `afterMigrate` SQL callback, not `R__swe5001_grants.sql`.** A repeatable migration re-runs only when its checksum changes, but `db-init`'s default privileges grant every table a later SWE5006 migration creates to the service's app role; an `afterMigrate` callback runs on every migrate and converges. This is a deliberate deviation from WP-B8's wording, recorded there.

- File: `blocbuy-platform/src/main/resources/db/swe5001/afterMigrate__service_grants.sql`. Only service profiles list `classpath:db/swe5001`, and the file is not on the monolith's classpath at all.
- Placeholders from `application-<svc>.yml`: `service` (`identity|commerce|notification`) and `appRole` (`${DB_USERNAME}`). Flyway runs as the owner role (`SPRING_FLYWAY_USER`).
- Logic in one `DO` block under `pg_advisory_xact_lock` (two commerce tasks may migrate at once; concurrent `GRANT` can fail with "tuple concurrently updated"): skip with a `NOTICE` when the app role does not exist or is the current user (local runs); for every table and sequence in schema `domain`, `GRANT SELECT, INSERT, UPDATE, DELETE` (sequences `USAGE, SELECT`) when the service owns it, otherwise `REVOKE ALL`; always revoke `flyway_schema_history`.
- Ownership (refactoring plan §2.2, #455): identity owns `sys_user`, `sys_user_oauth`, `sys_role`, `sys_permission`, `rel_user_role`, `rel_role_permission`; notification owns `biz_notification`, `sys_consumed_message`; commerce owns every other table, including `sys_scheduler_lock`, `sys_scheduled_item_retry`, `sys_checkout_expiry_cursor` and the future outbox. A table nobody lists therefore defaults to commerce, which matches "everything else".

## 4. Changes (one PR, about 36 files, est. 1,300 lines)

| # | File(s) | Change |
| --- | --- | --- |
| P1 | `backend/pom.xml` | Modules `blocbuy-platform`, `services/*`; `dependencyManagement` for platform; per-module JaCoCo `report` for platform and services |
| P2 | `blocbuy-client/.../common/constant/Topology.java` | Constants (§3.3) |
| P3 | `blocbuy-infrastructure/.../common/config/MyBatisConfig.java` + test | Monolith-only condition; `ApplicationContextRunner` test for missing, `monolith` and `distributed` |
| P4 | `blocbuy-platform/` (pom, `TopologyProperties`, `PlatformAutoConfiguration`, `TraceIdPropagationInterceptor`, `AutoConfiguration.imports`, `application-aws.yml`, tests) | §3.3, §3.4 |
| P5 | `blocbuy-bootstrap/src/main/resources/logback-spring.xml` | `aws` JSON block |
| P6 | `fullstack-localhost/backend.Dockerfile` | Copy `blocbuy-platform` and `services/` so the reactor resolves |
| P7 | `backend/pom.xml` Sonar property, `.github/workflows/ci-backend.yml` "Upload Sonar inputs" | Add `blocbuy-platform/target/site/jacoco/jacoco.xml` and `services/*/target/site/jacoco/jacoco.xml` to `sonar.coverage.jacoco.xmlReportPaths` and to the uploaded files |
| S1 | `services/<svc>/pom.xml` (×3) | Parent `blocbuy-parent` (`../../pom.xml`); depends on `blocbuy-bootstrap` and `blocbuy-platform`; `spring-boot-maven-plugin` `skip=false`, `finalName` `<svc>`, so the executable is `<svc>.jar` (nothing depends on a service, so no classifier) |
| S2–S4 | Per service: entry class, `application-<svc>.yml`, `<Svc>ApplicationIT` | §3.2; the IT asserts its own controllers and mappers are present and a foreign one is absent (for example no `ProductController` in identity, no `AuthController` in commerce) |
| G1 | `blocbuy-platform/.../db/swe5001/afterMigrate__service_grants.sql` | §3.5 |
| G2 | `blocbuy-platform/.../ServiceGrantsIT.java` | PostgreSQL 18 container; replicates `db-init` (owner and app role per database, default privileges); runs Flyway with `db/migration` + `db/swe5001` as the owner per service; asserts SQLSTATE `42501` as `blocbuy_commerce_app` on `sys_user`, as `blocbuy_notification_app` on `biz_product`, as `blocbuy_identity_app` on `biz_order`, owned tables readable; then creates a new table as owner, migrates again and asserts it is revoked from identity and notification and granted to commerce. Test-scoped dependency on `blocbuy-bootstrap` for the migrations |
| G3 | `blocbuy-bootstrap/src/test/.../MonolithGrantsAbsentTest.java` | Asserts `classpath*:db/swe5001/**` resolves nothing and Flyway locations in every monolith profile omit it |
| D1 | `backend/docs/adr/0008-*.md`, ADR index | Status Accepted (#470 merged); note the `afterMigrate` mechanism |
| D2 | `docs/swe5001/impl-backend-architecture.md` | WP-B2, WP-B3 and WP-B8 dated notes (module names, callback instead of `R__`) |
| D3 | `docs/swe5001/HANDOFF.md`, `backend/README.md` | A3/A4 rows; how to run a service locally |
| D4 | `docs/agile/sprint-04/log/YYYY-MM-DD.md`, `log.md` | Day-log entry |

Contract: unchanged (`contracts/openapi.json` is still exported from the monolith; service assemblies serve subsets of the same paths).

## 5. Verification

| # | Check | Command | Proves |
| --- | --- | --- | --- |
| V1 | Baseline monolith JAR class list | `unzip -Z1 blocbuy-backend-exec.jar \| sort > before.txt` on `c8f529ac` | Reference for V7 |
| V2 | Service JARs | `./mvnw -B -DskipTests package`; `assert-boot-jar.sh` on each `services/<svc>/target/<svc>.jar` | AC1 |
| V3 | Full build | `./mvnw -B verify` with Docker; three service ITs, `ServiceGrantsIT`, `MonolithGrantsAbsentTest`, `MyBatisConfig` test, ArchUnit | AC2, AC4, AC5 |
| V4 | Grants bite | `ServiceGrantsIT` assertions; temporarily drop one `REVOKE` and see it fail | AC4 |
| V5 | Real boot | `identity-svc.jar` with `SPRING_PROFILES_ACTIVE=identity`, local PostgreSQL 18 and Redis containers, owner/app roles from `db-init`; `/api/actuator/health` `UP`; same start with `blocbuy.topology=monolith` must fail fast | AC3 |
| V6 | Monolith unchanged | `Dockerfile.ci` image + EC2-profile smoke as in A1 V4 | AC5 |
| V7 | Monolith JAR delta | `diff before.txt after.txt` shows only `com/blocbuy/common/constant/Topology.class` (and the `MyBatisConfig` class content) | AC5 |
| V8 | After merge | `dev` deploy of `ci-backend.yml` green; dev CloudFront `/api/actuator/health` `UP` (@l0sgAi) | AC5 |
| V9 | PR CI | `build-quality`, `docker`, `contract-check`, Sonar (coverage for platform and services visible, ≥ 80% new code) | AC6 |
| — | Fullstack | `docker compose build backend` in `fullstack-localhost` | P6 |

Not verified by this PR: service images (C1), any ECS run (C2), commerce and notification real boot (needs B1 and B3).

## 6. Dependencies on B1 and B3

`commerce-svc` and `notification-svc` cannot start for real until #467 and #468 merge: `StpInterfaceImpl` (scanned through `com.blocbuy.common`) needs identity's `RoleRepository`, and commerce needs a `UserAppService`. In this PR their ITs provide `@MockitoBean RoleRepository` and `@MockitoBean UserAppService` with a comment naming #467 and #468; when B1 and B3 land, those mocks are deleted, which also proves the real beans wire up. Asks to the B owners, posted on the issues after review:

- #467: make `StpInterfaceImpl` conditional on `Topology.MONOLITH` or on identity being present, and `SessionStpInterface` on `Topology.DISTRIBUTED`, using the `Topology` constants.
- #468: `RemoteUserAppService` conditional on `Topology.DISTRIBUTED` and absent from identity (identity keeps `UserAppServiceImpl`); build its client from the injected `RestClient.Builder` so trace propagation applies; `InternalUserController` follows the ArchUnit rule (`@Hidden`, distributed-only, no `matchIfMissing`).

## 7. Risks and rollback

| Risk | Impact | Mitigation |
| --- | --- | --- |
| New modules break the `dev` build or the monolith JAR | SWE5006 down | Platform not on the monolith classpath; V6–V8; rollback is `git revert` (no schema or data change: the grants never run on `dev`) |
| Grants revoke something a service needs | 500s in the ECS session | Ownership table in §3.5 matches the refactoring plan; `ServiceGrantsIT` exercises each service's owned tables; C2's first session runs the smoke suite |
| `afterMigrate` races between two commerce tasks | Startup failure | Advisory transaction lock in the callback |
| CI time grows (three more Spring contexts with PostgreSQL) | Slower PRs | One context per service; reuse one container per test class |
| Sonar new-code coverage below 80% | Gate fails | Per-module JaCoCo reports wired into Sonar (P7); unit tests for platform classes |
| B1/B3 slip | Only identity runs for real by 10-16 | Identity go/no-go 10-15 per the split plan; two-service fallback recorded in ADR 0008 |

## 8. Evidence

- Decision records: ADR 0008 to Accepted with the grants mechanism; WP-B2/B3/B8 dated notes. No new ADR.
- NFR register: propose no new row; data ownership per service is part of ADR 0008. Reviewer to confirm.
- Day-log entry with V1–V7 results; PR body follows `.github/PULL_REQUEST_TEMPLATE.md` with `Refs #455`.

## 9. Questions for the reviewer

1. `afterMigrate` callback instead of `R__swe5001_grants.sql` (§3.5)?
2. A new `blocbuy-platform` module that only services depend on (§3.1), rather than adding the pieces to `blocbuy-infrastructure` where the monolith would carry them?
3. Mock `RoleRepository` and `UserAppService` in the commerce and notification ITs until B1/B3 (§6), accepting that only `identity-svc` boots for real in this PR?
4. Per-module JaCoCo reports added to the Sonar path (P7), rather than a new coverage-aggregator module?
5. JSON logs through a `springProfile name="aws"` block in the shared `logback-spring.xml` (§3.4)?
6. Flip ADR 0008 to Accepted in this PR?
