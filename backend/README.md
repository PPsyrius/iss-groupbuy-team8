# BlocBuy Backend

Java 21 · Spring Boot 3.5.15 · MyBatis · PostgreSQL 18 · Redis 8.10 · Sa-Token · Maven (COLA multi-module)

Locked decisions and rationale: [docs/design/backend-skeleton-plan.md](docs/design/backend-skeleton-plan.md).

## Modules

| Module | Role |
|---|---|
| `blocbuy-client` | External DTOs, API constants (`/api/v1`, headers); no business code |
| `blocbuy-domain` | Aggregates, VOs, domain services, repository ports, error-code enums — JDK + Lombok only |
| `blocbuy-application` | Commands/queries, use-case services (transaction boundary), assemblers |
| `blocbuy-adapter` | Web controllers, global exception handler; protocol adaptation only |
| `blocbuy-infrastructure` | MyBatis mappers/DOs, repository impls, hand-written converters, Redis, Sa-Token RBAC |
| `blocbuy-bootstrap` | `BlocBuyApplication`, `application*.yml`, final assembly |

Domains (v1): `identity`, `groupbuy`, `order`, `catalog`, `notification`.

## Prerequisites

- JDK 21 (e.g. `export JAVA_HOME=$(brew --prefix openjdk@21)`); Maven via committed `./mvnw` wrapper (3.9.x).
- Docker (for the Testcontainers PG smoke test and local stack).

## Common commands

```bash
./mvnw verify                                  # compile + unit tests + ArchUnit + checkstyle + ITs
./mvnw spring-boot:run                         # run app (root-safe; only bootstrap executes)
./mvnw verify -Popenapi-export                 # regenerate ../contracts/openapi.json
```

Profiles: `local` (default; localhost PG/Redis) · `docker` (compose service names) ·
`openapi-export` (build-only, no live infra).

Secrets come from the environment, never from git: `DB_USERNAME`, `DB_PASSWORD`, `REDIS_PASSWORD`.
Local datasource: `jdbc:postgresql://localhost:5432/Blocbuy?currentSchema=domain` — note the
database name is case-sensitive if created quoted; verify `Blocbuy` vs `blocbuy` on first connect.

## Build gates

- Maven Enforcer: Java [21,22), Maven [3.9,), dependency convergence.
- Checkstyle: Google checks, 120-column line length (`checkstyle.xml`).
- ArchUnit (`blocbuy-bootstrap/src/test/.../architecture/`): layer direction, framework-free domain,
  no controller→mapper, persistence internals stay internal, no field injection.
- Tests: JUnit 5; `PgSmokeIT` spins a real PostgreSQL 18 via Testcontainers.

## API conventions

- Base path `/api/v1`; errors are RFC 9457 Problem Details + stable `code` + `traceId`.
- Auth: Sa-Token, one token domain, Redis-backed sessions, RBAC from identity tables.
- Write endpoints define idempotency via the `Idempotency-Key` header.
- Lists paginate with a 100-item page cap; cursor field reserved for keyset pagination.
