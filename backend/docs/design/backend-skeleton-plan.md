# Backend Skeleton Implementation Plan

Status: LOCKED — all 20 questions answered 2026-08-30. Ready to implement.
Style: terse. Substance complete.

## 1. Goal

Init `backend/` skeleton. Spring Boot 3.5.15 + JDK 21 + Maven COLA multi-module + MyBatis.
5 domains × 4 layers, skeleton only — no business impl.
`mvnw verify` green from day one: compile + checkstyle + ArchUnit + unit tests + one PG smoke test.

## 2. Locked Decisions (from Q&A)

| # | Decision |
|---|---|
| D1 | COLA multi-module: client / adapter / application / domain / infrastructure / bootstrap |
| D2 | groupId `com.blocbuy`, artifact prefix `blocbuy-` |
| D3 | Backend self-contained under `backend/`; no repo-root pom |
| D4 | Domains: `identity`, `groupbuy`, `order`, `catalog`, `notification` |
| D5 | Middleware: PostgreSQL 18 + Redis 8.10.0 only. No MQ, no ES |
| D6 | Sa-Token session Redis-backed (`sa-token-redis-jackson`) |
| D7 | One token domain; RBAC roles/permissions loaded from user SQL tables via `StpInterface` |
| D8 | JDBC `jdbc:postgresql://localhost:5432/Blocbuy`; PG schema = `domain` |
| D9 | Hand-written converters (no MapStruct) |
| D10 | Errors = RFC 9457 ProblemDetail + `code` + `traceId`; success = bare DTO; no envelope |
| D11 | API base path `/api/v1` |
| D12 | springdoc-openapi + springdoc-openapi-maven-plugin for `contracts/openapi.json` |
| D13 | Checkstyle Google Checks, line length relaxed to 120 |
| D14 | No formatter plugin now — Checkstyle only |
| D15 | English messages first; i18n seam in `ErrorCode` (message keys) |
| D16 | Dockerfile/compose — later, separate task |
| D17 | Rotate leaked dev passwords (docs/deploy/dev/db-access.md) — urgent, separate action |
| D18 | Boot pinned 3.5.15 exactly |
| D19 | Commit empty-baseline `contracts/openapi.json` |
| D20 | One Testcontainers PG smoke test in skeleton |

⚠ D8 caution: `Blocbuy` capital-B. PostgreSQL folds unquoted identifiers to lowercase. If DB was created quoted as `"Blocbuy"`, JDBC URL must keep exact case; if created unquoted, real name is `blocbuy`. Verify at first connection; doc the finding.

## 3. Stack — Versions Verified vs Maven Central 2026-08-30

| Component | Version | Source |
|---|---|---|
| JDK | 21 | toolchain pinned |
| Spring Boot | 3.5.15 | parent BOM |
| mybatis-spring-boot-starter | 3.0.5 | explicit |
| sa-token-spring-boot3-starter | 1.46.0 | explicit |
| sa-token-redis-jackson | 1.46.0 | explicit |
| springdoc-openapi-starter-webmvc-ui | 2.9.0 | explicit; 3.x targets Boot 4 — forbidden |
| springdoc-openapi-maven-plugin | 1.5 | verify at impl |
| PostgreSQL driver, Flyway core + `flyway-database-postgresql` | Boot BOM | managed |
| spring-boot-starter-data-redis | Boot BOM | managed |
| Lombok | Boot BOM | `@RequiredArgsConstructor`/`@Slf4j` only |
| ArchUnit JUnit5 | 1.4.2 | explicit |
| Testcontainers (postgresql) | Boot BOM | managed |
| maven-checkstyle-plugin | 3.6.0 | explicit |
| maven-enforcer-plugin | Boot BOM | Java 21, Maven ≥3.9, dependencyConvergence |

All versions in parent `<properties>` + `<dependencyManagement>`. Children never declare versions.

## 4. Module Tree

```text
backend/
├── pom.xml                       # blocbuy-parent (pom packaging)
├── .mvn/wrapper/ + mvnw          # wrapper committed
├── checkstyle.xml                # Google checks, 120 col
├── blocbuy-client/               # external DTOs, contracts; zero business code
├── blocbuy-domain/               # aggregates, VOs, domain services, ports, events
├── blocbuy-application/          # command/query, use-case services, assemblers
├── blocbuy-adapter/              # controllers; protocol adaptation only
├── blocbuy-infrastructure/       # Mapper+DO+RepositoryImpl+Converter, Redis, Sa-Token RBAC impl
├── blocbuy-bootstrap/            # BlocBuyApplication, application*.yml, config, assembly
└── docs/design/                  # this plan
```

Dependency edges (Maven + ArchUnit enforced):

```text
adapter → application → domain
infrastructure → domain (+ application not allowed)
bootstrap → adapter + application + infrastructure + client
client ← adapter, application
domain → JDK + Lombok only
```

## 5. Package Map

Root: `com.blocbuy`

```text
per module × per domain (identity | groupbuy | order | catalog | notification):

blocbuy-domain/com/blocbuy/{domain}/model/        # aggregate root, entity, VO — skeleton
blocbuy-domain/com/blocbuy/{domain}/repository/   # port interface per aggregate root
blocbuy-domain/com/blocbuy/{domain}/event/        # past-tense events (placeholder)
blocbuy-domain/com/blocbuy/{domain}/exception/    # {Domain}ErrorCode enum
blocbuy-application/com/blocbuy/{domain}/command/ # intent commands
blocbuy-application/com/blocbuy/{domain}/query/   # view queries
blocbuy-application/com/blocbuy/{domain}/service/ # use-case services (tx boundary)
blocbuy-application/com/blocbuy/{domain}/assembler/
blocbuy-adapter/com/blocbuy/{domain}/web/         # {X}Controller, request/response DTOs
blocbuy-infrastructure/com/blocbuy/{domain}/persistence/
                                                  # {X}Mapper, {X}DO, {X}RepositoryImpl, {X}Converter
```

Shared kernel (minimal, per module):

```text
blocbuy-domain .../common/exception/   ErrorCode (interface, messageKey seam), BizException
blocbuy-domain .../common/model/       PageQuery (max size guard), PageResult
blocbuy-client .../common/constant/    ApiConstants (/api/v1, Idempotency-Key header)
blocbuy-application .../common/gateway/ # port placeholders (IdempotencyGateway etc.)
blocbuy-adapter .../common/web/        GlobalExceptionHandler (@RestControllerAdvice)
blocbuy-infrastructure .../common/config/ MyBatis, Redis, Sa-Token (StpInterface impl), Flyway marker
```

## 6. Baseline Components (skill hard gates — delivered as skeleton)

1. **ErrorCode system** — `ErrorCode` interface {`code()`, `httpStatus()`, `messageKey()`}; `BizException(ErrorCode, details)` final, unchecked; per-domain enum skeleton + `CommonErrorCode` (400/401/403/404/409/412/429/500).
2. **GlobalExceptionHandler** — single advice: BizException→enum status; `MethodArgumentNotValidException`/`ConstraintViolationException`→400 safe field errors; Sa-Token `NotLoginException`/`NotPermissionException`→401/403; unknown→500, logged once w/ traceId. Body: ProblemDetail + `code` + `traceId`.
3. **Sa-Token** — config via `@ConfigurationProperties` (token name, timeout, excluded paths); interceptor; `StpInterfaceImpl` in infrastructure loading roles/perms from identity tables (skeleton queries, RBAC per D7); skeleton login/logout/me endpoints in identity adapter.
4. **MyBatis** — mapper scan = `com.blocbuy.**.persistence`; map-underscore-to-camel on; XML mappers in `src/main/resources/mapper/{domain}/`; `@Param`/explicit columns rules documented.
5. **Redis** — key prefix `blocbuy:{env}:{domain}:{type}:{id}` constant; Jackson serializer; timeouts set; Sa-Token DAO points at Redis (D6).
6. **Flyway** — `db/migration/` + `V1__init.sql` placeholder (comment only); `defaultSchema=domain`, `currentSchema` in JDBC URL.
7. **OpenAPI** — springdoc UI `/swagger-ui`; parent `openapi-export` profile runs springdoc-maven-plugin → writes `contracts/openapi.json` at repo root (feeds `make contract-check`).
8. **Actuator/Micrometer** — liveness/readiness split, `/actuator/prometheus`, traceId MDC servlet filter (generates if absent, echoes header).
9. **Pagination** — `PageQuery` enforces max size 100; `nextCursor` field reserved in `PageResult`.
10. **Idempotency seam** — `Idempotency-Key` header constant + `IdempotencyGateway` port placeholder; impl with first write use case.

## 7. Per-Domain Skeleton Content

Each of 5 domains gets, per layer:

| Layer | Skeleton artifact |
|---|---|
| domain | one aggregate-root placeholder class + repository port interface + `{Domain}ErrorCode` enum |
| application | command/ + query/ dirs (`package-info.java`), one use-case service interface placeholder |
| adapter | one controller skeleton (`/api/v1/{resource}`, `@Tag` OpenAPI annotations, no business logic) |
| infrastructure | persistence dir (`package-info.java`) + RepositoryImpl placeholder |

Resource mapping (D11): `/api/v1/users`, `/api/v1/group-deals`, `/api/v1/orders`, `/api/v1/products`, `/api/v1/notifications`.

## 8. Build & Gates

- `mvnw` wrapper committed (Maven 3.9.x).
- Parent pom: modules, properties, dependencyManagement, pluginManagement (compiler release 21, surefire, failsafe, enforcer, checkstyle, springdoc plugin in profile).
- Enforcer: requireJavaVersion [21,22), requireMavenVersion [3.9,), dependencyConvergence.
- Checkstyle `checkstyle.xml` (Google, 120 col) bound to `verify` — Makefile `lint` calls same.
- ArchUnit suite `blocbuy-bootstrap/src/test/.../architecture/`:
  - 4-layer dependency direction
  - domain imports only JDK/Lombok (no spring/mybatis/redis)
  - no `*Controller` → `*Mapper`
  - no cross-domain `..persistence..` access
  - no field injection (no `@Autowired` on fields)
- Tests: per-module JUnit5 skeleton; `bootstrap` context test; infrastructure `PgSmokeIT` via Testcontainers PostgreSQL 18 (D20).
- Profiles: `local` → `localhost:5432/Blocbuy?currentSchema=domain`, Redis localhost:6379; `docker` → host `postgres`, `redis`. Secrets via env vars (`DB_USERNAME`, `DB_PASSWORD`, `REDIS_PASSWORD`) — never committed (D17).

## 9. Config Files

```text
blocbuy-bootstrap/src/main/resources/
├── application.yml            # common: server, jackson ISO-8601, actuator, springdoc, flyway enabled
├── application-local.yml      # localhost PG/Redis, env-var secrets
├── application-docker.yml     # compose-network hosts
├── db/migration/V1__init.sql  # placeholder
└── logback-spring.xml         # JSON-ish console + traceId pattern
```

## 10. Execution Order

1. Parent pom + 6 module poms + wrapper + enforcer + checkstyle.xml.
2. Domain common kernel: ErrorCode, BizException, PageQuery/PageResult.
3. Client constants (base path, headers).
4. Adapter common: GlobalExceptionHandler.
5. Bootstrap: `BlocBuyApplication`, application*.yml, actuator, traceId filter, logback.
6. Infrastructure common: MyBatis config, Redis config, Sa-Token config + StpInterfaceImpl, Flyway dir.
7. 5 domains × per §7 skeleton.
8. ArchUnit rules + context test + PgSmokeIT.
9. `openapi-export` profile → `contracts/openapi.json` baseline commit.
10. `mvnw verify` green → report: module/layer map, gate output, D8 case finding, risks.

## 11. Definition of Done

- `cd backend && ./mvnw verify` passes clean env.
- `make lint` path works (`checkstyle:check`).
- ArchUnit suite green — boundaries machine-enforced.
- ProblemDetail error body live (trigger 404/validation in smoke test).
- Sa-Token interceptor active; login skeleton returns 401 unauthenticated.
- No secrets in repo; env placeholders only.
- `contracts/openapi.json` baseline committed.
- No business logic, no empty MQ/ES abstractions (D5).

## 12. Risks / Follow-ups

- D8 `Blocbuy` case — verify at first connect; if mismatch, rename DB or fix URL.
- D17 password rotation — separate urgent action, owner: user.
- springdoc-maven-plugin 1.5 vs Boot 3.5.15 — smoke at step 9.
- Sa-Token 1.46.0 config keys — verify vs official docs at impl.
- `repository-structure.md` shows single-module backend — update doc after impl (multi-module won).
