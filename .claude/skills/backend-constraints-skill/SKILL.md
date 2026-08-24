---
name: backend-constraints-skill
description: Implement or review BlocBuy backend code built with Java 21, Spring Boot, MyBatis, PostgreSQL, Redis, Elasticsearch, RabbitMQ, and Maven. Use for new features, refactoring, code review, and architecture checks. Enforces modular monolith, DDD, COLA four-layer architecture, REST APIs, error status codes, Sa-Token, security, data access, messaging consistency, testing, and observability. Not for frontend or non-Java services.
---

# BlocBuy Backend Development Constraints

Treat this skill as a project-level quality gate, not a general tutorial. Claude Code loads it automatically for relevant tasks, and the user can invoke it explicitly via `/backend-constraints-skill`. Once loaded, these constraints remain in effect for subsequent turns of the current task.

First read the `CLAUDE.md` files that apply to the current working directory and its subdirectories. Respect existing repository conventions that do not conflict with this skill. If `CLAUDE.md`, existing implementation, and this skill conflict, explicitly point out the conflict and its impact; never silently weaken security, data-consistency, or business-correctness constraints. Only modify code within the scope the user has authorized.

## How to Work

1. Inspect the repository structure, build files, and relevant implementation first, then identify the bounded context, use case, aggregate, four-layer placement, and synchronous/asynchronous boundary for this change. Do not assume the current state from file names or user descriptions alone.
2. Check the project baseline. If the `ErrorCode` status-code abstraction or domain status-code enums are missing, raise it first and recommend building them as the top priority; if the current task involves implementing business exceptions, first build a minimal usable status-code system. Apply the same treatment if a unified `@RestControllerAdvice` is missing. Never throw bare `RuntimeException` as a workaround for a missing baseline.
3. Read only the reference files relevant to the current task, then implement:
   - Architecture, directory layout, DDD, module dependencies: read [references/architecture-and-ddd.md](references/architecture-and-ddd.md).
   - REST, status codes, exceptions, Sa-Token: read [references/api-errors-security.md](references/api-errors-security.md).
   - Java 21, OOP, Spring Boot, Maven: read [references/java-spring.md](references/java-spring.md).
   - MyBatis, PostgreSQL, Redis, Elasticsearch: read [references/data-access.md](references/data-access.md).
   - RabbitMQ, transactional consistency, idempotency: read [references/messaging-consistency.md](references/messaging-consistency.md).
   - Testing, logging, monitoring, performance, delivery: read [references/quality-operations.md](references/quality-operations.md).
   - Version-specific behavior or anything needing further verification: consult only the official sources listed in [references/authoritative-sources.md](references/authoritative-sources.md); never hardcode dependency versions from memory.
4. After implementation, run the repository's existing compile, test, static-analysis, and architecture checks related to the change, then self-check against the "Hard Gates". Never delete tests, loosen gates, or hide failures to make validation pass. For review tasks, output precisely locatable findings, impact, and minimal fix suggestions, ranked by severity.

## Hard Gates

The following rules must not be violated unless the user explicitly approves an exception:

- Use a modular monolith with DDD and COLA. Code is first divided by bounded context / business domain, then placed into the four layers `adapter`, `application`, `domain`, `infrastructure`. Dependencies point only toward the business core; cross-domain access to another module's Mappers, DOs, repository implementations, or internal aggregates is forbidden.
- `domain` must not depend on Spring, MyBatis, Redis, Elasticsearch, RabbitMQ, Web DTOs, or infrastructure implementations. Repository and external-capability ports are defined on the domain side and implemented in the infrastructure layer.
- Controllers/Consumers only do protocol adaptation, authentication entry points, validation, and result conversion; the application layer orchestrates use cases and transactions; the domain layer carries invariants; the infrastructure layer handles persistence and external systems.
- Never execute Mapper/Repository/remote queries inside any loop, Stream callback, recursion, or per-item async task. First collect and deduplicate IDs, execute a batch query, then build a `Map<ID, Entity>` for backfill; batch writes follow the same pattern. Any suspected N+1 must be flagged and fixed, or the blocking reason explained.
- REST APIs use resource nouns, correct HTTP methods and status codes, and explicit idempotency and pagination semantics. Forbidden: `POST /getXxx`, unbounded full-list endpoints, always returning HTTP 200, or exposing DOs/aggregates directly.
- Spring Bean dependencies must be `private final` and constructor-injected; use the real Lombok annotation `@RequiredArgsConstructor`. `@RequiredConstructorArgs` does not exist — never copy that spelling. Field injection and `ApplicationContext.getBean()` in general business code are forbidden.
- Any class that logs uses Lombok `@Slf4j`. Forbidden: `System.out/err`, string-concatenated log messages, logging passwords/tokens/full phone numbers/national IDs/bank card numbers, or large objects. Do not log the same exception multiple times.
- Business exceptions may only be constructed from status-code enums, e.g. `throw new BizException(OrderErrorCode.ORDER_NOT_FOUND)`. Free-form strings, magic numbers, or bare runtime exceptions to express business failure are forbidden.
- All inbound request exceptions must be converted by the unified global exception handler. Scattered `try/catch` blocks in Controllers assembling error responses are forbidden; never return stack traces, SQL, or internal class names to clients.
- Authentication and authorization use Sa-Token uniformly. Running a homegrown token parser alongside it, a second authorization framework, or relying on frontend button hiding for authorization is forbidden. Object-level permissions must be checked server-side.
- PostgreSQL is the source of truth for transactions. Redis and Elasticsearch must never be the sole source of truth for strongly consistent facts such as orders, inventory, or payments. Between the database and RabbitMQ use an Outbox / reliable-events scheme; never claim that a plain local transaction atomically commits both.
- All external input goes through Bean Validation and business validation; SQL uses parameter binding; never commit secrets, default passwords, or production connection strings.

## Decision Priority

When rules conflict, protect in this order: business correctness and funds/inventory safety > authorization and data security > consistency and idempotency > module boundaries > maintainability > micro performance optimizations. Any concurrency design affecting orders, payments, inventory, or refunds must state its database constraints, locking/versioning strategy, idempotency keys, and failure-recovery path.

## Output Requirements

- Implementation tasks: deliver code, and state the business domain it belongs to, its layer placement, transaction/consistency approach, verification results, and unresolved risks.
- Review tasks: report gate violations first, with file and line numbers; distinguish "must fix" from "suggested improvement".
- Missing baselines: explicitly state "missing status-code enum system" or "missing global exception handler", explain what it blocks, and make building that baseline the first recommendation.
- Do not rewrite unrelated code at scale for the sake of conformance; new abstractions must be driven by the current use case or a clear reuse point.
