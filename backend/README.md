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
| `blocbuy-infrastructure` | MyBatis mappers/DOs, repository impls, hand-written converters, Redis, Sa-Token RBAC, Stripe gateway (only module referencing `com.stripe`) |
| `blocbuy-bootstrap` | `BlocBuyApplication`, `application*.yml`, final assembly |

Domains (v1): `identity`, `groupbuy`, `order`, `catalog`, `notification`, `payment` (Stripe; added 2026-08-31).

## Prerequisites

**- JDK 21 (e.g. `export JAVA_HOME=$(brew --prefix openjdk@21)`); Maven via committed `./mvnw` wrapper (3.9.x).
- Docker (for the Testcontainers PG smoke test and local stack).

## Common commands

```bash
./mvnw spotless:apply                          # auto-format (google-java-format AOSP) — run before commit
./mvnw verify                                  # format check + compile + unit tests + ArchUnit + checkstyle + SpotBugs + JaCoCo + ITs
./mvnw spring-boot:run                         # run app (root-safe; only bootstrap executes)
./mvnw verify -Popenapi-export                 # regenerate ../contracts/openapi.json
docker build -t blocbuy-backend .              # production image (Dockerfile at backend/)
```

Profiles: `local` (default; localhost PG/Redis) · `docker` (compose service names) ·
`openapi-export` (build-only, no live infra).

Secrets come from the environment, never from git: `DB_USERNAME`, `DB_PASSWORD`, `REDIS_PASSWORD`,
`STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`.
Local datasource: `jdbc:postgresql://localhost:5432/Blocbuy?currentSchema=domain` — note the
database name is case-sensitive if created quoted; verify `Blocbuy` vs `blocbuy` on first connect.

## Build gates

- Maven Enforcer: Java [21,22), Maven [3.9,), dependency convergence.
- Spotless (`spotless-maven-plugin`): google-java-format **AOSP** (4-space indent) — the single source
  of truth for formatting; checked at `validate`, fixed with `./mvnw spotless:apply`.
- Checkstyle: Google checks, 120-column line length, AOSP indentation values (`checkstyle.xml`);
  enforces only what the formatter does not own (naming, imports, Javadoc, blocks).
- SpotBugs: effort Max, threshold Medium, exclusions with written reasons in `spotbugs-excludes.xml`.
- JaCoCo: per-module reports + aggregate at `blocbuy-bootstrap/target/site/jacoco-aggregate/`;
  coverage gate (≥80% on new code) lives in the SonarQube quality gate, not Maven.
- ArchUnit (`blocbuy-bootstrap/src/test/.../architecture/`): layer direction, framework-free domain,
  no controller→mapper, persistence internals stay internal, no field injection.
- Tests: JUnit 5; `PgSmokeIT` spins a real PostgreSQL 18 via Testcontainers.

CI: `.github/workflows/ci-backend.yml` (build-quality → contract-check → sonar → docker) and
`security.yml` (Gitleaks + Trivy). Runbook: [../docs/ci/backend-pipeline.md](../docs/ci/backend-pipeline.md);
thresholds: [../docs/quality/quality-gates.md](../docs/quality/quality-gates.md).

## API conventions

- Base path `/api/v1`; errors are RFC 9457 Problem Details + stable `code` + `traceId`.
- Auth: Sa-Token, one token domain, Redis-backed sessions, RBAC from identity tables.
  Exception: `POST /api/v1/webhooks/stripe` is a public path; authenticity comes from Stripe
  signature verification in the gateway, not session auth.
- Write endpoints define idempotency via the `Idempotency-Key` header.
- Lists paginate with a 100-item page cap; cursor field reserved for keyset pagination.
