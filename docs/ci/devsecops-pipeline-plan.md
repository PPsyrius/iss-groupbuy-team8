# DevSecOps Pipeline Plan — BlocBuy Backend

Status: approved. Quality/security Steps 1–5 and existing-EC2 delivery Step 6 are implemented.
Scope: backend quality gates, CI, code-quality platform, security scanning, and application delivery.
Out of scope here: frontend pipeline, Terraform pipeline, agile-metrics automation (see `docs/design/repository-structure.md`).

## Goals

| Standard | Tooling | Enforcement point |
|---|---|---|
| Java 21 LTS runtime | Maven Wrapper 3.9.x + Enforcer | every build, local and CI |
| Code formatter | Spotless + google-java-format, AOSP style (4-space indent) | `validate` phase, CI fail-fast |
| Code-style linting | Checkstyle (Google checks, 120 cols) | `verify` phase |
| Static bug analysis | SpotBugs | `verify` phase |
| Code coverage | JaCoCo | report every build; gate via SonarQube quality gate |
| Code quality gate | SonarQube / SonarCloud | CI job + server-side quality gate |
| Architecture enforcement | ArchUnit (COLA/DDD layering) | unit test phase |
| Secret / vulnerability / image scanning | Gitleaks, Trivy, Dependabot | CI + weekly cron |
| Immutable application delivery | ECR digest + GitHub OIDC + SSM | green push to protected `dev` |

## Current State → Target State

| Item | Before | After Steps 1–4 |
|---|---|---|
| Checkstyle + ArchUnit + Enforcer | ✅ configured | unchanged |
| Spotless (AOSP) | ❌ | `spotless:check` at `validate`; `spotless:apply` for devs |
| SpotBugs | ❌ | `spotbugs:check` at `verify`, `spotbugs-excludes.xml` filter |
| JaCoCo | ❌ | per-module reports + aggregate in `blocbuy-bootstrap` |
| SonarQube | ❌ | `sonar` CI job with artifact reuse (SonarCloud scan + quality gate) |
| GitHub workflows | ❌ none | `ci-backend.yml`, `security.yml`, `pr-labeler.yml` |
| Dependabot | ❌ | `.github/dependabot.yml` (maven, github-actions, docker) |
| Docker image | ❌ (only `application-docker.yml`) | `backend/Dockerfile` multi-stage, non-root |
| Secret scanning | ❌ | Gitleaks in `security.yml` + `.gitleaks.toml` |
| Existing EC2 delivery | ❌ | backend-only bundle, ECR digest, SSM deploy, health and rollback |

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
5. **JaCoCo**: `prepare-agent` + per-module `report` in every module; `report-aggregate` in `blocbuy-bootstrap` (only module depending on all others) → `blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml`. No hard Maven coverage check — the threshold lives in the SonarQube quality gate where "new code" scoping exists.

## Step 2 — CI Workflows

### `.github/workflows/ci-backend.yml`

Triggers: PRs and pushes to `dev` touching `backend/**` or `contracts/**`. Concurrency group cancels stale runs.

- **Job `build-quality`** — temurin 21, Maven cache:
  1. `./mvnw -B spotless:check checkstyle:check` (fail fast)
  2. `./mvnw -B verify` (unit tests + ArchUnit + Failsafe ITs + SpotBugs + JaCoCo)
  3. Upload `jacoco.xml` + SpotBugs XML + checkstyle results as artifacts (7-day retention)
  4. Upload Sonar inputs: compiled classes, test results, JaCoCo reports (1-day retention)
- **Job `contract-check`** — `./mvnw -B verify -Popenapi-export` then `git diff --exit-code -- contracts/`; fails the PR if the committed `openapi.json` drifts from the code. On PRs (Step 5) it also runs `openapi-diff --fail-on-incompatible` against the base branch's committed contract, failing on backward-incompatible API changes unless the PR carries the `contract-change-approved` label (explicit human approval; diff then runs report-only).
- **Job `sonar`** — downloads Sonar inputs from `build-quality`, runs SonarCloud scan and quality gate check. Coverage ≥ 80% on new code, no new blocker/critical issues, A ratings on new code.
- **Job `docker`** — PRs and pushes to `dev`: build image, smoke test with the EC2 profile against PG 18 + Redis 8, and Trivy image scan. On an enabled green `dev` push, publish that exact image to ECR and emit its immutable digest.
- **Job `deploy-existing-ec2`** — enabled green `dev` pushes only: protected environment, GitHub OIDC, exact SSM bundle allowlist, single-instance deployment, health check and automatic application rollback.

### `.github/workflows/pr-labeler.yml`

`actions/labeler@v5` on PR open/sync — wires up the previously orphaned `.github/labeler.yml`.

### `.github/dependabot.yml`

Weekly, grouped PRs: Maven (`backend/`), GitHub Actions (`/`), Docker (`backend/`).

### Branch protection (manual, repo settings)

`dev`: require PR, require `build-quality` + `contract-check` + `sonar` + `docker` green, require CODEOWNERS review, no force-push. Cannot be expressed in-repo; recorded here as an action item (see "Off-Repo TODO" below).

## Step 3 — SonarQube (CI-based analysis)

SonarCloud analysis runs as a CI job (`sonar`) using the `SonarSource/sonarqube-scan-action`. The `build-quality` job uploads compiled classes, test results, and JaCoCo reports as artifacts; the `sonar` job downloads them and runs the scanner without recompiling. This avoids duplicating the full Maven build while still enforcing the coverage gate.

Quality gate configured server-side: coverage ≥ 80% on new code, no new blocker/critical issues, A ratings on new code. The `SonarCloud Code Analysis` status check must be added to branch protection as a required check.

## Step 4 — Security Tooling

- **`backend/Dockerfile`** — multi-stage: `maven:3.9-eclipse-temurin-21` builds `blocbuy-bootstrap` (`-am`, tests skipped — they ran in `build-quality`); runtime `eclipse-temurin:21-jre`, non-root user, port 8589, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`. No in-image HEALTHCHECK (JRE image has no curl); orchestrator probes hit `/actuator/health` (already exposed). Profile `docker` selected by compose via `SPRING_PROFILES_ACTIVE`, not baked in.
- **`.github/workflows/security.yml`** — PR/push changes to backend, deployment or workflows plus weekly cron:
  - **Gitleaks** full-history secret scan. `.gitleaks.toml` extends defaults and allowlists reviewed placeholders.
  - **Trivy** separates Maven vulnerability and deployment-configuration gates at HIGH/CRITICAL.
  - **Actionlint/ShellCheck** validates workflows, embedded shell and EC2 lifecycle scripts.
  - **Deployment policy** enforces one backend service and an exact non-secret SSM archive allowlist.
- **Dependabot** (Step 2) closes the loop on remediation.

SARIF upload to the Security tab requires GitHub Advanced Security on private repos (free on public). Upload steps are `continue-on-error` so private-repo forks still gate on the console scan itself.

## Step 5 — Pipeline Hardening (implemented 2026-09-02)

Fixes found during a pipeline review; all landed in-repo:

1. **Trivy actually gates now.** `aquasecurity/trivy-action` defaults to `exit-code: 0` — both fs and image scans previously only *reported* HIGH/CRITICAL without failing the job, contradicting the "0 unfixed HIGH/CRITICAL" gate. Both scans now set `exit-code: '1'` + `ignore-unfixed: true` (the latter aligns with the "unfixed" wording so CVEs with no released patch don't break the build).
2. **`docker` job runs on PRs** (was push-only). Image build, smoke test, and scan now gate *before* merge.
3. **Boot smoke test in `docker` job.** After `docker build`, the image boots on a scratch Docker network against real `postgres:18` + `redis:8.10` (requirepass set) containers, and CI polls `GET /actuator/health` until `status=UP` (60s budget). Catches startup-time failures (bad Flyway migration, missing env, broken Spring context) that a successful `docker build` hides. Container logs are dumped on failure.
4. **API breaking-change check in `contract-check`** (PR only). `openapi-diff-cli` 2.1.7 (`--fail-on-incompatible`) compares the base branch's committed `contracts/openapi.json` against the PR's regenerated spec; drift check still catches "forgot to commit", this catches "broke consumers". Escape hatch (added 2026-09-09): a PR labeled `contract-change-approved` downgrades the check to report-only — used for deliberate spec tightening, e.g. adding validation `minimum`/`maximum` that only rejects values the server never honored (#56: `page=0` previously 500'd). The label is the recorded approval; apply it only after reviewing the diff output.
5. **CodeQL SAST job in `security.yml`.** Opt-in via repo variable `ENABLE_CODEQL=true` (requires GHAS on private repos); `build-mode: none` so no Maven build is needed.
6. **`setup-java cache: maven`** replaces the three hand-rolled `actions/cache` blocks for `~/.m2/repository` (it resolves `backend/**/pom.xml` itself).
7. **Fixed a silent no-op in the drift check (pre-existing bug).** `git diff --exit-code -- contracts/` ran under `working-directory: backend`, where the pathspec matches nothing and `git diff` exits 0 — the drift gate could never fail (verified locally: modifying `contracts/openapi.json` still exited 0). The step now runs from `${{ github.workspace }}`.

## Step 6 — Existing EC2 application delivery (implemented 2026-09-04)

The selected first deployment phase deliberately reuses one existing EC2 host and its existing
PostgreSQL and Redis services. Terraform and Bitovi are not executed.

1. `application-ec2.yml` uses host-loopback PostgreSQL/Redis settings; the backend-only Compose model
   uses Linux host networking and declares no database, cache or volume.
2. The runtime bundle provides preflight, health, locked deploy, automatic rollback and manual
   rollback scripts. Runtime secrets remain `/etc/blocbuy/backend.env` with `0600` permissions.
3. A green `dev` push uses a branch-scoped OIDC role to push the smoke-tested image to one private ECR
   repository. Docker's pushed digest is cross-checked with ECR and only the digest is deployed.
4. A separate environment-scoped OIDC role can send `AWS-RunShellScript` only to the configured EC2
   instance. SSM transfers only the allowlisted non-secret bundle.
5. `rollback-backend.yml` requires `dev`, a reason, explicit confirmation and the protected
   `backend-dev` environment. It uses the recorded previous digest unless an operator supplies a
   reviewed digest from the configured repository.
6. Deployment and rollback share a non-cancellable concurrency group. Neither operation reverses
   Flyway migrations or manages PostgreSQL/Redis.

Canonical setup and incident procedures:
[`docs/deploy/backend-ec2-pipeline-operator-guide.md`](../deploy/backend-ec2-pipeline-operator-guide.md).

## Off-Repo TODO (cannot be done from the codebase)

These need GitHub repo settings / external accounts; do them once, then the matching CI jobs activate automatically:

| # | Action | Where | Unblocks |
|---|---|---|---|
| 1 | Set repo **secret** `SONAR_TOKEN` (SonarCloud scanner token) | Repo Settings → Secrets and variables → Actions | `sonar` CI job — the ≥80% new-code coverage gate and quality gate are dead until this is set |
| 2 | Set repo **variable** `ENABLE_CODEQL=true` | same page | `codeql` SAST job. Requires **GitHub Advanced Security** if the repo is private (free for public repos) — enable GHAS first or the job fails |
| 3 | **Branch protection** on `dev`: require PR, backend checks plus `workflow-lint`, `deployment-bundle`, `gitleaks`, `trivy-deps`, `trivy-config`, CODEOWNERS review, no force-push | Repo Settings → Branches | Prevents bypass of delivery/security policy |
| 4 | **Gitleaks license**: `gitleaks-action` needs a free `GITLEAKS_LICENSE` secret for organization-owned repos | gitleaks.io → add as repo secret | `gitleaks` job on org repos |
| 5 | Confirm **GHAS** availability or accept `continue-on-error` SARIF uploads (scans still gate via exit codes; only the Security-tab view is lost) | Repo Settings → Advanced Security | SARIF dashboards for Trivy/CodeQL |
| 6 | Configure ECR, two OIDC roles, EC2 SSM/ECR role, `backend-dev` protections and deployment variables from the operator guide | AWS + Repo Settings | Immutable deploy and controlled rollback |

## Quality Gates Summary

| Gate | Threshold | Where enforced |
|---|---|---|
| Formatting | 100% Spotless-clean | `validate` / CI step 1 |
| Style | 0 Checkstyle errors | `verify` |
| Bugs | 0 SpotBugs findings ≥ Medium | `verify` |
| Architecture | ArchUnit suite green | `test` |
| Coverage | ≥ 80% on new code | `sonar` CI job (SonarCloud quality gate) |
| Vulnerabilities | 0 unfixed HIGH/CRITICAL | Trivy in CI |
| Secrets | 0 leaks in history | Gitleaks in CI |
| Contract drift | `contracts/openapi.json` == generated | `contract-check` job |
| Contract compatibility | no backward-incompatible change vs base, or `contract-change-approved` label on PR | `contract-check` job (PR only, openapi-diff) |
| Boot smoke | `/actuator/health` = UP in 60s vs PG 18 + Redis 8 | `docker` job |
| SAST | 0 CodeQL alerts | `security.yml` `codeql` job (opt-in) |
| Deployment bundle | exactly one backend service and six allowlisted archive entries | `security.yml` and `ci-backend.yml` |
| Release identity | tested ECR image pinned by SHA-256 digest | `ci-backend.yml` |
| Deployment health | Actuator reports `UP`; otherwise application rollback | EC2 lifecycle scripts |

## Local Developer Loop

```bash
cd backend
./mvnw spotless:apply          # fix formatting before commit
./mvnw verify                  # everything CI runs, minus Trivy
```

Pre-push hook optional; CI is the backstop. Full runbook: `docs/ci/backend-pipeline.md`. Thresholds: `docs/quality/quality-gates.md`.

## Risks / Notes

- **One-time formatting churn**: `spotless:apply` touches nearly every file. Land it alone, record in `.git-blame-ignore-revs`.
- **JDK 26 on dev machines**: Enforcer rejects `[21,22)`-outside JDKs; set `JAVA_HOME` to a JDK 21 (`/usr/libexec/java_home -v 21` on macOS).
- **Sonar host undecided**: job ships disabled; flipping on = one repo variable + one secret.
- **Infrastructure IaC is deferred**: application delivery is versioned, but EC2/VPC/IAM drift remains an operational risk until a later import/migration phase is approved.
