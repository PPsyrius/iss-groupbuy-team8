# Repository Structure

This document describes the layout of the BlocBuy monorepo, the purpose of each directory, and the local commands behind the backend Maven wrapper and `docker-compose.yaml`. Verified against the tree on 2026-09-21; if it drifts again, the tree — not this file — wins.

The repository is a **polyglot monorepo**: a Java Spring Boot backend, a React SPA frontend, CI/CD pipelines, deployment policy/scripts, Terraform infrastructure, and project documentation live together. Delivery reuses an existing EC2 host for dev; production infrastructure is Terraform-managed.

---

## Directory Tree

```
blocbuy/
├── .agents/                        # agent working agreements
├── .claude/skills/                 # repo skills (agile-evidence-capture, backend-constraints-skill, pgsql-ddl)
├── .github/
│   ├── workflows/
│   │   ├── ci-backend.yml          # quality → image scan/publish → existing-EC2 SSM deployment
│   │   ├── ci-frontend.yml         # change detection → pnpm check gate → SonarCloud → S3/CloudFront delivery
│   │   ├── security.yml            # secrets, CVEs, config, workflows, shell and bundle policy
│   │   ├── rollback-backend.yml    # confirmed/manual existing-EC2 application rollback
│   │   ├── pr-labeler.yml          # applies labeler.yml to PRs
│   │   ├── typesafe-smoke.yml      # live TypeSafe Jev contract smoke (non-required)
│   │   └── board-snapshot.yml      # scheduled board metrics → docs/agile/metrics/
│   ├── dependabot.yml              # weekly grouped updates: maven, github-actions, docker, npm (frontend)
│   ├── ISSUE_TEMPLATE/             # user-story.yml, bug.yml, task.yml
│   ├── PULL_REQUEST_TEMPLATE.md
│   ├── labeler.yml
│   └── CODEOWNERS
├── .gitleaks.toml                  # secret-scan allowlists (placeholders, docs)
├── contracts/
│   └── openapi.json                # committed; single source of truth for the API
├── backend/                        # Spring Boot service (Maven, COLA multi-module)
│   ├── pom.xml                     # blocbuy-parent: aggregate + dependencyManagement + gates
│   ├── .mvn/wrapper/ + mvnw        # committed Maven Wrapper (Maven 3.9.x)
│   ├── checkstyle.xml              # Google checks, 120 cols, AOSP indentation values
│   ├── spotbugs-excludes.xml       # SpotBugs filter (each exclusion carries a reason)
│   ├── Dockerfile                  # multi-stage image (maven build → temurin 21 JRE, non-root); local compose builds
│   ├── Dockerfile.ci               # runtime-only twin: CI assembles from the build-quality boot-jar artifact
│   ├── .dockerignore
│   ├── blocbuy-client/             # external DTOs, API contracts; no business code
│   ├── blocbuy-domain/             # aggregates, VOs, domain services, ports; JDK-only deps
│   ├── blocbuy-application/        # command/query, use-case services, assemblers; tx boundary
│   ├── blocbuy-adapter/            # controllers; protocol adaptation only
│   ├── blocbuy-infrastructure/     # Mapper+DO+RepositoryImpl+Converter, Redis, Sa-Token impl
│   ├── blocbuy-bootstrap/          # @SpringBootApplication, application*.yml, assembly
│   └── docs/design/                # backend-local design notes (skeleton plan, etc.)
├── frontend/                       # React SPA (pnpm; Node 24 via .nvmrc/.mise.toml)
│   ├── src/routes/                 # TanStack file-based routes; routeTree.gen.ts committed, never edited
│   ├── src/features/<feature>/     # one feature per dir, colocated tests
│   ├── src/api/generated/          # gitignored; regenerated from contracts/ via pnpm gen:api
│   ├── e2e/                        # Playwright specs (smoke + login journeys)
│   ├── docs/                       # plan.md, ADRs, design tokens/screens, blueprint provenance
│   ├── AGENTS.md                   # agent conventions + Intent guidance block
│   ├── package.json                # exact pins; pnpm check gate; lefthook hook installer
│   └── lefthook-adjacent configs   # .oxlintrc.json, .oxfmtrc.json, knip.json, lefthook.yml at repo root
├── deployment/
│   ├── backend/
│   │   ├── compose.yml             # one backend service; host networking; no DB/Redis/volumes
│   │   ├── backend.env.example     # non-secret host template; real file stays on EC2
│   │   ├── bin/                    # preflight, health, deploy, rollback, fetch-env scripts
│   │   ├── iam/                    # replace-before-use least-privilege IAM JSON examples
│   │   └── ecr-lifecycle-policy.json
│   └── frontend/
│       ├── cloudfront/             # versioned SPA-fallback function + response-headers policy
│       └── iam/                    # frontend OIDC deploy-role examples
├── infra/terraform/                # production backend stack (Terraform-managed)
│   ├── backend-app-stack/          # OIDC roles, ECR, ALB, EC2, user-data bootstrap
│   ├── envs/prod.tfvars            # pinned prod values (dev stays hand-managed, out of scope)
│   └── README.md                   # init/plan/apply usage
├── docs/
│   ├── agile/                      # sprint logs, metrics snapshots, README index
│   ├── ci/                         # backend-pipeline.md (runbook), devsecops-pipeline-plan.md (decisions)
│   ├── deploy/                     # dev runbooks + prod/ Terraform runbook set
│   ├── design/                     # this file
│   └── quality/                    # quality-gates.md, nfr-register.md
├── docker-compose.yaml             # local integration runtime (postgres, redis, backend)
├── lefthook.yml                    # pre-commit hooks (repo root)
└── README.md
```

There is no repo-root `Makefile` and no `scripts/` directory: backend tasks run as `./mvnw …` from `backend/`, frontend tasks as `pnpm …` from `frontend/`, and CI calls the same commands.

---

## Directory Reference

### `.github/` — Pipelines & Collaboration Templates

The DevSecOps pipeline lives here — GitHub Actions can only execute workflows from `.github/workflows/`, so this *is* the "pipeline component" of the repository.

| File | Purpose |
|---|---|
| `ci-backend.yml` | Quality and contract gates, EC2-profile image smoke/Trivy scan, immutable ECR publication, and an enabled `dev` deployment to one existing EC2 instance through OIDC and SSM. |
| `security.yml` | Gitleaks, dependency/config Trivy, Actionlint, ShellCheck, one-service Compose enforcement and exact non-secret deployment archive policy; also runs weekly. |
| `rollback-backend.yml` | Manual, confirmed and reasoned application rollback through the protected `backend-dev` environment. |
| `pr-labeler.yml` | Applies `labeler.yml` path rules to every PR automatically. |
| `dependabot.yml` | Weekly grouped update PRs for Maven, GitHub Actions, Docker base images, and npm (`/frontend`). |
| `ci-frontend.yml` | Change detection, `pnpm check` as the required `frontend-check`, SonarCloud Frontend analysis, preview e2e, and gated S3/CloudFront delivery. |
| `typesafe-smoke.yml` | Live TypeSafe Jev contract smoke (non-required). |
| `board-snapshot.yml` | Scheduled board metrics committed to `docs/agile/metrics/`. |
| `ISSUE_TEMPLATE/` | Structured templates. The user-story template enforces acceptance criteria on every story — an Agile process artifact in its own right. |
| `PULL_REQUEST_TEMPLATE.md` | Checklist linking PRs back to issues, keeping the issue → PR → pipeline traceability chain intact. |
| `CODEOWNERS` | Routes review requests automatically (e.g. `infra/` changes ping the infra owner). |

### `contracts/` — API Contract (Neutral Ground)

The single committed `openapi.json` is the source of truth for the API, owned by neither the backend nor the frontend directory.

- **Backend writes it**: springdoc-openapi generates the spec from controller annotations at build time (code-first).
- **Frontend reads it**: TypeScript types/client are generated from this file — the frontend never needs a running backend to compile.
- **CI enforces it**: the backend pipeline fails if the generated spec differs from the committed one, so every API change appears as a reviewable diff in the PR.

See `frontend/docs/adr/0003-generated-client-not-committed.md` for the client-side rationale and trade-offs.

### `backend/` — Spring Boot Service

The Java 21 + Spring Boot 4.1.1 backend with PostgreSQL 18 and Redis 8.10 — no message broker and no search engine in the stack (decision D5, 2026-08-30; payment/Stripe added 2026-08-31). It is a **COLA multi-module Maven aggregate** (`blocbuy-parent` + `client` / `domain` / `application` / `adapter` / `infrastructure` / `bootstrap`), self-contained under `backend/` with no repo-root POM. Layer dependencies are machine-enforced twice: by Maven module dependencies and by ArchUnit tests.

Within each module, code is organized **first by business domain** — `identity`, `groupbuy`, `order`, `catalog`, `notification`, `payment` (root package `com.blocbuy`). A global `controller/service/mapper/entity` grab-bag is forbidden. Unit tests are colocated under each module's `src/test/java` mirroring the main source tree.

Key conventions:

- API base path `/api/v1`; every JSON endpoint returns the unified `Result<T>` envelope (`code`/`data`/`message`, plus `errors`/`traceId` when present) backed by `ErrorCode` status-code enums; HTTP status mirrors the outcome via the global exception handler.
- Auth: Sa-Token with a single token domain, Redis-backed sessions, RBAC roles/permissions loaded from the identity SQL tables.
- Persistence: MyBatis (Mappers never leave the infrastructure layer), Flyway migrations, hand-written DO↔domain converters.
- Datasource: database `Blocbuy`, PostgreSQL schema `domain`; secrets arrive via environment variables, never committed.
- Build gates: Maven Enforcer (Java 21 / Maven ≥3.9 / dependency convergence), Spotless (google-java-format AOSP formatting, checked at `validate`), Checkstyle (Google checks, 120 columns), SpotBugs (static bug analysis, threshold Medium), JaCoCo (coverage, gated via SonarQube quality gate), ArchUnit, JUnit 5 + Testcontainers. Thresholds: `docs/quality/quality-gates.md`.

The full locked decision set and execution order live in `backend/docs/design/backend-skeleton-plan.md`.

### `frontend/` — React SPA

The web frontend (React 19, TypeScript 7, Vite 8, pnpm 12, Node 24). `src/api/generated/` holds the hey-api client generated from `contracts/openapi.json`; it is **gitignored** and regenerated by `pnpm gen:api` (runs in `prepare`, first in `pnpm check` and `pnpm build`). One gate command: `pnpm check` (gen → typecheck → oxlint → oxfmt → vitest → knip). Playwright specs live in `frontend/e2e/`. Conventions: `frontend/AGENTS.md`; onboarding hub: `frontend/README.md`.

### `deployment/` — Application delivery

`deployment/backend/` is the host-side deployment contract (Compose, env template, lifecycle scripts, IAM examples, ECR policy). `deployment/frontend/` carries the versioned CloudFront function, response-headers policy, and OIDC role examples. See the [operator guide](../deploy/backend-ec2-pipeline-operator-guide.md) and [dev CloudFront guide](../deploy/dev-cloudfront-deployment.md).

### `infra/` — Terraform-managed production

`infra/terraform/` holds the production backend stack (`backend-app-stack/`: OIDC roles, ECR, ALB, EC2, user-data bootstrap) plus `envs/prod.tfvars` and usage docs. The dev environment stays hand-managed and out of scope. See `infra/terraform/README.md`.

### `docs/` — All Written Deliverables

Everything gradeable-in-writing lives here, versioned alongside the code it describes.

| Subdirectory | Contents |
|---|---|
| `agile/` | Sprint-log indexes (`sprint-NN/log.md`), daily entries (`sprint-NN/log/YYYY-MM-DD.md`), board snapshots (`metrics/`), README index. |
| `ci/` | `backend-pipeline.md` (CI runbook) and `devsecops-pipeline-plan.md` (decision record). |
| `deploy/` | Dev runbooks plus `prod/` (Terraform design, runbook, archived handover). |
| `design/` | This file. |
| `quality/` | `quality-gates.md` (thresholds + exception process), `nfr-register.md`. |
| `backend-blocker-response-20260916.md` | Historical B1–B5 coordination record (assessment evidence). |

### `docker-compose.yaml` — Local Runtime Definition

Defines the two local infrastructure services — `postgres` 18 and `redis` 8.10. The backend runs natively (`./mvnw spring-boot:run` from `backend/`, `local` profile) or via `backend/Dockerfile`; the frontend runs natively (`pnpm dev` from `frontend/`, `/api` proxied to `http://localhost:8589`). Start the data layer with `docker compose up -d` from the repo root.

---

## No repo-root Makefile

There is no `Makefile` and no `scripts/` directory. Daily commands, per component README:

- Backend: `cd backend && ./mvnw spotless:apply && ./mvnw verify` (format, then full gates).
- Frontend: `cd frontend && pnpm install --frozen-lockfile && pnpm check && pnpm build`.
- Contract drift (same check CI runs): `cd backend && ./mvnw verify -Popenapi-export`, then `git diff --exit-code -- contracts/` from the repo root.
- Board metrics: `.claude/skills/agile-evidence-capture/scripts/snapshot-board.sh` (see `docs/agile/metrics/README.md`).
