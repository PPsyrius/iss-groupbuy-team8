# BlocBuy Backend

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
| `blocbuy-bootstrap` | `BlocBuyApplication`, `application*.yml`, final assembly; publishes the plain `blocbuy-backend.jar` (a library for the services) and the runnable `blocbuy-backend-exec.jar` (the monolith) |
| `blocbuy-platform` | SWE5001 service support: topology guard, outbound trace propagation, `application-aws.yml`, per-service grants (`db/swe5001`); never on the monolith's classpath |
| `services/identity-svc`, `commerce-svc`, `notification-svc` | SWE5001 service assemblies: entry class, `application-<service>.yml`, own component and mapper scan ([backend ADR 0008](docs/adr/0008-dual-deployment-topology.md)) |

Domains (v1): `identity`, `groupbuy`, `order`, `catalog`, `notification`, `payment` (Stripe; added 2026-08-31).

## Prerequisites

- JDK 21 (e.g. `export JAVA_HOME=$(brew --prefix openjdk@21)`); Maven via committed `./mvnw` wrapper (3.9.x).
- Docker (for the Testcontainers PostgreSQL integration test suites and local stack).

## Common commands

For the local Docker stack, put `DB_USERNAME`, `DB_PASSWORD`, and `REDIS_PASSWORD` in `backend/.env` (gitignored), then run `docker compose up -d` from this directory. Use `docker compose up -d postgres redis` when running the backend natively. If you previously used a repository-root `.env`, move your local copy into `backend/` without committing it.

```bash
./mvnw spotless:apply                          # auto-format (google-java-format AOSP) — run before commit
./mvnw verify                                  # format check + compile + unit tests + ArchUnit + checkstyle + SpotBugs + JaCoCo + ITs
./mvnw spring-boot:run                         # run app (root-safe; only bootstrap executes)
./mvnw verify -Popenapi-export                 # regenerate ../contracts/openapi.json
docker build -t blocbuy-backend .              # production image (Dockerfile at backend/)
```

Profiles: `local` (default; localhost PG/Redis) · `docker` (compose service names) · `ec2` (host loopback) · `openapi-export` (build-only, no live infra) · `identity` / `commerce` / `notification` (service assemblies only) · `aws` (ECS services: Redis TLS, JSON logs). Set `BLOCBUY_STRIPE_CHECKOUT_ENABLED=true` with Sandbox credentials and return URLs to opt into Checkout; it defaults to `false` in every profile. `stripe-sprint2` remains a one-release compatibility alias. Payment rollout and pre-live gates: [Stripe release boundary](docs/payment/stripe-release-boundary.md).

The monolith is the default topology everywhere and the SWE5006 `dev` system. The SWE5001 services set `blocbuy.topology=distributed` in their own profile and refuse to start otherwise. Run one locally after `./mvnw -DskipTests package`, for example `java -jar services/identity-svc/target/identity-svc.jar --spring.profiles.active=local,identity` with the same environment variables as the monolith. Until #467 and #468 land, only `identity-svc` starts on its own: `commerce-svc` and `notification-svc` still need the session-role lookup and the remote identity check.

Secrets come from the environment, never from git: `DB_USERNAME`, `DB_PASSWORD`, `REDIS_PASSWORD`, `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, `TYPESAFE_API_KEY`. Set `TYPESAFE_ENABLED=true` to allow opt-in Jev re-ranking for listing searches; model, endpoint, and timeout settings have safe defaults in `application.yml`.

Scheduled jobs are deployment-opt-in. Before setting `BLOCBUY_SCHEDULING_ENABLED=true`, count overdue open teams, overdue unfulfilled purchase intents (including paid refusal cases) and due refund obligations; the team and intent expiry sweeps each select at most 100 candidates per run and the refund worker attempts one due obligation per tick. This switch can move money through the configured Stripe gateway even under the EC2-only profile; the gateway requires a sandbox key. To change or pause scheduling, follow the [deployment recreation procedure](../deployment/backend/README.md#change-the-scheduling-flag); restarting an existing container does not reload its environment.

Local datasource: `jdbc:postgresql://localhost:5432/Blocbuy?currentSchema=domain` — note the database name is case-sensitive if created quoted; verify `Blocbuy` vs `blocbuy` on first connect.

## Build gates

- Maven Enforcer: Java [21,22), Maven [3.9,), dependency convergence.
- Spotless (`spotless-maven-plugin`): google-java-format **AOSP** (4-space indent) — the single source of truth for formatting; checked at `validate`, fixed with `./mvnw spotless:apply`.
- Checkstyle: Google checks, 120-column line length, AOSP indentation values (`checkstyle.xml`); enforces only what the formatter does not own (naming, imports, Javadoc, blocks).
- SpotBugs: effort Max, threshold Medium, exclusions with written reasons in `spotbugs-excludes.xml`.
- JaCoCo: aggregate report at `blocbuy-bootstrap/target/site/jacoco-aggregate/` plus per-module reports for `blocbuy-platform` and `services/*`, which the monolith does not depend on; coverage gate (≥80% on new code) lives in the SonarQube quality gate, not Maven.
- ArchUnit (`blocbuy-bootstrap/src/test/.../architecture/`): layer direction, framework-free domain, no controller→mapper, persistence internals stay internal, no field injection.
- Tests: JUnit 5; repository `*IT` suites spin a real PostgreSQL 18 via Testcontainers.

CI: `.github/workflows/ci-backend.yml` (build-quality → contract-check → sonar-backend → docker) and `security.yml` (Gitleaks + Trivy). Runbook: [../docs/ci/backend-pipeline.md](../docs/ci/backend-pipeline.md); thresholds: [../docs/quality/quality-gates.md](../docs/quality/quality-gates.md).

## API conventions

- Base path `/api/v1`; JSON successes and failures use `Result<T>` with stable `code` and `message`, optional `data`, `errors` and `traceId`, and a matching HTTP status for failures.
- Auth: Sa-Token, one token domain, Redis-backed sessions, RBAC from identity tables. Exception: `POST /api/v1/webhooks/stripe` is a public path; authenticity comes from Stripe signature verification in the gateway, not session auth.
- Write endpoints define idempotency via the `Idempotency-Key` header.
- Lists paginate with a 100-item page cap; cursor field reserved for keyset pagination.

Unpaid-intent cleanup (#225) uses `blocbuy.scheduling.purchase-intent-expiry` (interval `PT1M`, initial delay `PT30S`, renewable lease `PT2M`, batch size `100` with a 1..500 bound). It soft-marks intents and never calls Stripe; late provider facts still use settlement/refund. Expiry, skipped and failed counts use `blocbuy.purchase.intent.expiry.intents` with the `result` tag. Busy candidates and failures defer for five minutes; confirmed paid candidates leave this job's queue permanently. Recovery writes have a three-second statement timeout so lock contention cannot freeze the leased run. See [ADR 0001](docs/adr/0001-payment-business-integration.md#addendum-2026-09-30-expire-unpaid-intents-225-us30) for locks, migration recovery and rollout.
