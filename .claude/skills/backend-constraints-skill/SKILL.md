---
name: backend-constraints-skill
description: Implement or review BlocBuy backend code built with Java 21, Spring Boot 3.5, MyBatis, PostgreSQL 18, Redis 8, Sa-Token, Stripe payments, and Maven. Use for new features, refactoring, code review, and architecture checks. Enforces COLA multi-module modular monolith, DDD, four-layer architecture, REST APIs, the unified Result response envelope with ErrorCode status-code enums, Sa-Token RBAC, security, data access, testing, and observability. Any change to the HTTP API surface (controllers, endpoints, DTOs, annotations) must keep contracts/openapi.json regenerated and committed. RabbitMQ and Elasticsearch are deliberately NOT in the stack (decision 2026-08-30); do not introduce them without user approval. Not for frontend or non-Java services.
---

# BlocBuy Backend Development Constraints

Treat this skill as a project-level quality gate, not a general tutorial. Claude Code loads it automatically for relevant tasks, and the user can invoke it explicitly via `/backend-constraints-skill`. Once loaded, these constraints remain in effect for subsequent turns of the current task.

First read the `CLAUDE.md` files that apply to the current working directory and its subdirectories. Respect existing repository conventions that do not conflict with this skill. If `CLAUDE.md`, existing implementation, and this skill conflict, explicitly point out the conflict and its impact; never silently weaken security, data-consistency, or business-correctness constraints. Only modify code within the scope the user has authorized.

## Locked Stack Decisions (2026-08-30, user-confirmed)

These are settled; do not reopen them without explicit user approval. Full rationale: `backend/docs/design/backend-skeleton-plan.md`.

- **Modules**: Maven COLA aggregate under `backend/` (self-contained, no repo-root POM): `blocbuy-client`, `blocbuy-domain`, `blocbuy-application`, `blocbuy-adapter`, `blocbuy-infrastructure`, `blocbuy-bootstrap`. groupId `com.blocbuy`.
- **Domains (v1)**: `identity`, `groupbuy`, `order`, `catalog`, `notification`; amended 2026-08-31: + `payment` (Stripe). Do not create other domain packages (inventory, search, …) without approval.
- **Middleware**: PostgreSQL 18 + Redis 8.10 only. No RabbitMQ, no Elasticsearch. Database `Blocbuy`, PostgreSQL schema `domain`. Redis backs Sa-Token sessions.
- **Auth**: Sa-Token, one token domain, RBAC roles/permissions loaded from identity SQL tables via `StpInterface`.
- **API**: base path `/api/v1`; every JSON endpoint returns the unified `Result<T>` envelope (`code` / `data` / `message`, plus `errors` and `traceId` only when present) backed by `ErrorCode` status-code enums — amended 2026-09-07, user-confirmed, supersedes the original "success = bare DTO, errors = RFC 9457 Problem Details" decision. The HTTP status still reflects the outcome (no 200-wrapped failures) and is applied only by the global exception handler. Messages English-first with an i18n message-key seam in `ErrorCode`.
- **Conversions**: hand-written DO↔domain converters — no MapStruct.
- **Pinned versions**: Spring Boot 3.5.15 (exact), mybatis-spring-boot-starter 3.0.5, sa-token 1.46.0 (boot3 starter + redis-jackson), springdoc-openapi 2.9.0 (2.x line only — 3.x targets Boot 4), ArchUnit 1.4.2, stripe-java 33.4.0 (payment domain; infrastructure module only). Everything else from the Spring Boot BOM; versions centralized in the parent POM.
- **Gates**: Maven Enforcer, Spotless + google-java-format AOSP (amended 2026-08-31 — supersedes the original "no formatter plugin" decision; Checkstyle Indentation moved to AOSP values, formatting owned solely by Spotless), Checkstyle (Google checks, 120 columns), SpotBugs, ArchUnit layer rules, JUnit 5 + Testcontainers.

## How to Work

1. Inspect the repository structure, build files, and relevant implementation first, then identify the bounded context, use case, aggregate, four-layer placement, and synchronous/asynchronous boundary for this change. Do not assume the current state from file names or user descriptions alone.
2. Check the project baseline. If the `ErrorCode` status-code abstraction or domain status-code enums are missing, raise it first and recommend building them as the top priority; if the current task involves implementing business exceptions, first build a minimal usable status-code system. Apply the same treatment if a unified `@RestControllerAdvice` is missing. Never throw bare `RuntimeException` as a workaround for a missing baseline.
3. Read only the reference files relevant to the current task, then implement:
   - Architecture, directory layout, DDD, module dependencies: read [references/architecture-and-ddd.md](references/architecture-and-ddd.md).
   - REST, status codes, exceptions, Sa-Token: read [references/api-errors-security.md](references/api-errors-security.md).
   - Java 21, OOP, Spring Boot, Maven: read [references/java-spring.md](references/java-spring.md).
   - MyBatis, PostgreSQL, Redis: read [references/data-access.md](references/data-access.md) (its Elasticsearch section is deferred — ES is not in the stack).
   - RabbitMQ, transactional consistency, idempotency: [references/messaging-consistency.md](references/messaging-consistency.md) — **deferred, no MQ in the stack**. Read only if the user approves introducing messaging; the idempotency/consistency principles still apply to HTTP write endpoints and callbacks.
   - Testing, logging, monitoring, performance, delivery: read [references/quality-operations.md](references/quality-operations.md).
   - Version-specific behavior or anything needing further verification: consult only the official sources listed in [references/authoritative-sources.md](references/authoritative-sources.md); never hardcode dependency versions from memory.
4. After implementation, run the repository's existing compile, test, static-analysis, and architecture checks related to the change, then self-check against the "Hard Gates". Never delete tests, loosen gates, or hide failures to make validation pass. For review tasks, output precisely locatable findings, impact, and minimal fix suggestions, ranked by severity.
5. If the change alters the HTTP API surface in any way — adding/removing/renaming endpoints, changing controller method signatures, request/response DTO fields, validation or springdoc annotations, tags, or operation summaries — regenerate and commit the OpenAPI contract before finishing:

   ```bash
   cd backend
   ./mvnw -B verify -Popenapi-export -DskipTests \
     -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true
   git add contracts/openapi.json
   ```

   Then verify with `git status` that `contracts/openapi.json` has no uncommitted diff left. CI's `contract-check` job (`.github/workflows/ci-backend.yml`) regenerates the spec from code and fails the PR on any drift against the committed file, and additionally rejects backward-incompatible changes versus the base branch via `openapi-diff`. A green local `git diff --exit-code -- contracts/` (run from the repo root after regeneration) means the drift gate will pass.

## Hard Gates

The following rules must not be violated unless the user explicitly approves an exception:

- Use a modular monolith with DDD and COLA. Code is first divided by bounded context / business domain, then placed into the four layers `adapter`, `application`, `domain`, `infrastructure`. Dependencies point only toward the business core; cross-domain access to another module's Mappers, DOs, repository implementations, or internal aggregates is forbidden.
- `domain` must not depend on Spring, MyBatis, Redis, Web DTOs, or infrastructure implementations (its only allowed third-party dependency is Lombok). Repository and external-capability ports are defined on the domain side and implemented in the infrastructure layer. Ports never reference provider SDK types (e.g. no `com.stripe` in port signatures); the infrastructure layer is the only module allowed to depend on such SDKs.
- Payment specifics (domain added 2026-08-31): provider webhooks are public paths authenticated by signature verification in the gateway, not Sa-Token; the adapter forwards the raw body untouched; provider callbacks deduplicate on the provider event id (`log_payment_webhook_event`); the payment row is persisted before any provider call; amounts are `BigDecimal` major units in domain/application, converted to provider minor units only in the gateway.
- Controllers/Consumers only do protocol adaptation, authentication entry points, validation, and result conversion; the application layer orchestrates use cases and transactions; the domain layer carries invariants; the infrastructure layer handles persistence and external systems.
- Never execute Mapper/Repository/remote queries inside any loop, Stream callback, recursion, or per-item async task. First collect and deduplicate IDs, execute a batch query, then build a `Map<ID, Entity>` for backfill; batch writes follow the same pattern. Any suspected N+1 must be flagged and fixed, or the blocking reason explained.
- REST APIs use resource nouns, correct HTTP methods and status codes, and explicit idempotency and pagination semantics. Forbidden: `POST /getXxx`, unbounded full-list endpoints, always returning HTTP 200, or exposing DOs/aggregates directly.
- All JSON endpoints return `Result<T>` (`com.blocbuy.common.web.Result`) via its factories: success `Result.ok(data)` / `Result.ok()`, failure `Result.error(ErrorCode[, errors])` — assembled only in the global exception handler. Controllers must not return bare DTOs, `ResponseEntity`, or hand-built envelopes, and must not set the HTTP status themselves; the status comes from the same `ErrorCode` enum. Sole exemption: protocol handshakes that are not JSON resources (e.g. the 302 OAuth authorization redirect with `Location` + `Set-Cookie` headers) may return headers only.
- The committed OpenAPI contract `contracts/openapi.json` must always match what the code generates. Any API-surface change without a regenerated, committed contract is an unfinished change — the `contract-check` CI job will fail the PR. Never hand-edit `contracts/openapi.json`; it is produced only by the `openapi-export` Maven profile (`backend/blocbuy-bootstrap/pom.xml`). Contract evolution must stay backward compatible (additive changes only); breaking changes require explicit user approval and an API version bump.
- Spring Bean dependencies must be `private final` and constructor-injected; use the real Lombok annotation `@RequiredArgsConstructor`. `@RequiredConstructorArgs` does not exist — never copy that spelling. Field injection and `ApplicationContext.getBean()` in general business code are forbidden.
- Any class that logs uses Lombok `@Slf4j`. Forbidden: `System.out/err`, string-concatenated log messages, logging passwords/tokens/full phone numbers/national IDs/bank card numbers, or large objects. Do not log the same exception multiple times.
- Business exceptions may only be constructed from status-code enums, e.g. `throw new BizException(OrderErrorCode.ORDER_NOT_FOUND)`. Free-form strings, magic numbers, or bare runtime exceptions to express business failure are forbidden.
- All inbound request exceptions must be converted by the unified global exception handler. Scattered `try/catch` blocks in Controllers assembling error responses are forbidden; never return stack traces, SQL, or internal class names to clients.
- Authentication and authorization use Sa-Token uniformly — one token domain, RBAC loaded from the identity SQL tables. Running a homegrown token parser alongside it, a second authorization framework, or relying on frontend button hiding for authorization is forbidden. Object-level permissions must be checked server-side.
- PostgreSQL is the source of truth for transactions. Redis must never be the sole source of truth for strongly consistent facts such as orders, deal progress, or payments — it holds caches, sessions, rate limits, and derived state only. There is no message broker in the stack; cross-aggregate consistency uses in-process calls within local transactions, and deferred work uses idempotent scheduled jobs with database state as the backstop.
- All external input goes through Bean Validation and business validation; SQL uses parameter binding; never commit secrets, default passwords, or production connection strings.

## Decision Priority

When rules conflict, protect in this order: business correctness and funds/inventory safety > authorization and data security > consistency and idempotency > module boundaries > maintainability > micro performance optimizations. Any concurrency design affecting orders, payments, inventory, or refunds must state its database constraints, locking/versioning strategy, idempotency keys, and failure-recovery path.

## Output Requirements

- Implementation tasks: deliver code, and state the business domain it belongs to, its layer placement, transaction/consistency approach, verification results, and unresolved risks.
- Review tasks: report gate violations first, with file and line numbers; distinguish "must fix" from "suggested improvement".
- Missing baselines: explicitly state "missing status-code enum system" or "missing global exception handler", explain what it blocks, and make building that baseline the first recommendation.
- Do not rewrite unrelated code at scale for the sake of conformance; new abstractions must be driven by the current use case or a clear reuse point.
