# BlocBuy Backend

| | |
|---|---|
| **CI** | [![Backend CI](https://github.com/PPsyrius/iss-groupbuy-team8/actions/workflows/ci-backend.yml/badge.svg?branch=dev)](https://github.com/PPsyrius/iss-groupbuy-team8/actions/workflows/ci-backend.yml) [![Security](https://github.com/PPsyrius/iss-groupbuy-team8/actions/workflows/security.yml/badge.svg?branch=dev)](https://github.com/PPsyrius/iss-groupbuy-team8/actions/workflows/security.yml) |
| **Quality** | [![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=blocbuy-backend&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=blocbuy-backend) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=blocbuy-backend&metric=coverage)](https://sonarcloud.io/component_measures?id=blocbuy-backend&metric=coverage&view=list) [![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=blocbuy-backend&metric=sqale_rating)](https://sonarcloud.io/component_measures?id=blocbuy-backend&metric=sqale_rating) [![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=blocbuy-backend&metric=reliability_rating)](https://sonarcloud.io/component_measures?id=blocbuy-backend&metric=reliability_rating) |
| **Code** | ![Java](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2FPPsyrius%2Fiss-groupbuy-team8%2Fdev%2Fbackend%2Fpom.xml&query=%2F%2A%5Blocal-name%28%29%3D%27project%27%5D%2F%2A%5Blocal-name%28%29%3D%27properties%27%5D%2F%2A%5Blocal-name%28%29%3D%27java.version%27%5D&label=java) ![Spring Boot](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2FPPsyrius%2Fiss-groupbuy-team8%2Fdev%2Fbackend%2Fpom.xml&query=%2F%2A%5Blocal-name%28%29%3D%27project%27%5D%2F%2A%5Blocal-name%28%29%3D%27parent%27%5D%2F%2A%5Blocal-name%28%29%3D%27version%27%5D&label=spring%20boot) ![PostgreSQL](https://img.shields.io/badge/postgresql-18-blue) ![Redis](https://img.shields.io/badge/redis-8.10-blue) |
| **Release** | [![Latest Tag](https://img.shields.io/github/v/tag/PPsyrius/iss-groupbuy-team8?label=tag)](https://github.com/PPsyrius/iss-groupbuy-team8/tags) |
| **GitHub** | [![Contributors](https://img.shields.io/github/contributors/PPsyrius/iss-groupbuy-team8)](https://github.com/PPsyrius/iss-groupbuy-team8/graphs/contributors) [![Last Commit](https://img.shields.io/github/last-commit/PPsyrius/iss-groupbuy-team8/dev?label=last%20commit)](https://github.com/PPsyrius/iss-groupbuy-team8/commits/dev/) |

Java 21 · Spring Boot 4.1.1 · MyBatis · PostgreSQL 18 · Redis 8.10 · Sa-Token · Maven (COLA multi-module)

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
