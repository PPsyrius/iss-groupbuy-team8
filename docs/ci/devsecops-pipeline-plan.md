# DevSecOps Pipeline Plan — BlocBuy Backend

Status: approved. Steps 1–4 implemented 2026-08-31; Step 5 (hardening) implemented 2026-09-02.
Scope: backend quality gates, CI, code-quality platform, and security scanning.
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

## Current State → Target State

| Item | Before | After Steps 1–4 |
|---|---|---|
| Checkstyle + ArchUnit + Enforcer | ✅ configured | unchanged |
| Spotless (AOSP) | ❌ | `spotless:check` at `validate`; `spotless:apply` for devs |
| SpotBugs | ❌ | `spotbugs:check` at `verify`, `spotbugs-excludes.xml` filter |
| JaCoCo | ❌ | per-module reports + aggregate in `blocbuy-bootstrap` |
| SonarQube | ❌ | `sonar` CI job, enabled by repo variables/secrets |
| GitHub workflows | ❌ none | `ci-backend.yml`, `security.yml`, `pr-labeler.yml` |
| Dependabot | ❌ | `.github/dependabot.yml` (maven, github-actions, docker) |
| Docker image | ❌ (only `application-docker.yml`) | `backend/Dockerfile` multi-stage, non-root |
| Secret scanning | ❌ | Gitleaks in `security.yml` + `.gitleaks.toml` |

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
- **Job `contract-check`** — `./mvnw -B verify -Popenapi-export` then `git diff --exit-code -- contracts/`; fails the PR if the committed `openapi.json` drifts from the code. On PRs (Step 5) it also runs `openapi-diff --fail-on-incompatible` against the base branch's committed contract, failing on backward-incompatible API changes.
- **Job `sonar`** — see Step 3. Skipped unless configured.
- **Job `docker`** — PRs and pushes to `dev` (Step 5: was push-only): build image, smoke test (boot against PG 18 + Redis 8, probe `/actuator/health`), Trivy image scan with `exit-code: 1`, SARIF to the Security tab.

### `.github/workflows/pr-labeler.yml`

`actions/labeler@v5` on PR open/sync — wires up the previously orphaned `.github/labeler.yml`.

### `.github/dependabot.yml`

Weekly, grouped PRs: Maven (`backend/`), GitHub Actions (`/`), Docker (`backend/`).

### Branch protection (manual, repo settings)

`dev`: require PR, require `build-quality` + `contract-check` + `docker` green, require CODEOWNERS review, no force-push. Cannot be expressed in-repo; recorded here as an action item (see "Off-Repo TODO" below).

## Step 3 — SonarQube

Host choice is intentionally deferred. The `sonar` CI job activates when repo **variable** `SONAR_HOST_URL` is set; secrets/vars required:

| Name | Kind | Example |
|---|---|---|
| `SONAR_HOST_URL` | variable | `https://sonarcloud.io` or self-hosted URL |
| `SONAR_TOKEN` | secret | scanner token |
| `SONAR_ORGANIZATION` | variable | SonarCloud only |

Options: **SonarCloud** (zero infra; private repos need a paid plan) or **self-hosted Community Edition** on the existing AWS dev box (free; you operate upgrades/backups). Both work with the same job. Quality gate configured server-side: coverage ≥ 80% on new code, no new blocker/critical issues, A ratings on new code.

## Step 4 — Security Tooling

- **`backend/Dockerfile`** — multi-stage: `maven:3.9-eclipse-temurin-21` builds `blocbuy-bootstrap` (`-am`, tests skipped — they ran in `build-quality`); runtime `eclipse-temurin:21-jre`, non-root user, port 8589, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`. No in-image HEALTHCHECK (JRE image has no curl); orchestrator probes hit `/actuator/health` (already exposed). Profile `docker` selected by compose via `SPRING_PROFILES_ACTIVE`, not baked in.
- **`.github/workflows/security.yml`** — PRs touching `backend/**` + weekly cron:
  - **Gitleaks** full-history secret scan (`security-gitleaks` job). `.gitleaks.toml` extends defaults, allowlists `${VAR}` placeholders in YAML and docs.
  - **Trivy fs scan** (`security-deps` job): Maven dependency CVEs + config misconfig, HIGH/CRITICAL, SARIF upload + table output. OWASP Dependency-Check deliberately not used — Trivy covers the same ground with zero NVD API key friction.
- **Dependabot** (Step 2) closes the loop on remediation.

SARIF upload to the Security tab requires GitHub Advanced Security on private repos (free on public). Upload steps are `continue-on-error` so private-repo forks still gate on the console scan itself.

## Step 5 — Pipeline Hardening (implemented 2026-09-02)

Fixes found during a pipeline review; all landed in-repo:

1. **Trivy actually gates now.** `aquasecurity/trivy-action` defaults to `exit-code: 0` — both fs and image scans previously only *reported* HIGH/CRITICAL without failing the job, contradicting the "0 unfixed HIGH/CRITICAL" gate. Both scans now set `exit-code: '1'` + `ignore-unfixed: true` (the latter aligns with the "unfixed" wording so CVEs with no released patch don't break the build).
2. **`docker` job runs on PRs** (was push-only). Image build, smoke test, and scan now gate *before* merge.
3. **Boot smoke test in `docker` job.** After `docker build`, the image boots on a scratch Docker network against real `postgres:18` + `redis:8.1` (requirepass set) containers, and CI polls `GET /actuator/health` until `status=UP` (60s budget). Catches startup-time failures (bad Flyway migration, missing env, broken Spring context) that a successful `docker build` hides. Container logs are dumped on failure.
4. **API breaking-change check in `contract-check`** (PR only). `openapi-diff-cli` 2.1.7 (`--fail-on-incompatible`) compares the base branch's committed `contracts/openapi.json` against the PR's regenerated spec; drift check still catches "forgot to commit", this catches "broke consumers".
5. **CodeQL SAST job in `security.yml`.** Opt-in via repo variable `ENABLE_CODEQL=true` (requires GHAS on private repos); `build-mode: none` so no Maven build is needed.
6. **`setup-java cache: maven`** replaces the three hand-rolled `actions/cache` blocks for `~/.m2/repository` (it resolves `backend/**/pom.xml` itself).
7. **Fixed a silent no-op in the drift check (pre-existing bug).** `git diff --exit-code -- contracts/` ran under `working-directory: backend`, where the pathspec matches nothing and `git diff` exits 0 — the drift gate could never fail (verified locally: modifying `contracts/openapi.json` still exited 0). The step now runs from `${{ github.workspace }}`.

## Off-Repo TODO (cannot be done from the codebase)

These need GitHub repo settings / external accounts; do them once, then the matching CI jobs activate automatically:

| # | Action | Where | Unblocks |
|---|---|---|---|
| 1 | Set repo **variable** `SONAR_HOST_URL` (+ **secret** `SONAR_TOKEN`; + variable `SONAR_ORGANIZATION` if SonarCloud) | Repo Settings → Secrets and variables → Actions | `sonar` job — the ≥80% new-code coverage gate and quality gate are dead until this is set. Host choice: SonarCloud (private repos need paid plan) or self-hosted Community on the AWS dev box |
| 2 | Set repo **variable** `ENABLE_CODEQL=true` | same page | `codeql` SAST job. Requires **GitHub Advanced Security** if the repo is private (free for public repos) — enable GHAS first or the job fails |
| 3 | **Branch protection** on `dev`: require PR, require checks `build-quality` + `contract-check` + `docker` (+ `sonar`/`codeql` once enabled), require CODEOWNERS review, no force-push | Repo Settings → Branches | Without this, every green check above is advisory only |
| 4 | **Gitleaks license**: `gitleaks-action` needs a free `GITLEAKS_LICENSE` secret for organization-owned repos | gitleaks.io → add as repo secret | `gitleaks` job on org repos |
| 5 | Confirm **GHAS** availability or accept `continue-on-error` SARIF uploads (scans still gate via exit codes; only the Security-tab view is lost) | Repo Settings → Advanced Security | SARIF dashboards for Trivy/CodeQL |

## Quality Gates Summary

| Gate | Threshold | Where enforced |
|---|---|---|
| Formatting | 100% Spotless-clean | `validate` / CI step 1 |
| Style | 0 Checkstyle errors | `verify` |
| Bugs | 0 SpotBugs findings ≥ Medium | `verify` |
| Architecture | ArchUnit suite green | `test` |
| Coverage | ≥ 80% on new code | SonarQube quality gate |
| Vulnerabilities | 0 unfixed HIGH/CRITICAL | Trivy in CI |
| Secrets | 0 leaks in history | Gitleaks in CI |
| Contract drift | `contracts/openapi.json` == generated | `contract-check` job |
| Contract compatibility | no backward-incompatible change vs base | `contract-check` job (PR only, openapi-diff) |
| Boot smoke | `/actuator/health` = UP in 60s vs PG 18 + Redis 8 | `docker` job |
| SAST | 0 CodeQL alerts | `security.yml` `codeql` job (opt-in) |

## Local Developer Loop

```bash
cd backend
./mvnw spotless:apply          # fix formatting before commit
./mvnw verify                  # everything CI runs, minus Sonar/Trivy
```

Pre-push hook optional; CI is the backstop. Full runbook: `docs/ci/backend-pipeline.md`. Thresholds: `docs/quality/quality-gates.md`.

## Risks / Notes

- **One-time formatting churn**: `spotless:apply` touches nearly every file. Land it alone, record in `.git-blame-ignore-revs`.
- **JDK 26 on dev machines**: Enforcer rejects `[21,22)`-outside JDKs; set `JAVA_HOME` to a JDK 21 (`/usr/libexec/java_home -v 21` on macOS).
- **Sonar host undecided**: job ships disabled; flipping on = one repo variable + one secret.
- **docker-compose.yml does not exist yet** at repo root (described in `repository-structure.md` as target state); the Dockerfile is independently usable.
