# Repository Structure

This document describes the layout of the BlocBuy monorepo, the purpose of each directory, and the development workflows supported by the `Makefile` and `docker-compose.yml`.

The repository is a **polyglot monorepo**: a Java Spring Boot backend, a React SPA frontend, Terraform infrastructure, CI/CD pipelines, and all project documentation live in a single repository. This gives us end-to-end traceability — every issue, pull request, pipeline run, design decision, and report revision is linked in one place, which is itself evidence of engineering discipline for the project report.

---

## Directory Tree

```
blocbuy/
├── .github/
│   ├── workflows/
│   │   ├── ci-backend.yml          # mvn verify → regenerates openapi.json → fails if contracts/ dirty
│   │   ├── ci-frontend.yml         # generate types from contracts/ → typecheck → test → build
│   │   ├── security.yml            # SonarCloud, OWASP Dep-Check, Trivy, Gitleaks
│   │   ├── terraform.yml           # plan on PR, gated apply
│   │   ├── agile-metrics.yml       # cron: GitHub GraphQL → docs/agile/metrics/
│   │   └── _reusable-*.yml
│   ├── ISSUE_TEMPLATE/             # user-story.yml, bug.yml, task.yml
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── CODEOWNERS
├── contracts/
│   └── openapi.json                # committed; single source of truth for the API
├── backend/                        # Spring Boot service (Maven, COLA multi-module)
│   ├── pom.xml                     # blocbuy-parent: aggregate + dependencyManagement + gates
│   ├── .mvn/wrapper/ + mvnw        # committed Maven Wrapper (Maven 3.9.x)
│   ├── checkstyle.xml              # Google checks, line length 120
│   ├── blocbuy-client/             # external DTOs, API contracts; no business code
│   ├── blocbuy-domain/             # aggregates, VOs, domain services, ports; JDK-only deps
│   ├── blocbuy-application/        # command/query, use-case services, assemblers; tx boundary
│   ├── blocbuy-adapter/            # controllers; protocol adaptation only
│   ├── blocbuy-infrastructure/     # Mapper+DO+RepositoryImpl+Converter, Redis, Sa-Token impl
│   ├── blocbuy-bootstrap/          # @SpringBootApplication, application*.yml, assembly
│   └── docs/design/                # backend-local design notes (skeleton plan, etc.)
├── frontend/                       # React SPA
│   ├── src/
│   │   └── api/generated/          # gitignored; regenerated from contracts/
│   ├── package.json
│   └── turbo.json
├── infra/
│   ├── modules/                    # reusable Terraform modules
│   ├── envs/                       # dev/, prod/ environment compositions
│   └── policies/                   # OPA/Conftest policy-as-code
├── scripts/
│   ├── agile-metrics/              # GraphQL dump + burndown chart renderer
│   └── generate-client.sh          # openapi-typescript wrapper
├── e2e/                            # cross-stack end-to-end tests
├── docs/
│   ├── proposal/                   # project proposal
│   ├── design/
│   │   ├── adr/                    # Architecture Decision Records (numbered)
│   │   ├── analysis/               # use cases, domain model, state machines (Mermaid)
│   │   ├── architecture/           # logical & deployment views, analysis→design transition
│   │   └── patterns/               # one document per design pattern applied
│   ├── agile/
│   │   ├── sprint-01/              # plan.md, review.md, retro.md (one dir per sprint)
│   │   └── metrics/                # auto-committed sprint CSVs + burndown charts
│   ├── reports/
│   │   ├── progress/               # fortnightly progress reports
│   │   └── final/                  # final project report
│   └── presentation/               # final presentation materials
├── docker-compose.yml              # local runtime: postgres + backend + frontend
├── Makefile                        # single entrypoint for every dev & CI task
└── README.md
```

---

## Directory Reference

### `.github/` — Pipelines & Collaboration Templates

The DevSecOps pipeline lives here — GitHub Actions can only execute workflows from `.github/workflows/`, so this *is* the "pipeline component" of the repository.

| File | Purpose |
|---|---|
| `ci-backend.yml` | Builds and tests the backend (`mvn verify`), regenerates `openapi.json` from the compiled code, and **fails the build if `contracts/` has uncommitted drift**. This guarantees the committed contract never lies. |
| `ci-frontend.yml` | Regenerates TypeScript types from `contracts/openapi.json`, then typechecks, tests, and builds the SPA. |
| `security.yml` | Static analysis and supply-chain scanning: SonarCloud (code quality), OWASP Dependency-Check (CVEs in dependencies), Trivy (container image scan), Gitleaks (secret detection). |
| `terraform.yml` | Runs `terraform plan` on every PR touching `infra/`; `apply` is gated behind environment-specific approvals. |
| `agile-metrics.yml` | Scheduled workflow that queries the GitHub Projects GraphQL API and commits sprint metrics into `docs/agile/metrics/`. Our Agile evidence collection is itself automated — a DevSecOps deliverable. |
| `ISSUE_TEMPLATE/` | Structured templates. The user-story template enforces acceptance criteria on every story — an Agile process artifact in its own right. |
| `PULL_REQUEST_TEMPLATE.md` | Checklist linking PRs back to issues, keeping the issue → PR → pipeline traceability chain intact. |
| `CODEOWNERS` | Routes review requests automatically (e.g. `infra/` changes ping the infra owner). |

### `contracts/` — API Contract (Neutral Ground)

The single committed `openapi.json` is the source of truth for the API, owned by neither the backend nor the frontend directory.

- **Backend writes it**: springdoc-openapi generates the spec from controller annotations at build time (code-first).
- **Frontend reads it**: TypeScript types/client are generated from this file — the frontend never needs a running backend to compile.
- **CI enforces it**: the backend pipeline fails if the generated spec differs from the committed one, so every API change appears as a reviewable diff in the PR.

See ADR-0003 for the rationale and trade-offs.

### `backend/` — Spring Boot Service

The monolithic Java 21 + Spring Boot 3.5.15 backend with PostgreSQL 18 and Redis 8.10 — no message broker and no search engine in the initial stack (decision D5, 2026-08-30). It is a **COLA multi-module Maven aggregate** (`blocbuy-parent` + `client` / `domain` / `application` / `adapter` / `infrastructure` / `bootstrap`), self-contained under `backend/` with no repo-root POM. Layer dependencies are machine-enforced twice: by Maven module dependencies and by ArchUnit tests.

Within each module, code is organized **first by business domain** — the confirmed v1 domains are `identity`, `groupbuy`, `order`, `catalog`, `notification` (root package `com.blocbuy`). A global `controller/service/mapper/entity` grab-bag is forbidden. Unit tests are colocated under each module's `src/test/java` mirroring the main source tree — "well structured source code with unit test cases for classes with more complex behaviours" is a named module deliverable, and colocated tests keep that evidence next to the code it covers.

Key conventions:

- API base path `/api/v1`; errors use RFC 9457 Problem Details + stable `code` + `traceId`; no unified success envelope.
- Auth: Sa-Token with a single token domain, Redis-backed sessions, RBAC roles/permissions loaded from the identity SQL tables.
- Persistence: MyBatis (Mappers never leave the infrastructure layer), Flyway migrations, hand-written DO↔domain converters.
- Datasource: database `Blocbuy`, PostgreSQL schema `domain`; secrets arrive via environment variables, never committed.
- Build gates: Maven Enforcer (Java 21 / Maven ≥3.9 / dependency convergence), Checkstyle (Google checks, 120 columns), ArchUnit, JUnit 5 + Testcontainers.

The full locked decision set and execution order live in `backend/docs/design/backend-skeleton-plan.md`.

### `frontend/` — React SPA

The web frontend. `src/api/generated/` holds the TypeScript API client generated from `contracts/openapi.json`; it is **gitignored** and regenerated via `make generate-client`. `turbo.json` is scoped here (not at repo root) since Turborepo orchestrates only the frontend's own task graph.

### `infra/` — Terraform Infrastructure

| Subdirectory | Purpose |
|---|---|
| `modules/` | Reusable Terraform modules (network, compute, database, etc.). |
| `envs/` | Per-environment compositions (`dev/`, `prod/`) that instantiate the modules with environment-specific variables and remote state backends. |
| `policies/` | OPA/Conftest policies evaluated in the pipeline — policy-as-code guardrails (e.g. no public S3 buckets, mandatory tags) that form part of the DevSecOps write-up. |

### `scripts/` — Automation Helpers

Cross-cutting scripts invoked by the Makefile and the pipelines:

- `agile-metrics/` — the GraphQL dump + burndown renderer behind `agile-metrics.yml`; can also be run manually via `make metrics`.
- `generate-client.sh` — wraps `openapi-typescript` so client generation is one command everywhere (dev machines and CI).

### `e2e/` — End-to-End Tests

Cross-stack tests that exercise the fully containerized system (`make up`) through the browser/API. Kept outside `frontend/` because they test the integrated system, not one app.

### `docs/` — All Written Deliverables

Everything gradeable-in-writing lives here, versioned alongside the code it describes.

| Subdirectory | Contents |
|---|---|
| `proposal/` | The project proposal (title, overview, architecture, scope, effort estimates). |
| `design/adr/` | Numbered Architecture Decision Records (`0001-monolith.md`, `0002-java-stack.md`, `0003-contract-in-repo.md`, …). Written *when decisions are made*, not reconstructed later — these feed the report's "key architectural decisions" section directly. |
| `design/analysis/` | OOAD analysis artifacts: use case specifications, domain model, state machines — all diagrams in Mermaid so they diff and review like code. |
| `design/architecture/` | High-level design: logical and deployment views, and the analysis→design transition strategy. |
| `design/patterns/` | One document per design pattern applied: the problem, forces, chosen pattern, and pointers to the implementing classes. This chapter is the primary grade lever — keep it current every sprint. |
| `agile/sprint-NN/` | Sprint plan, sprint review, and retrospective for each sprint. |
| `agile/metrics/` | Auto-committed sprint snapshots (issue-state CSVs, burndown charts) from `agile-metrics.yml`. This is the durable evidence of our Agile process — the GitHub Projects board UI is not part of the git archive, so metrics are exported into it. |
| `reports/progress/` | Fortnightly progress reports (`2026-09-14.md`, …) as submitted to CANVAS. |
| `reports/final/` | The final project report sources. |
| `presentation/` | Final presentation slides and assets. |

### `docker-compose.yml` — Local Runtime Definition

Defines the three local services — `postgres`, `backend`, `frontend` — their networking, environment, and health checks. Each service is independently startable, which is what enables the partial-stack dev modes described below. The frontend service is an nginx container serving the built bundle with a parameterized backend upstream (`BACKEND_URL`). This is a **local integration convenience only**: in the deployed architecture the frontend is static files on S3 behind CloudFront, and CloudFront behaviors — not nginx — perform the `/api/*` routing. The nginx config exists solely so the containerized stack is self-contained for demos and e2e runs.

---

## The Makefile — One Entrypoint for Everything

### Why a Makefile when we have docker-compose?

They solve different problems:

- **`docker-compose.yml` defines services** — what runs, how it's wired, which ports and env vars. It is a runtime description.
- **The `Makefile` defines tasks** — the verbs of daily development. Many of these involve no container at all: generating the API client, running `mvn verify` on the local JVM, linting, checking contract drift, rendering sprint metrics.

The Makefile is the **team's onboarding contract**. Five part-time engineers on different machines (macOS, Windows, Linux) each remember one thing: `make <verb>`. Nobody needs to recall whether tests run with `mvn verify` or `./mvnw test`, which directory to `cd` into, or which flags `openapi-typescript` needs. CI calls the *same targets*, which shrinks "works on my machine, fails in CI" drift to near zero.

Design rule: **keep targets thin**. A target is a memorable name plus at most ~5 lines. Anything longer moves into `scripts/` and the target calls the script.

### Target Reference

```makefile
## ── Stack lifecycle ─────────────────────────────────────────────
up:                # full containerized stack (local demo / e2e)
	docker compose up -d --build

down:
	docker compose down

## ── Daily dev modes (see next section) ──────────────────────────
dev-frontend:      # backend+db in containers; frontend native with HMR
	docker compose up -d postgres backend
	cd frontend && npm run dev

dev-backend:       # db+frontend in containers; backend native (IDE or CLI)
	docker compose up -d --build postgres frontend
	cd backend && ./mvnw spring-boot:run

## ── Build & test ────────────────────────────────────────────────
test: test-backend test-frontend

test-backend:
	cd backend && ./mvnw verify

test-frontend:
	cd frontend && npm test

lint:
	cd backend && ./mvnw checkstyle:check
	cd frontend && npx oxlint

e2e: up
	cd e2e && npm test

## ── API contract ────────────────────────────────────────────────
generate-client:   # contracts/openapi.json → frontend/src/api/generated/
	./scripts/generate-client.sh

contract-check:    # regenerate spec from code; fail if contracts/ drifts
	cd backend && ./mvnw verify -Popenapi-export
	git diff --exit-code contracts/

## ── Project ops ─────────────────────────────────────────────────
metrics:           # manual run of the sprint metrics dump + burndown
	./scripts/agile-metrics/run.sh
```

### How Each Role Uses It

#### Frontend engineer — `make dev-frontend`

Runs postgres and the backend as containers (run-only, no rebuilding), and the frontend natively via the Vite dev server with hot module replacement — every file save reflects in the browser instantly.

Vite proxies API calls to the containerized backend (`vite.config.ts`):

```ts
server: {
  proxy: { '/api': 'http://localhost:8080' }
}
```

The browser talks to `localhost:5173`; Vite forwards `/api/*` to the backend container's published port. No CORS configuration needed.

After pulling `main`, if the API changed: `make generate-client` refreshes the typed client from `contracts/openapi.json` — no need to run or even build the backend.

#### Backend engineer — `make dev-backend`

Runs postgres and the frontend as containers, and the backend natively — either:

- **IDE (typical)**: run the compose line, then Run/Debug from IntelliJ. With `spring-boot-devtools` on the classpath, rebuilding (`Ctrl+F9` or build-on-save) hot-restarts the application in ~2 seconds with the debugger still attached.
- **CLI**: `make dev-backend` runs `./mvnw spring-boot:run`, which picks up the same devtools hot-restart.

Two mechanics to be aware of:

1. **Routing**: the frontend container's nginx proxies `/api` to `BACKEND_URL`, which in this mode is overridden to `host.docker.internal:8080` so the container reaches the natively-running backend on the host. `extra_hosts: ["host.docker.internal:host-gateway"]` in compose makes this work on Linux too (harmless on Docker Desktop).
2. **Stale frontend build**: the frontend container serves whatever was baked at its last image build. The `--build` flag in the target rebuilds it on every start, so after pulling `main` you always see the current UI — this removes an entire class of "it's broken for me" confusion.

Datasource note: the backend uses Spring profiles to switch its database URL — `local` profile → `jdbc:postgresql://localhost:5432/Blocbuy?currentSchema=domain` (backend on host), `docker` profile → host `postgres` (backend in the compose network). Redis follows the same pattern (`localhost:6379` vs host `redis`). Credentials come from environment variables (`DB_USERNAME`, `DB_PASSWORD`, `REDIS_PASSWORD`) in both profiles.

#### Everyone — cross-stack verbs

| Command | When |
|---|---|
| `make up` | Full-stack demo, sprint review, or before running e2e — everything containerized and self-contained. Note: the backend container matches the deployed ECS artifact, but the frontend's nginx serving path is local-only (prod serves the same bundle from S3/CloudFront). |
| `make test` | Before pushing: runs the entire test suite across both stacks. |
| `make contract-check` | After changing any controller/DTO: regenerates the spec and fails locally if you forgot to commit the updated contract — the same check CI runs, so you catch it before the PR does. |
| `make lint` | Same linters, same configs as the `security.yml`/CI runs. |
| `make metrics` | Sprint boundary: manually trigger the metrics snapshot if the scheduled run needs supplementing. |

### CI Uses the Same Targets

The workflows in `.github/workflows/` call the identical Makefile targets (`make test-backend`, `make contract-check`, …) rather than duplicating command lines in YAML. One definition of every task, exercised on every laptop and every pipeline run.
