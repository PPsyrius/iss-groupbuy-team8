# Contributing

## Before You Push

### Backend

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # JDK 21 required (Enforcer rejects others)
./mvnw spotless:apply                               # format
./mvnw verify                                       # tests + ArchUnit + Checkstyle + SpotBugs + JaCoCo
```

Changed a controller or DTO? Also `./mvnw verify -Popenapi-export` and commit the updated `contracts/openapi.json`.

### Frontend

```bash
cd frontend
pnpm install                 # Node 24 + pnpm 12.4.2 via corepack (engine-strict); runs gen:api
pnpm check                   # gen:api + tsc + oxlint + oxfmt --check + vitest + knip (same as CI)
```

Once per clone: `pnpm exec lefthook install` (git hooks: oxfmt, oxlint and `tsc --noEmit` on staged files). `pnpm e2e` needs `pnpm build` first. Details: `frontend/AGENTS.md`.

## Commits

Conventional Commits: `feat(scope): summary`, `fix(scope): summary`, `docs:`, `style:`, `chore:`.
Summary ≤ 72 chars, imperative. Body only when the *why* is not obvious.

## Pull Requests

- Target `dev`. Small PRs; one concern per PR.
- CI must be green: `build-quality`, `contract-check` (backend) and `frontend-check` (frontend) are required checks. Red pipelines don't merge.
- Formatting-only changes land as their own commit (`style:`), never mixed with behavior changes.
- CODEOWNERS review required. PRs touching `.github/**`, `backend/Dockerfile`, or pipeline config need the devsecops label owner.
- New secrets/config: environment variables only, documented in `backend/README.md`. Never committed values (Gitleaks gates full history).

## Evidence

The course grades the report, so the paper trail is part of the work. See `docs/agile/README.md`.

- Every PR links its issue (`Closes #N` or `Refs #N`) and adds an entry to `docs/agile/sprint-NN/log.md` in the same PR. `style:` and `chore(deps):` are exempt.
- Non-functional work (security, performance, availability, observability, cost, maintainability, CI, config, infra) adds or updates a row in `docs/quality/nfr-register.md`.
- A real choice between alternatives is written down when it is made. Only the frontend writes ADRs (`frontend/docs/adr/`). Backend decisions go in the locked decision table of `backend/docs/design/backend-skeleton-plan.md` (dated amendments in place) and the design docs under `backend/docs/design/`; cross-cutting decisions (CI, deploy, quality gates, process) go in a dated paragraph of the owning doc under `docs/`.
- Issue and PR templates under `.github/` carry the shapes. Claude Code sessions get the same checklist from the `agile-evidence-capture` skill.

## Style & Architecture

- Formatting is machine-owned: Spotless + google-java-format AOSP (4-space). Don't hand-format.
- COLA layering enforced by ArchUnit — violations fail the build. Layer rules: `backend/blocbuy-bootstrap/src/test/java/com/blocbuy/architecture/ArchitectureTest.java`.
- Gates and thresholds: `docs/quality/quality-gates.md`. Pipeline runbook: `docs/ci/backend-pipeline.md`.
