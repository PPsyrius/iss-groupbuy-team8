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
# Select Node 24 with the repository runtime tool, then enable Corepack.
corepack enable
pnpm install                 # runs gen:api
pnpm check                   # gen:api + tsc + oxlint + oxfmt --check + vitest + knip (same as CI)
```

Once per clone: `pnpm exec lefthook install` (git hooks: oxfmt, oxlint and `tsc --noEmit` on staged files). `pnpm e2e` builds `dist/` when it is missing and serves an existing one as is. Details: `frontend/AGENTS.md`.

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

- Every PR links its issue (`Closes #N` or `Refs #N`) and adds an entry to `docs/agile/sprint-NN/log/YYYY-MM-DD.md` in the same PR, creating the day's file if needed (`log.md` indexes the day files). `style:` and `chore(deps):` are exempt.
- Non-functional work (security, performance, availability, observability, cost, maintainability, CI, config, infra) adds or updates a row in `docs/quality/nfr-register.md`.
- A real choice between alternatives is written down when it is made. Only the frontend writes ADRs (`frontend/docs/adr/`). Backend decisions go in the locked decision table of `backend/docs/design/backend-skeleton-plan.md` (dated amendments in place) and the design docs under `backend/docs/design/`; cross-cutting decisions (CI, deploy, quality gates, process) go in a dated paragraph of the owning doc under `docs/`.
- Issue and PR templates under `.github/` carry the shapes. Claude Code sessions get the same checklist from the `agile-evidence-capture` skill.

## Style & Architecture

- Formatting is machine-owned: Spotless + google-java-format AOSP (4-space). Don't hand-format.
- In README files, keep each prose paragraph and prose list item on one physical source line. Fenced code blocks, tables, and nested lists retain the line breaks their structure requires. Apply this when editing a README; do not reflow unrelated or historical documents just to adopt the convention.
- COLA layering enforced by ArchUnit — violations fail the build. Layer rules: `backend/blocbuy-bootstrap/src/test/java/com/blocbuy/architecture/ArchitectureTest.java`.
- Gates and thresholds: `docs/quality/quality-gates.md`. Pipeline runbook: `docs/ci/backend-pipeline.md`.

## Language of the repository

**Every file committed to this repository is written in English.** No exceptions, no mixed-language
documents.

This covers:

- documentation of every kind — design docs, implementation plans, ADRs, runbooks, sprint logs,
  the NFR register, READMEs
- code comments and Javadoc, identifier names, log messages, test names
- SQL migration comments and `COMMENT ON` text
- commit messages and pull-request descriptions
- GitHub issue titles and bodies, and review comments

It does **not** cover conversation. Team members work in the language they are most fluent in, and
an agent should keep replying in the language the user wrote in. The rule is about what lands in
git, not about how the work is discussed while it is being done.

Why: this is a five-person team whose members do not share one first language, and the deliverable
is assessed by readers outside the team. A Chinese-only implementation plan is invisible to the
teammates who have to act on it, to the reviewer of the PR it belongs to, and to the marker reading
the progress report that cites it. It also silently makes the author the only person who can
maintain that document. The cost of writing English at the moment the document is created is near
zero; the cost of translating it later — after it has been cited, linked and partly superseded —
is not.

For agents specifically:

- When the user asks for a committed document in another language, write it in English and explain
  the repository rule in one sentence. A conversational translation can be provided separately.
- Never produce a document that is English in structure and another language in its prose, or a
  table whose headers are English and whose cells are not.
- When you touch an existing non-English document for any other reason, do not silently translate
  it as a side effect. Translating a document is its own change, in its own commit.
- Terms that are already English stay verbatim in any language: identifiers, API paths, error
  codes, CLI commands, commit-type keywords (`feat`, `fix`, …) and exact error strings.
