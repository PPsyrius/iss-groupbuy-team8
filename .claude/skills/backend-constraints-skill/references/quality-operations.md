# Testing, Logging, Observability, Performance, and Delivery

Read this when writing tests, reviewing quality, adding logs or metrics, or preparing for delivery.

## Testing Strategy

- Domain layer: pure JUnit unit tests covering invariants, state machines, boundary values, and failure paths - no Spring startup.
- Application layer: mock the ports and verify use-case orchestration, transaction preconditions, permissions, and events; do not mock behavior inside value objects/entities.
- Adapter: `@WebMvcTest`/contract tests covering serialization, validation, HTTP statuses, error bodies, and Sa-Token authentication.
- Infrastructure: use Testcontainers for PostgreSQL/MyBatis SQL, unique constraints, lock/concurrency behavior, and Redis serialization. (MQ/ES container tests apply only if those are later introduced.)
- End-to-end critical journeys: open/join a group deal, place an order, participation-count races, group completion, cancellation, refund, duplicate requests, and recovery flows.
- Concurrency tests must verify final database invariants, not just API success counts; include duplicate requests, out-of-order events, timeouts, and partial failures.
- Test data is independent, deterministic, and repeatable; dependence on execution order, real production accounts, or shared-environment leftovers is forbidden. Time, IDs, and randomness are injected through controllable ports.
- Fix a defect by first adding a test that reproduces the behavior; tests assert externally observable behavior, not private implementation details.

## Coverage and the SonarCloud Gate

- The enforced gate (server-side, in the CI `sonar-backend` job / `SonarCloud Backend` check): **≥ 80% coverage on new code**, plus no new blocker/critical issues and A ratings on new code. There is deliberately no `jacoco:check` Maven threshold (`docs/quality/quality-gates.md`) — coverage is judged only on new code in SonarCloud. Locally, hold touched files to 100% so the 80% floor is never in play.
- Coverage data flow: per-module JaCoCo exec → `jacoco-report-aggregate` (bound to `verify` in `blocbuy-bootstrap/pom.xml`) → `blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml` → SonarCloud via `sonar.coverage.jacoco.xmlReportPaths` in `backend/pom.xml` (an absolute `${maven.multiModuleProjectDirectory}` path — never override it with a relative one on the scanner command line; the scanner resolves relative paths per-module base dir and coverage silently reads 0%).
- **Only the aggregate report is authoritative.** Adapter classes are exercised by `blocbuy-bootstrap` tests; module-local reports miss that cross-module coverage. The aggregate exists only after `verify` — `mvn test` does not produce it.
- When writing tests for a controller/adapter change, cover every branch you added: failure `catch` arms, blank/null validation branches, and trivial endpoints (a one-line `logout()` counted as new code and sank a gate once). Happy-path-only tests are the most common cause of gate failures.
- A red Quality Gate can be stale: confirm which commit SonarCloud analyzed before assuming code is uncovered — the fix may already be committed but not yet analyzed.
- Pre-commit self-check after `./mvnw -B verify` (JDK 21): list per-file missed lines from the aggregate (snippet in SKILL.md's "Pre-Commit Quality Verification"); any miss in a file you touched means add or extend a test before committing.

## Architecture and Static Gates

- ArchUnit verifies the four-layer dependencies, no framework dependencies in domain, no cross-domain access to `mapper/persistence/internal` packages, and no Controller-to-Mapper dependency.
- Static analysis/code search intercepts field injection, `System.out`, bare `RuntimeException`, Controllers catching generic exceptions, SQL `${}`, sensitive logging, and database access in loops; the loop-query rule allows manual review but must never be silently ignored.
- CI runs at least compilation, formatting checks, unit tests, integration tests, architecture tests, dependency vulnerability scans, and secret scans. Main-branch builds must be reproducible from a clean environment.
- Rehearse forward-compatibility and rollback for migrations of important tables; run contract-compatibility checks for events/APIs.

## Logging

- Classes that log uniformly use `@Slf4j`; use placeholders: `log.info("order created, orderId={}, userId={}", orderId, maskedUserId)`.
- INFO records low-frequency business milestones and external-interaction summaries; WARN records recoverable exceptions/degradation; ERROR records system failures needing action. High-frequency per-item information uses DEBUG or metrics.
- Every request/message carries a traceId end to end; important async events carry eventId and aggregateId; do not rely on thread names as correlation keys.
- Logs are structured and masked; tokens, cookies, passwords, verification codes, private keys, full ID/payment data, and untrimmed request bodies are forbidden. Control log-field cardinality and length.
- Log an exception once, at the boundary where a handling decision can be made, including the cause; lower layers must not re-log and rethrow.
- Security audit logs record login, permission changes, merchant operations, price/inventory changes, refunds - with actor, action, object, result, time, source, and traceId - and are tamper-proof with restricted access.

## Metrics, Tracing, and Health Checks

- Use Spring Boot Actuator/Micrometer; correlate logs, metrics, and traces via traceId/business ID.
- RED: request Rate, Errors, Duration; add resources - connection pools, threads/virtual threads, GC, heap, Redis/PG latency. (RabbitMQ backlog / ES lag metrics apply only if those are later introduced.)
- Business metrics include order placement, payment success rate, group-completion latency, refund backlog, and scheduled-job success/failure counts.
- Metric labels use only low-cardinality dimensions; userId/orderId/eventId as metric tags are forbidden.
- Liveness only reflects whether the process is alive; readiness reflects whether it can take traffic. Do not let transient downstream failures trigger liveness restart storms.
- Create spans for external calls and key use cases; record safe summaries, not full payloads. Alerts must be actionable and have runbooks.

## Performance and Resilience

- Measure before optimizing: establish latency percentiles, throughput, error rate, resource, and data-volume baselines. Performance issues start by checking N+1, missing indexes, unbounded results, connection-pool waits, and large-object serialization.
- Every queue, thread pool, connection pool, batch, request body, page, and cache has a bound; under overload, explicitly reject/degrade to avoid cascading failure.
- Timeouts decrease from the entry-point budget toward downstream; circuit breaking applies only to remote boundaries where it makes sense. Fallbacks must never return fake success that corrupts funds/inventory.
- Graceful shutdown stops new traffic, waits a bounded time to finish requests/messages, then releases resources. Verify rolling compatibility of schema/messages/APIs during deployment.

## Definition of Done

- Code sits in the correct domain and layer; module dependencies pass.
- No loop queries/N+1; new queries have bounds, index justification, and a batch strategy.
- REST statuses, DTOs, validation, idempotency, error-code enums, and global exception mapping are all in place.
- Sa-Token login/permission/object-ownership checks cover both success and denial paths.
- Transaction, consistency, concurrency, and message duplication/reordering strategies have tests or explicit verification.
- New/changed code shows no missed lines or branches in the JaCoCo aggregate report (`./mvnw -B verify`, then inspect `blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml`) — the SonarCloud "≥ 80% on new code" gate must pass on the first CI run, not after a fix-up commit.
- Logs are masked; key metrics/traces are locatable; configuration holds no secrets.
- Maven verification passes; unverified items and risks are listed explicitly - never substitute "should be fine" for evidence.
