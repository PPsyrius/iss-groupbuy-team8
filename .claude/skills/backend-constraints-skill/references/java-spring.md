# Java 21, OOP, Spring Boot, and Maven

Read this when writing Java/Spring code, configuring beans, transactions, or build files.

## Java and OOP

- Keep classes and methods to a single, clear responsibility; prefer composition over inheritance. Inheritance expresses only a stable, substitutable is-a relationship; never build hierarchies to reuse a few lines of code.
- Replace primitive obsession with domain types such as `OrderId`, `Money`, `PhoneNumber`; validate in constructors and keep them immutable.
- Aggregates/entities do not use Lombok `@Data`; generate `equals/hashCode/toString` carefully, avoiding lazy fields, sensitive fields, mutable collections, or recursive associations.
- Expose immutable copies of collections; do not leak internal mutable state. `record` suits immutable DTO/value carriers, but types carrying complex invariants deserve an explicit class with factories.
- `Optional` is only for possibly-absent return values; do not use it as an entity field, DTO field, or method parameter, and never return a null Optional.
- Monetary amounts use `BigDecimal` with explicit scale/rounding/currency; compare with `compareTo`. Identifiers are never floating point; account for counter overflow.
- Instants use `Instant`; user/store calendar rules use `LocalDate`/`ZonedDateTime` with an explicit `ZoneId`; relying on the server default timezone or bare `new Date()` is forbidden.
- Resources use try-with-resources; catch specific exceptions and preserve the cause. Empty catch blocks, `printStackTrace`, swallowed exceptions, and unbounded retries are forbidden.
- `switch` must be exhaustive over business enums; fail fast on unknown states. Never persist or transmit enum ordinals.
- Eliminate shared mutable state where possible; use thread-safe components and bounded executors. Java 21 virtual threads suit high volumes of blocking I/O, but validate connection pools, ThreadLocals, rate limiting, and monitoring before rollout; CPU-bound tasks do not get faster on virtual threads. Preview APIs are disabled unless the project explicitly accepts them.
- Method parameters and return contracts state null semantics explicitly; validate at entry, preserve invariants inside. Do not scatter repetitive null checks that paper over model flaws.
- Comments explain reasons, constraints, and trade-offs rather than restating code; public contracts and complex algorithms get Javadoc. TODOs must reference an owner or ticket; vague placeholders are not allowed.

## Spring Beans and Configuration

- Spring beans use `@RequiredArgsConstructor` + `private final` constructor injection. Other injection styles are allowed only when the framework forces them, with the reason documented.
- Choose accurate stereotypes: Controller, Service/ApplicationService, Repository/Adapter. Not every object becomes a Spring bean; domain objects are created by business code.
- Configuration uses type-safe `@ConfigurationProperties` + `@Validated`, centralizing defaults and units; scattered `@Value` in business code is forbidden. Configuration classes carry no business logic.
- `@SpringBootApplication` sits in the top-level package; explicitly limit scanning boundaries to avoid accidentally scanning tests or other domains. Bean name conflicts are not solved by allowing overrides.
- External clients must configure connect/read/response timeouts, connection pools, request size limits, retry bounds, and graceful shutdown; calls without timeouts are forbidden.
- Retry only idempotent, transiently-failing operations, using exponential backoff, jitter, and a maximum count. Do not stack multiple retry layers that amplify each other.
- `@Async` uses a named, bounded executor and propagates necessary trace/security context; never rely on the caller's transaction extending into the async thread. Default unbounded thread pools are forbidden.

## Transactions

- `@Transactional` usually sits on the application layer's public command methods; queries use `readOnly = true`. Keep transactions short - no slow network calls, waiting, or large file processing inside a transaction.
- Understand Spring proxy limits: same-class self-invocation bypasses the transaction proxy; non-public methods and manually `new`-ed objects do not get proxied either. Do not paper over design problems by widening transaction scope.
- By default only unchecked exceptions roll back; if checked exceptions are used, `rollbackFor` must be explicit. Never catch an exception and return success, causing the transaction to commit.
- Before choosing an isolation level, state which concurrency anomaly you are preventing; use database unique constraints, conditional updates, row locks, or optimistic locking to maintain invariants. Deadlock/serialization-failure retries must be bounded and rerun the whole transaction.
- Side effects that depend on committed data fire only after commit; cross-DB/MQ uses an Outbox - see the messaging reference.

## Web and Serialization

- Controller parameters explicitly use `@PathVariable`, `@RequestParam`, `@RequestBody`; request bodies get `@Valid`; binding entities directly is forbidden.
- Jackson policies for unknown/sensitive fields, times, and enums stay consistent; enabling dangerous polymorphic deserialization is forbidden. Evolve DTOs backward-compatibly.
- Uploads restrict type, size, and count and use server-generated storage names; never trust filenames/Content-Type; guard against path traversal and malicious content.

## Maven

- The parent POM centralizes versions via `dependencyManagement`/Spring Boot BOM; child modules do not override casually. Commit the Maven Wrapper; builds pin the Java 21 toolchain/release.
- Maven Enforcer constrains at least Java/Maven versions, dependency convergence, and banned duplicate classes; pin plugin versions so CI is reproducible.
- Minimize dependencies and prefer official starters/clients; do not simultaneously pull overlapping logging, JSON, or HTTP client libraries. Scan CVEs and licenses periodically; run compatibility tests before upgrading.
- Builds should include formatting, static analysis, unit tests, integration tests, and architecture tests; quality-gate failures must not be skipped by default profiles.
- Production artifacts contain no test secrets, dev configuration, unused samples, or local absolute paths.
