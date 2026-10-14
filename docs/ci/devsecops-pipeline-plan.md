# DevSecOps Pipeline Plan — Backend, Frontend Quality, and Repository Automation

Status: approved. Quality/security Steps 1–5 and existing-EC2 delivery Step 6 are implemented. Scope: backend quality gates and delivery, frontend SonarCloud quality analysis, security scanning, repository process automation, and live TypeSafe Jev integration verification. Out of scope here: frontend application delivery details and Terraform pipelines (see `frontend/docs/plan.md`, `backend/README.md`, `frontend/README.md`).

## Goals

| Standard | Tooling | Enforcement point |
|---|---|---|
| Java 21 LTS runtime | Maven Wrapper 3.9.x + Enforcer | every build, local and CI |
| Code formatter | Spotless + google-java-format, AOSP style (4-space indent) | `validate` phase, CI fail-fast |
| Code-style linting | Checkstyle (Google checks, 120 cols) | `verify` phase |
| Static bug analysis | SpotBugs | `verify` phase |
| Code coverage | JaCoCo (backend), Vitest V8 LCOV (frontend) | gate via the matching SonarCloud quality gate |
| Code quality gate | SonarCloud Backend and Frontend projects | independent CI jobs + server-side quality gates |
| Architecture enforcement | ArchUnit (COLA/DDD layering) | unit test phase |
| Secret / vulnerability / image scanning | Gitleaks, Trivy, Dependabot | CI + weekly cron |
| Live AI integration compatibility | TypeSafe Jev System One smoke | applicable PRs, `v*`, manual; non-required |
| Immutable application delivery | ECR digest + GitHub OIDC + SSM | green push to protected `dev` |

## Rolled-out state (Steps 1–6 implemented)

The before/after rollout table was removed 2026-09-21 after implementation: every item below landed (Spotless, SpotBugs, JaCoCo, both SonarCloud jobs, all workflows, Dependabot, Docker, Gitleaks/Trivy, TypeSafe smoke, EC2 delivery). What each step does today lives in [backend-pipeline.md](backend-pipeline.md); the steps below remain as the decision record.

## Step 1 — Local Quality Tooling (POM + format)

All versions pinned in `blocbuy-parent` properties, verified against Maven Central 2026-08-31:

| Plugin | Version |
|---|---|
| spotless-maven-plugin | 3.10.1 |
| google-java-format | 1.36.1 |
| spotbugs-maven-plugin | 4.10.4.0 (spotbugs 4.10.4) |
| jacoco-maven-plugin | 0.8.15 |

1. **Spotless**: google-java-format `AOSP` style (4-space indent), import order `\#,*` (static block, blank line, then everything alphabetical — matches Checkstyle `CustomImportOrder`), `removeUnusedImports`, trailing-whitespace/newline cleanup. Bound to `validate` → `check` so every `verify` gates on formatting. Devs fix with `./mvnw spotless:apply`.
2. **Checkstyle conflict resolution (decision)**: Spotless/AOSP is the single source of truth for formatting. Checkstyle `Indentation` module moved from Google 2-space to AOSP values (`basicOffset=4, caseIndent=4, throwsIndent=8, lineWrappingIndentation=8, arrayInitIndent=4`). Line length stays 120 (D13); google-java-format wraps at 100, which is a subset — no conflict. Everything non-formatting (naming, imports, Javadoc, blocks) stays enforced by Checkstyle.
3. **One-time reformat**: `./mvnw spotless:apply` over the whole codebase, landed as a single formatting commit recorded in `.git-blame-ignore-revs` so `git blame` stays useful.
4. **SpotBugs**: `check` at `verify`, `effort=Max`, `threshold=Medium`, exclusions in `backend/spotbugs-excludes.xml` (bootstrap main class, Lombok/Spring-known false positives only).
5. **JaCoCo**: `prepare-agent` in every module; aggregate report in `blocbuy-bootstrap` → `blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml`. Sonar owns the new-code coverage threshold.

## Step 2 — CI Workflows

### `.github/workflows/ci-backend.yml`

Triggers: PRs and pushes to `dev` touching the backend/contract/deployment paths, `v*` tag pushes, and `merge_group` for forward compatibility with GitHub Merge Queue.

Pull requests are the authoritative pre-merge validation path. The `dev` repository ruleset requires selected status checks to pass against the latest `dev` state before merging. `merge_group` retains the same applicable validation so Merge Queue can be enabled later without redesigning the workflows.

After GitHub updates `dev`, the resulting push runs a lightweight delivery path: package the merged application, assemble the runtime image, publish to ECR, and deploy. Backend-relevant pushes also run `sonar-backend` independently to refresh merged-SHA coverage without repeating the full quality/security gates. Production `v*` tag pushes retain the full release validation path.

Stale ordinary runs may be cancelled, but merge-group validation must not be cancelled as stale.

- **Job `build-quality`** — temurin 21, Maven cache:
  1. `./mvnw -B spotless:check checkstyle:check` (fail fast)
  2. `./mvnw -B verify` (unit tests + ArchUnit + Failsafe ITs + SpotBugs + JaCoCo)
  3. Upload `jacoco.xml` + SpotBugs XML + checkstyle results as artifacts (7-day retention)
  4. Upload Sonar inputs: compiled classes, test results, JaCoCo reports (1-day retention)
- **Job `contract-check`** — regenerate the OpenAPI contract then `git diff --exit-code -- contracts/`; fails the PR if the committed `openapi.json` drifts from the code. On PRs (Step 5) it also runs `openapi-diff --fail-on-incompatible` against the base branch's committed contract, failing on backward-incompatible API changes unless the PR carries the `contract-change-approved` label (explicit human approval; diff then runs report-only).
- **Job `sonar-backend`** (`SonarCloud Backend`) — see Step 3. PR, merge-group and release scans reuse `build-quality` artifacts when Sonar credentials/context permit it; backend-relevant `dev` pushes generate coverage and scan on one runner, independently of delivery.
- **Job `docker`** — applicable PR, merge-group and production-tag runs: download the exact boot-jar produced by successful `build-quality`, assemble it with `Dockerfile.ci`, smoke test with the EC2 profile against PG 18 + Redis 8, and run the Trivy image gate. SARIF is preserved as an artifact; Code Scanning ingestion is push-only when enabled. Enabled production tag pushes preserve the tested image for ECR publication.
- **Post-merge dev delivery** — after an up-to-date validated PR lands on `dev`, rebuild the delivery image from the merged source without repeating the pre-merge quality/security gates, publish it to ECR by immutable digest, then run `deploy-existing-ec2`.
- **Job `deploy-existing-ec2`** — enabled `dev` delivery pushes only: protected environment, GitHub OIDC, exact SSM bundle allowlist, single-instance deployment, health check and automatic application rollback.

### `.github/workflows/ci-frontend.yml` SonarCloud job (added 2026-09-18)

- **Job `sonar-frontend`** (`SonarCloud Frontend`) is independent of `frontend-check` and always reports on pull requests, so it can be a required status check without blocking unrelated PRs.
- Trusted frontend changes install from the frozen lockfile, generate Vitest V8 LCOV, scan `frontend/src` under project key `blocbuy-frontend`, and wait for the server-side quality gate.
- Frontend-changing pushes to `dev` run the same job to keep SonarCloud's main-branch baseline current. Non-frontend, fork and Dependabot PRs report a successful not-applicable result because repository secrets are unavailable or no frontend source needs analysis.
- `frontend-check` remains the separate required generation, type, lint, format, test, unused-code and build gate. SonarCloud does not replace or modify the existing CodeQL job in `security.yml`.

### Pull-request and post-merge delivery decision

The repository ruleset makes pull requests the normal path into `dev`; direct pushes and force-pushes are not part of the normal development flow.

The required status checks are listed in [backend-pipeline.md](backend-pipeline.md#required-pr-checks) (`build-quality`, `SonarCloud Backend`, `docker`, `gitleaks`, `frontend-check`, `SonarCloud Frontend`); that file owns the current list. This plan does not duplicate it.

The ruleset also requires branches to be up to date before merging. If `dev` changes after a PR has passed its required checks, the PR must be updated and the required validation must pass against the latest `dev` state before it can merge.

`merge_group` support is retained in the workflows but is not the repository's current authoritative gate. If the repository later becomes eligible for GitHub Merge Queue, the same applicable gates can validate GitHub's speculative integrated state.

To conserve GitHub Actions minutes, the resulting `push/dev` does not repeat the full Maven quality/security gate chain, contract validation, Docker smoke/Trivy validation, or the live TypeSafe smoke. Backend-relevant pushes run `sonar-backend` to generate merged-SHA coverage and update its main-branch baseline without repeating the full gate chain. Every `dev` push runs Gitleaks in `security.yml`; the broader security gates remain pre-merge, on releases and in the weekly scan. Independently, the delivery path packages the merged source, assembles the image, publishes by immutable ECR digest, and deploys. Frontend-changing pushes likewise refresh their project's main-branch baseline with install, tests with coverage, analysis and its quality gate.

This intentionally changes the earlier artifact-provenance model: the Docker image produced during pre-merge validation is not promoted across workflow runs. The delivery path rebuilds the deployable artifact from the exact merged source after the up-to-date PR passes. This accepts a small reproducibility/provenance trade-off in exchange for eliminating a full duplicate CI/security run. Production `v*` tag releases continue to execute the full release gate chain.

### Addendum (2026-09-24): refresh the backend `dev` SonarCloud baseline

The backend project's main branch remained at its 2026-09-16 analysis even while backend PR quality gates passed on 2026-09-24. For backend-relevant dev pushes, `sonar-backend` generates merged-SHA coverage (unit tests, PostgreSQL ITs and aggregate JaCoCo; skip Spotless, Checkstyle and SpotBugs) and scans on one runner without transferring artifacts from another job. This adds a test run for accurate current-commit coverage, but not another full quality-gate chain; package/ECR/SSM delivery stays independent.

### Addendum (2026-09-24): refresh the Security `dev` badge

The Security badge targets `dev`, but the workflow's regular `dev` run was only the weekly scheduled scan; successful PR checks did not refresh it. Every merged `dev` push now runs the independent full-history Gitleaks job, without repeating the PR's workflow, deployment, dependency, configuration or optional CodeQL scans. Weekly and manual runs still execute the broader checks, and a green push badge only confirms this lightweight post-merge secret scan.

### `.github/workflows/typesafe-smoke.yml`

The TypeSafe workflow performs a live compatibility check against the Jev System One API used by the optional listing-search reranker.

Normal `build-quality` verification remains deterministic and uses tests/stubs rather than a live API key. The live smoke complements those tests by verifying that BlocBuy's expected external Jev wire contract is still accepted by the real service.

The smoke uses the same `choice` shape as the production listing-search reranker:

- state contains `shopper_query`;
- question `best_match` uses `type: choice`;
- criteria contain representative listing candidates;
- the response must contain a `choice` answer and probabilities for every expected candidate;
- every probability must be a number in `[0,1]`;
- model identity and usage metadata must be present.

The smoke does not assert which candidate should win. It is a contract/availability test, not a model-quality benchmark.

Triggers:

- applicable backend pull requests where the Actions secret is available;
- `merge_group` when GitHub Merge Queue is available;
- production `v*` tag pushes;
- manual `workflow_dispatch`.

Fork and Dependabot PRs do not receive the TypeSafe API secret and therefore skip the live call. Normal `push/dev` does not run the smoke; its triggers are listed above.

`typesafe-smoke` is deliberately not a required repository status check. The production listing-search integration treats Jev as a best-effort enhancement: if the live call fails, the application preserves the deterministic PostgreSQL shortlist order and total.

### `.github/workflows/pr-labeler.yml`

`actions/labeler` on PR open/sync — wires up `.github/labeler.yml`.

### `.github/workflows/board-snapshot.yml` (added 2026-09-18)

Daily at 15:30 UTC (23:30 Singapore) and on `workflow_dispatch`: runs `.claude/skills/agile-evidence-capture/scripts/snapshot-board.sh`, uploads the day's CSV as a 90-day artifact, rebuilds the bot branch `metrics/board-snapshots` from `dev` plus any snapshot days not merged yet, force-updates that branch (its own, lease-protected against a concurrent run), and keeps one open PR to `dev` titled `docs(agile): board snapshots through <date>`. Asked for in the PR #113 review: the first CSV (`docs/agile/metrics/2026-09-17.csv`) was a hand run.

Alternatives rejected: (a) commit straight to `dev`: the `dev-require-review` ruleset has no bypass actors and requires a squash-merged PR with four checks; (b) one branch and PR per day: a review per CSV and stale branches, since `delete_branch_on_merge` is off; (c) artifact only: the burndown source has to be in the git archive (`docs/agile/metrics/README.md`); (d) keep appending to the bot branch: after a squash merge the next PR's three-dot diff re-shows the already-merged days, so the branch is rebuilt from `dev` instead.

**2026-09-25 correction:** the `BOARD_SNAPSHOT_TOKEN` secret existed, but the September 25 run logged `unknown owner type` on the private-project query and still published a CSV with blank board columns. The workflow now requires the secret without a `github.token` fallback, and the script fails before writing a CSV if the board query fails or returns no status-bearing repository issues. The existing secret was replaced on 2026-09-25 with the PPsyrius CLI token, which has `read:project` and successfully reads the 58-item private board locally; a scheduled run with the new secret is not yet verified. This token still opens PRs as PPsyrius, so teammate review remains necessary. Refreshing a local `gh` token alone does not update the stored secret. Earlier September 19–24 board columns were reconstructed from dated project timeline events at each snapshot's capture time. GitHub scheduled the September 25 file at 03:20 Singapore despite the 23:30 cron target, so the filename reflects the actual Singapore run date, not a guaranteed end-of-day capture.

### `.github/dependabot.yml`

Weekly, grouped PRs: Maven (`backend/`), GitHub Actions (`/`), Docker (`backend/`), plus configured frontend dependency updates where supported.

**2026-09-22 Java runtime update policy.** The backend remains on the agreed Java 21 LTS and Spring Boot 4 baseline. Dependabot may refresh Java 21 `eclipse-temurin` images, but ignores major Docker tag updates; it also ignores majors for the Boot-4-line Maven artifacts (`org.mybatis.spring.boot*`, `cn.dev33*`, `org.springdoc*`, `org.apache.tomcat*`) while same-line maintenance still flows. These ignores use `update-types: version-update:semver-major`, which filters version updates only — security updates, including major security updates, remain eligible and land as PRs for deliberate handling, never silent adoption. Moving to a later Java release requires a deliberate stack decision and coordinated updates to Maven Enforcer, CI, runtime images, and documentation. Automatically following non-LTS Java tags was rejected because it would split the build and runtime from the Java 21 baseline.

### Branch protection (manual, repo settings)

The `dev` ruleset mirrors the required checks in [backend-pipeline.md](backend-pipeline.md#required-pr-checks): require PRs, require branches to be up to date before merging, human review (including CODEOWNERS where ownership is intentional), stale-approval dismissal, approval of the latest reviewable push, conversation resolution, deletion restriction, and no force-push.

`Restrict updates` remains off: the PR requirement, rather than bypass-only updates, defines the normal path into `dev`.

`merge_group` is retained in workflow configuration for forward compatibility but Merge Queue is not currently required by the repository ruleset.

These settings are configured off-repo in the repository ruleset.

## Step 3 — SonarCloud Backend and Frontend (CI-based analysis)

Backend analysis runs as `sonar-backend` (`SonarCloud Backend`) using the pinned Sonar Maven scanner. The project name is `BlocBuy Backend` and its stable key remains `blocbuy-backend`. The `build-quality` job uploads compiled classes, test results, and JaCoCo reports as artifacts; the Sonar job restores them and invokes `org.sonarsource.scanner.maven:sonar-maven-plugin` without repeating the full Maven verification build.

Quality gate configured server-side: coverage ≥ 80% on new code, no new blocker/critical issues, A ratings on new code. The required `SonarCloud Backend` status must report reliably before merge.

Frontend analysis runs independently as `sonar-frontend` (`SonarCloud Frontend`) using the pinned Sonar scanner action. The project name is `BlocBuy Frontend` and its stable key is `blocbuy-frontend`. Vitest produces `coverage/lcov.info`; generated API and route-tree files are excluded, and test files are classified separately so they do not count as product LOC. A separate project is necessary because the Maven scanner is rooted at `backend/`, while two independent scans into one project key would replace rather than combine analysis results. This frontend quality gate is additive; the existing optional CodeQL configuration remains unchanged.

## Step 4 — Security Tooling

- **`backend/Dockerfile`** — multi-stage: `maven:3.9-eclipse-temurin-21` builds `blocbuy-bootstrap` (`-am`, tests skipped — they ran in `build-quality`); runtime `eclipse-temurin:21-jre`, non-root user, port 8589, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`. No in-image HEALTHCHECK (JRE image has no curl); orchestrator probes hit `/api/actuator/health` (management base-path, already exposed). Profile `docker` selected by compose via `SPRING_PROFILES_ACTIVE`, not baked in. Used by local `docker compose` builds.
- **`backend/Dockerfile.ci`** — runtime-only twin used by the `docker` CI job: successful `build-quality` uploads the boot jar packaged by `mvn verify`; `docker` downloads that exact artifact and assembles the image without repeating an in-container Maven build. The current `docker` job requires `build-quality` success, so it does not use a multi-stage fallback when that artifact is absent.
- **`.github/workflows/security.yml`** — pull requests and merge groups affecting backend, deployment, or workflow files; production `v*` tag pushes; weekly scheduled and manual scans; plus Gitleaks-only `dev` pushes:
  - **Gitleaks** event-selected secret scan with full history available. `.gitleaks.toml` extends defaults and allowlists reviewed placeholders; the full-history target is not established by event-scoped green runs.
  - **Trivy** separates Maven vulnerability and deployment-configuration gates at HIGH/CRITICAL.
  - **Actionlint/ShellCheck** validates workflows, embedded shell and EC2 lifecycle scripts.
  - **Deployment policy** enforces one backend service and an exact non-secret SSM archive allowlist.
  - **CodeQL** provides optional Java SAST when repository Code Scanning is enabled.
- **Dependabot** (Step 2) closes the loop on remediation.

Trivy SARIF reports are preserved as workflow artifacts even when a Trivy gate fails. Security-tab ingestion is enabled only when the repository's Code Scanning capability is available; the Trivy exit code remains the CI gate independently of SARIF ingestion.

2026-10-14 reconciliation: earlier rollout notes described Gitleaks as full-history enforcement, but observed dev execution selected one commit (`--log-opts=-1`); separate full-history scans found unresolved historical matches. The [quality-gate checkpoint](../quality/quality-gates.md#secret-scan-scope-checkpoint--2026-10-14) records the unchanged target and current gap. This is a documentation correction, not a new exception or CI policy change.

## Step 5 — Pipeline Hardening (implemented 2026-09-02)

Fixes found during pipeline reviews; all landed in-repo:

1. **Trivy actually gates now.** `aquasecurity/trivy-action` defaults to `exit-code: 0` — both fs and image scans previously only *reported* HIGH/CRITICAL without failing the job, contradicting the "0 unfixed HIGH/CRITICAL" gate. Both scans now set `exit-code: '1'` + `ignore-unfixed: true` (the latter aligns with the "unfixed" wording so CVEs with no released patch don't break the build).
2. **`docker` job runs on PRs.** Image build, smoke test, and scan now gate *before* merge.
3. **Boot smoke test in `docker` job.** After `docker build`, the image boots against real `postgres:18` + `redis:8.10` containers and CI polls the application health endpoint until `status=UP`. Catches startup-time failures (bad Flyway migration, missing env, broken Spring context) that a successful `docker build` hides. Container logs are dumped on failure.
4. **API breaking-change check in `contract-check`** (PR only). `openapi-diff-cli` 2.1.7 (`--fail-on-incompatible`) compares the base branch's committed `contracts/openapi.json` against the PR's regenerated spec; drift check still catches "forgot to commit", this catches "broke consumers". Escape hatch (added 2026-09-09): a PR labeled `contract-change-approved` downgrades the check to report-only — used for deliberate spec tightening. The label is the recorded approval; apply it only after reviewing the diff output.
5. **CodeQL SAST job in `security.yml`.** Opt-in via repo variable `ENABLE_CODEQL=true`; repository-side GitHub Code Scanning must also be available/enabled for result ingestion. `build-mode: none` avoids a separate Maven build.
6. **`setup-java cache: maven`** replaces hand-rolled Maven cache blocks.
7. **Fixed a silent no-op in the drift check.** `git diff --exit-code -- contracts/` previously ran under `working-directory: backend`, where the pathspec matched nothing. The step now runs from `${{ github.workspace }}`.
8. **Pre-merge validation + lightweight `push/dev`.** Required up-to-date PR checks are the authoritative pre-merge boundary; merged `dev` pushes do not repeat the expensive validation chain (see the dated Sonar and Security baseline addenda above).
9. **Live TypeSafe Jev contract smoke.** The real System One endpoint is exercised separately from deterministic Maven verification using the same `choice` response contract consumed by the listing-search reranker. It remains non-required because Jev is an optional runtime enhancement with deterministic fallback.

## Step 6 — Existing EC2 application delivery (implemented 2026-09-04)

The selected first deployment phase deliberately reuses one existing EC2 host and its existing PostgreSQL and Redis services. Terraform and Bitovi are not executed. The delivery design (host-loopback Compose, runtime bundle, digest-pinned ECR deploy, environment-scoped OIDC roles, rollback) is detailed in the operator guide; this plan keeps only the decision.

Canonical setup and incident procedures: [`docs/deploy/backend-ec2-pipeline-operator-guide.md`](../deploy/backend-ec2-pipeline-operator-guide.md).

## Off-Repo TODO (cannot be done from the codebase)

These need GitHub repo settings / external accounts; do them once, then the matching CI jobs activate automatically:

| # | Action | Where | Unblocks |
|---|---|---|---|
| 1 | Set repo secret `SONAR_TOKEN` to a SonarQube Cloud personal access token with analysis access to both BlocBuy projects | Repo Settings → Secrets and variables → Actions | `SonarCloud Backend` and `SonarCloud Frontend` jobs and server-side quality gates |
| 2 | Set repo **variable** `ENABLE_CODEQL=true` and enable/confirm repository-side GitHub Code Scanning | Repo Settings → Secrets and variables / Code security | `codeql` SAST result ingestion |
| 3 | Configure the `dev` **ruleset**: require PR, `build-quality`, `SonarCloud Backend`, `docker`, `gitleaks`, `frontend-check`, `SonarCloud Frontend`, branches up to date, review requirements, deletion restriction and no force-push | Repo Settings → Rules | Enforces the authoritative pre-merge validation path |
| 4 | **Gitleaks license**: `gitleaks-action` needs a free `GITLEAKS_LICENSE` secret for organization-owned repos | gitleaks.io → add as repo secret | `gitleaks` job on org repos |
| 5 | Confirm Code Scanning result ingestion works before making CodeQL/code-scanning results a repository ruleset requirement | Repo Settings → Code security | Prevents a ruleset from waiting on results the repository cannot accept |
| 6 | **Completed 2026-09-23:** configured ECR and scoped OIDC/EC2 roles; restricted `backend-dev` and `frontend-dev` to `dev`; verified backend immutable-digest deployment through SSM with local/public health and frontend S3/CloudFront deployment through `e2e-dev` | AWS + Repo Settings; sprint log 2026-09-23 | Immutable deploy and controlled rollback |
| 7 | Keep repo **secret** `TYPESAFE_API_KEY` configured | Repo Settings → Secrets and variables → Actions | Live `typesafe-smoke` contract verification |
| 8 | **Secret replaced 2026-09-25; live run pending.** Repo `BOARD_SNAPSHOT_TOKEN` now holds the PPsyrius CLI token with `repo` and `read:project`, which can read `users/PPsyrius/projects/2` (#103) locally. A prior token produced `unknown owner type`; local `gh auth refresh` does not by itself update a stored secret. | Repo Settings → Secrets and variables → Actions | After the fix merges, a scheduled/manual `board-snapshot` run prints board status counts and opens a PR with nonblank board columns and required checks; teammate approval is still needed for PPsyrius-authored PRs |

## Quality Gates Summary

Enforced thresholds live in [`docs/quality/quality-gates.md`](../quality/quality-gates.md); this plan does not duplicate that table.

## Local Developer Loop

See `backend/README.md` for the local commands (`spotless:apply`, `verify`).

Normal local verification does not require `TYPESAFE_API_KEY`; Jev-dependent application tests use controlled test doubles/stubs. The live external contract check is owned by `typesafe-smoke.yml`. Pre-push hook optional; CI is the backstop. Full runbook: `docs/ci/backend-pipeline.md`. Thresholds: `docs/quality/quality-gates.md`.

## Risks / Notes

- **One-time formatting churn**: `spotless:apply` touches nearly every file. Land it alone, record in `.git-blame-ignore-revs`.
- **JDK 26 on dev machines**: Enforcer rejects `[21,22)`-outside JDKs; set `JAVA_HOME` to a JDK 21 (`/usr/libexec/java_home -v 21` on macOS).
- **Code-quality services are external dependencies**: the Backend and Frontend SonarCloud projects require their repository variables/tokens, and CodeQL result ingestion depends on repository-side Code Scanning being enabled.
- **TypeSafe Jev is an external optional dependency**: the live smoke verifies compatibility, but a TypeSafe outage must not prevent deterministic listing search or make the live smoke a required merge gate.
- **Infrastructure IaC is deferred**: application delivery is versioned, but EC2/VPC/IAM drift remains an operational risk until a later import/migration phase is approved.
